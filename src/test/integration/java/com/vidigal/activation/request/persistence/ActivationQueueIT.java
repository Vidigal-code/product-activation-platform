package com.vidigal.activation.request.persistence;

import com.vidigal.activation.request.application.RegistrationService;
import com.vidigal.activation.request.domain.ActivationCommand;
import com.vidigal.activation.request.domain.ActivationStatus;
import com.vidigal.activation.observability.ActivationTracing;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate", showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({RegistrationService.class, ActivationQueue.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class ActivationQueueIT {

    private static final Duration LEASE = Duration.ofSeconds(30);
    private static final int MAX_ATTEMPTS = 3;

    @Container
    static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>("postgres:17.11-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
    }

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private ActivationQueue queue;

    @Autowired
    private ActivationRequestRepository repository;

    @MockitoBean
    private ActivationTracing tracing;

    @BeforeEach
    void clearRequests() {
        repository.deleteAllInBatch();
    }

    @Test
    void reservesAPendingRequestForOneWorker() {
        var registration = registrationService.register(
                new ActivationCommand("queue-001", "customer-001", "product-001"), "correlation-001", "local-demo");

        var reserved = queue.reserve(LEASE, MAX_ATTEMPTS, true).orElseThrow();

        assertThat(reserved.requestId()).isEqualTo(registration.request().getId());
        assertThat(reserved.leaseToken()).isNotNull();
        assertThat(reserved.attemptCount()).isEqualTo(1);
        assertThat(registrationService.get(reserved.requestId(), "local-demo").getStatus()).isEqualTo(ActivationStatus.PROCESSING);
        assertThat(queue.reserve(LEASE, MAX_ATTEMPTS, true)).isEmpty();
    }

    @Test
    void reservesNewRequestsImmediatelyWhenApplicationClockIsAhead() {
        Instant advancedTime = Instant.now().plus(Duration.ofMinutes(5));
        RegistrationService.Registration registration;
        try (var applicationTime = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            applicationTime.when(Instant::now).thenReturn(advancedTime);
            registration = registrationService.register(
                    new ActivationCommand("clock-ahead", "customer-001", "product-001"),
                    "correlation-001", "local-demo");
        }

        var reserved = queue.reserve(LEASE, MAX_ATTEMPTS, true);

        assertThat(reserved).isPresent();
        assertThat(reserved.orElseThrow().requestId()).isEqualTo(registration.request().getId());
        assertThat(registration.request().getCreatedAt()).isNotNull();
        assertThat(registration.request().getUpdatedAt()).isEqualTo(registration.request().getCreatedAt());
        assertThat(registration.request().getNextAttemptAt()).isEqualTo(registration.request().getCreatedAt());
    }

    @Test
    void preservesTheRequestTraceContextAcrossThePersistentQueue() {
        String traceParent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
        when(tracing.currentTraceParent()).thenReturn(traceParent);
        var created = registrationService.register(
                new ActivationCommand("trace-persistence", "customer-001", "product-001"), "correlation", "local-demo");

        var reserved = queue.reserve(LEASE, MAX_ATTEMPTS, true).orElseThrow();

        assertThat(registrationService.get(created.request().getId(), "local-demo").getTraceParent()).isEqualTo(traceParent);
        assertThat(reserved.traceParent()).isEqualTo(traceParent);
    }

    @Test
    void completesAReservationAndRejectsRepeatedTransitions() {
        registrationService.register(new ActivationCommand("complete-001", "customer-001", "product-001"), "correlation", "local-demo");
        var reserved = queue.reserve(LEASE, MAX_ATTEMPTS, true).orElseThrow();

        assertThat(queue.complete(reserved)).isTrue();

        assertThat(registrationService.get(reserved.requestId(), "local-demo").getStatus()).isEqualTo(ActivationStatus.COMPLETED);
        assertThat(queue.complete(reserved)).isFalse();
        assertThat(queue.reserve(LEASE, MAX_ATTEMPTS, true)).isEmpty();
    }

    @Test
    void recordsAPermanentFailureWithoutReturningItToTheQueue() {
        registrationService.register(new ActivationCommand("failed-001", "customer-001", "product-001"), "correlation", "local-demo");
        var reserved = queue.reserve(LEASE, MAX_ATTEMPTS, true).orElseThrow();

        assertThat(queue.fail(reserved, "PROVIDER_REJECTED")).isTrue();

        var failed = registrationService.get(reserved.requestId(), "local-demo");
        assertThat(failed.getStatus()).isEqualTo(ActivationStatus.FAILED);
        assertThat(failed.getFailureCode()).isEqualTo("PROVIDER_REJECTED");
        assertThat(queue.complete(reserved)).isFalse();
        assertThat(queue.reserve(LEASE, MAX_ATTEMPTS, true)).isEmpty();
    }

    @Test
    void persistsTheNextAttemptAndDoesNotRunItBeforeItsScheduledTime() {
        registrationService.register(new ActivationCommand("retry-001", "customer-001", "product-001"), "correlation", "local-demo");
        var reserved = queue.reserve(LEASE, MAX_ATTEMPTS, true).orElseThrow();

        assertThat(queue.retry(reserved, Duration.ofHours(1), "PROVIDER_UNAVAILABLE")).isTrue();

        var deferred = registrationService.get(reserved.requestId(), "local-demo");
        assertThat(deferred.getStatus()).isEqualTo(ActivationStatus.PENDING);
        assertThat(deferred.getNextAttemptAt()).isAfter(deferred.getUpdatedAt().plus(Duration.ofMinutes(59)));
        assertThat(deferred.getFailureCode()).isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat(deferred.getLeaseToken()).isNull();
        assertThat(queue.reserve(LEASE, MAX_ATTEMPTS, true)).isEmpty();
    }

    @Test
    void defersUnattemptedWorkWithoutConsumingTheAttemptBudget() {
        registrationService.register(new ActivationCommand("circuit-open", "customer-001", "product-001"), "correlation", "local-demo");
        var reserved = queue.reserve(LEASE, MAX_ATTEMPTS, true).orElseThrow();

        assertThat(queue.defer(reserved, Duration.ofHours(1))).isTrue();

        var deferred = registrationService.get(reserved.requestId(), "local-demo");
        assertThat(deferred.getStatus()).isEqualTo(ActivationStatus.PENDING);
        assertThat(deferred.getAttemptCount()).isZero();
        assertThat(deferred.getNextAttemptAt()).isAfter(deferred.getUpdatedAt().plus(Duration.ofMinutes(59)));
        assertThat(deferred.getLeaseToken()).isNull();
        assertThat(queue.defer(reserved, Duration.ofHours(1))).isFalse();
        assertThat(queue.reserve(LEASE, MAX_ATTEMPTS, true)).isEmpty();
    }

    @Test
    void recoversAnExpiredLeaseAndFencesThePreviousWorker() {
        registrationService.register(new ActivationCommand("recover-001", "customer-001", "product-001"), "correlation", "local-demo");
        var expired = queue.reserve(Duration.ofMillis(100), MAX_ATTEMPTS, true).orElseThrow();

        var recovered = await().atMost(Duration.ofSeconds(3)).until(
                () -> queue.reserve(LEASE, MAX_ATTEMPTS, true).orElse(null), Objects::nonNull);

        assertThat(recovered.requestId()).isEqualTo(expired.requestId());
        assertThat(recovered.leaseToken()).isNotEqualTo(expired.leaseToken());
        assertThat(recovered.attemptCount()).isEqualTo(2);
        assertThat(queue.complete(expired)).isFalse();
        assertThat(queue.fail(expired, "STALE_FAILURE")).isFalse();
        assertThat(queue.retry(expired, Duration.ZERO, "STALE_RETRY")).isFalse();
        assertThat(queue.defer(expired, Duration.ZERO)).isFalse();
        assertThat(queue.complete(recovered)).isTrue();
        assertThat(registrationService.get(recovered.requestId(), "local-demo").getStatus()).isEqualTo(ActivationStatus.COMPLETED);
    }

    @Test
    void stopsExpiredWorkForReconciliationWhenProviderDoesNotSupportIdempotency() {
        registrationService.register(new ActivationCommand("uncertain-001", "customer-001", "product-001"), "correlation", "local-demo");
        var expired = queue.reserve(Duration.ofMillis(100), MAX_ATTEMPTS, false).orElseThrow();

        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            assertThat(queue.reserve(LEASE, MAX_ATTEMPTS, false)).isEmpty();
            assertThat(registrationService.get(expired.requestId(), "local-demo").getStatus()).isEqualTo(ActivationStatus.FAILED);
        });

        assertThat(registrationService.get(expired.requestId(), "local-demo").getFailureCode()).isEqualTo("RESULT_UNKNOWN");
        assertThat(registrationService.get(expired.requestId(), "local-demo").getAttemptCount()).isEqualTo(1);
        assertThat(queue.complete(expired)).isFalse();
    }

    @Test
    void stopsExpiredWorkAfterTheAttemptLimitEvenWithProviderIdempotency() {
        registrationService.register(new ActivationCommand("exhausted-lease", "customer-001", "product-001"), "correlation", "local-demo");
        var expired = queue.reserve(Duration.ofMillis(100), 1, true).orElseThrow();

        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            assertThat(queue.reserve(LEASE, 1, true)).isEmpty();
            assertThat(registrationService.get(expired.requestId(), "local-demo").getStatus()).isEqualTo(ActivationStatus.FAILED);
        });

        assertThat(registrationService.get(expired.requestId(), "local-demo").getFailureCode()).isEqualTo("RESULT_UNKNOWN");
    }

    @Test
    void finalizesPendingWorkWhenTheAttemptLimitWasReduced() {
        registrationService.register(new ActivationCommand("reduced-limit", "customer-001", "product-001"), "correlation", "local-demo");
        var reserved = queue.reserve(LEASE, MAX_ATTEMPTS, true).orElseThrow();
        queue.retry(reserved, Duration.ZERO, "PROVIDER_UNAVAILABLE");

        assertThat(queue.reserve(LEASE, 1, true)).isEmpty();

        var exhausted = registrationService.get(reserved.requestId(), "local-demo");
        assertThat(exhausted.getStatus()).isEqualTo(ActivationStatus.FAILED);
        assertThat(exhausted.getFailureCode()).isEqualTo("ATTEMPTS_EXHAUSTED");
    }

    @Test
    void concurrentWorkersReserveTheRequestExactlyOnce() throws Exception {
        registrationService.register(new ActivationCommand("concurrent-workers", "customer-001", "product-001"), "correlation", "local-demo");
        int workers = 12;
        var ready = new CountDownLatch(workers);
        var start = new CountDownLatch(1);
        var reservations = new ArrayList<ReservedActivation>();

        try (var executor = Executors.newFixedThreadPool(workers)) {
            var futures = new ArrayList<Future<Optional<ReservedActivation>>>();
            for (int worker = 0; worker < workers; worker++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                    return queue.reserve(LEASE, MAX_ATTEMPTS, true);
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (var future : futures) {
                future.get(10, TimeUnit.SECONDS).ifPresent(reservations::add);
            }
        }

        assertThat(reservations).hasSize(1);
        assertThat(reservations.getFirst().attemptCount()).isEqualTo(1);
    }

    @Test
    void rejectsExpiredWorkersEvenBeforeAnotherWorkerReservesTheRequest() {
        registrationService.register(new ActivationCommand("expired-token", "customer-001", "product-001"), "correlation", "local-demo");
        var expired = queue.reserve(Duration.ofMillis(100), MAX_ATTEMPTS, true).orElseThrow();
        var expiration = registrationService.get(expired.requestId(), "local-demo").getLeaseUntil();
        await().atMost(Duration.ofSeconds(3)).until(() -> expiration.isBefore(Instant.now().minusMillis(100)));

        assertThat(queue.complete(expired)).isFalse();
        assertThat(queue.fail(expired, "STALE_FAILURE")).isFalse();
        assertThat(queue.retry(expired, Duration.ZERO, "STALE_RETRY")).isFalse();
        assertThat(queue.defer(expired, Duration.ZERO)).isFalse();
        assertThat(registrationService.get(expired.requestId(), "local-demo").getStatus()).isEqualTo(ActivationStatus.PROCESSING);
    }
}
