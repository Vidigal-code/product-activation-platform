package com.vidigal.activation.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.vidigal.activation.request.application.RegistrationService;
import com.vidigal.activation.request.domain.ActivationCommand;
import com.vidigal.activation.request.domain.ActivationStatus;
import com.vidigal.activation.request.persistence.ActivationQueue;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(properties = {
        "activation.security.mode=local", "activation.worker.enabled=false",
        "activation.observability.refresh-interval=50ms"
})
@Import(RequestMetricsIT.Database.class)
class RequestMetricsIT {
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
    private ActivationQueue queue;

    @Autowired
    private MeterRegistry registry;

    @Test
    void refreshesAllPersistentStatusCountsWithoutTheActivationWorker() {
        register();
        assertThat(queue.complete(queue.reserve(Duration.ofMinutes(1), 3, false).orElseThrow())).isTrue();
        register();
        assertThat(queue.fail(queue.reserve(Duration.ofMinutes(1), 3, false).orElseThrow(), "PROVIDER_HTTP_400"))
                .isTrue();
        register();
        queue.reserve(Duration.ofMinutes(1), 3, false).orElseThrow();
        register();

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            for (ActivationStatus status : ActivationStatus.values()) {
                assertStatusCount(status, 1);
            }
        });

        assertThat(queue.complete(queue.reserve(Duration.ofMinutes(1), 3, false).orElseThrow())).isTrue();

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertStatusCount(ActivationStatus.PENDING, 0);
            assertStatusCount(ActivationStatus.PROCESSING, 1);
            assertStatusCount(ActivationStatus.COMPLETED, 2);
            assertStatusCount(ActivationStatus.FAILED, 1);
        });
    }

    private void register() {
        registrations.register(new ActivationCommand("metrics-" + UUID.randomUUID(), "customer-1", "product-1"),
                "metrics-test", "local-demo");
    }

    private void assertStatusCount(ActivationStatus status, long expected) {
        var gauge = registry.find("activation.requests").tag("status", status.name()).gauge();
        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isEqualTo(expected);
    }
}
