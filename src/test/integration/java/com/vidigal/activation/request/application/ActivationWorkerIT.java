package com.vidigal.activation.request.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.vidigal.activation.request.domain.ActivationCommand;
import com.vidigal.activation.request.domain.ActivationStatus;
import com.vidigal.activation.request.integration.ActivationOutcome;
import com.vidigal.activation.request.integration.ActivationProvider;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.slf4j.MDC;
import com.vidigal.activation.web.CorrelationIdFilter;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(properties = {
        "activation.security.mode=local",
        "activation.worker.enabled=false",
        "activation.retry.initial-delay=1ms",
        "activation.retry.max-delay=10ms"
})
@Import(ActivationWorkerIT.Database.class)
class ActivationWorkerIT {

    @TestConfiguration(proxyBeanMethods = false)
    static class Database {
        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgres() {
            return new PostgreSQLContainer<>("postgres:17.11-alpine");
        }
    }

    @Autowired
    private RegistrationService registrations;

    @Autowired
    private ActivationWorker worker;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MeterRegistry metrics;

    @MockitoBean
    private ActivationProvider provider;

    @BeforeEach
    void clearRequests() {
        jdbc.execute("TRUNCATE TABLE activation_requests");
    }

    @Test
    void completesAnActivationAfterProviderSuccess() {
        var registration = registrations.register(
                new ActivationCommand("EXT-" + UUID.randomUUID(), "CUST-001", "INTERNET-500"), "worker-success", "local-demo");
        when(provider.activate(any())).thenReturn(new ActivationOutcome(
                ActivationOutcome.Kind.SUCCESS, "PROVIDER_SUCCESS", Duration.ZERO));

        assertThat(worker.processNext()).isTrue();

        assertThat(registrations.get(registration.request().getId(), "local-demo").getStatus())
                .isEqualTo(ActivationStatus.COMPLETED);
    }

    @Test
    void defersAnOpenCircuitWithoutConsumingAnHttpAttempt() {
        var registration = registrations.register(
                new ActivationCommand("EXT-" + UUID.randomUUID(), "CUST-001", "INTERNET-500"), "circuit-open", "local-demo");
        when(provider.activate(any())).thenReturn(new ActivationOutcome(
                ActivationOutcome.Kind.DEFERRED, "PROVIDER_CIRCUIT_OPEN", Duration.ofSeconds(30)));

        assertThat(worker.processNext()).isTrue();

        var request = registrations.get(registration.request().getId(), "local-demo");
        assertThat(request.getStatus()).isEqualTo(ActivationStatus.PENDING);
        assertThat(request.getAttemptCount()).isZero();
        assertThat(request.getNextAttemptAt()).isAfter(request.getUpdatedAt());
        assertThat(worker.processNext()).isFalse();
        assertThat(metrics.counter("activation.worker.results", "result", "DEFERRED").count()).isPositive();
    }

    @Test
    void endsBusinessFailuresWithoutRetrying() {
        var registration = registrations.register(
                new ActivationCommand("EXT-" + UUID.randomUUID(), "CUST-001", "BUSINESS-ERROR"), "worker-failure", "local-demo");
        when(provider.activate(any())).thenReturn(new ActivationOutcome(
                ActivationOutcome.Kind.PERMANENT_FAILURE, "PROVIDER_HTTP_400", Duration.ZERO));

        worker.processNext();

        var request = registrations.get(registration.request().getId(), "local-demo");
        assertThat(request.getStatus()).isEqualTo(ActivationStatus.FAILED);
        assertThat(request.getFailureCode()).isEqualTo("PROVIDER_HTTP_400");
        assertThat(worker.processNext()).isFalse();
    }

    @Test
    void retriesTransientFailuresAndEventuallyCompletes() {
        var registration = registrations.register(
                new ActivationCommand("EXT-" + UUID.randomUUID(), "CUST-001", "TEMPORARY-ERROR"), "worker-retry", "local-demo");
        when(provider.activate(any())).thenReturn(
                new ActivationOutcome(ActivationOutcome.Kind.TRANSIENT_FAILURE, "PROVIDER_HTTP_500", Duration.ZERO),
                new ActivationOutcome(ActivationOutcome.Kind.SUCCESS, "PROVIDER_SUCCESS", Duration.ZERO));

        worker.processNext();

        assertThat(registrations.get(registration.request().getId(), "local-demo").getStatus()).isEqualTo(ActivationStatus.PENDING);
        await().atMost(Duration.ofSeconds(3)).pollInterval(Duration.ofMillis(20)).untilAsserted(() -> {
            worker.processNext();
            assertThat(registrations.get(registration.request().getId(), "local-demo").getStatus())
                    .isEqualTo(ActivationStatus.COMPLETED);
        });
        assertThat(registrations.get(registration.request().getId(), "local-demo").getAttemptCount()).isEqualTo(2);
    }

    @Test
    void exposesProviderLatencyWithBoundedOutcomeLabels() {
        registrations.register(new ActivationCommand("EXT-" + UUID.randomUUID(), "CUST-001", "INTERNET-500"),
                "worker-metrics", "local-demo");
        when(provider.activate(any())).thenReturn(new ActivationOutcome(
                ActivationOutcome.Kind.SUCCESS, "PROVIDER_SUCCESS", Duration.ZERO));

        worker.processNext();

        var timer = metrics.find("activation.provider.calls").tag("outcome", "SUCCESS").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isPositive();
    }

    @Test
    void stopsAfterTheConfiguredAttemptBudget() {
        var registration = registrations.register(
                new ActivationCommand("EXT-" + UUID.randomUUID(), "CUST-001", "TEMPORARY-ERROR"), "worker-budget", "local-demo");
        when(provider.activate(any())).thenReturn(new ActivationOutcome(
                ActivationOutcome.Kind.TRANSIENT_FAILURE, "PROVIDER_HTTP_500", Duration.ZERO));

        await().atMost(Duration.ofSeconds(3)).pollInterval(Duration.ofMillis(20)).untilAsserted(() -> {
            worker.processNext();
            assertThat(registrations.get(registration.request().getId(), "local-demo").getStatus())
                    .isEqualTo(ActivationStatus.FAILED);
        });

        assertThat(registrations.get(registration.request().getId(), "local-demo").getAttemptCount()).isEqualTo(3);
        assertThat(worker.processNext()).isFalse();
    }

    @Test
    void doesNotResendAnUncertainActivationWithoutProviderIdempotency() {
        var registration = registrations.register(
                new ActivationCommand("EXT-" + UUID.randomUUID(), "CUST-001", "TIMEOUT"), "worker-uncertain", "local-demo");
        when(provider.activate(any())).thenReturn(new ActivationOutcome(
                ActivationOutcome.Kind.UNCERTAIN, "PROVIDER_IO_UNCERTAIN", Duration.ZERO));

        worker.processNext();

        var request = registrations.get(registration.request().getId(), "local-demo");
        assertThat(request.getStatus()).isEqualTo(ActivationStatus.FAILED);
        assertThat(request.getFailureCode()).isEqualTo("PROVIDER_IO_UNCERTAIN");
        assertThat(request.getAttemptCount()).isEqualTo(1);
        assertThat(worker.processNext()).isFalse();
    }

    @Test
    void callsProviderOutsideTheTransactionWithTheStoredCorrelationId() {
        registrations.register(new ActivationCommand("EXT-" + UUID.randomUUID(), "CUST-001", "INTERNET-500"),
                "worker-correlation", "local-demo");
        when(provider.activate(any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isEqualTo("worker-correlation");
            return new ActivationOutcome(ActivationOutcome.Kind.SUCCESS, "PROVIDER_SUCCESS", Duration.ZERO);
        });

        worker.processNext();

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void recordsUnexpectedProviderErrorsAsUncertainWithoutExposingTheirMessage() {
        var registration = registrations.register(
                new ActivationCommand("EXT-" + UUID.randomUUID(), "CUST-001", "INTERNET-500"), "worker-unexpected", "local-demo");
        when(provider.activate(any())).thenThrow(new IllegalStateException("internal-sensitive-detail"));

        worker.processNext();

        var request = registrations.get(registration.request().getId(), "local-demo");
        assertThat(request.getStatus()).isEqualTo(ActivationStatus.FAILED);
        assertThat(request.getFailureCode()).isEqualTo("PROVIDER_UNEXPECTED_ERROR");
        assertThat(worker.processNext()).isFalse();
    }
}
