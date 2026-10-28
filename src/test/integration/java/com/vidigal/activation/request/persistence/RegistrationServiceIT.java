package com.vidigal.activation.request.persistence;

import com.vidigal.activation.request.application.RegistrationService;
import com.vidigal.activation.request.application.DuplicateRequestException;
import com.vidigal.activation.request.domain.ActivationCommand;
import com.vidigal.activation.request.domain.ActivationStatus;
import com.vidigal.activation.observability.ActivationTracing;
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

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.datasource.hikari.maximum-pool-size=2"
}, showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(RegistrationService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class RegistrationServiceIT {

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

    @MockitoBean
    private ActivationTracing tracing;

    @Test
    void registersPendingRequestAndMakesItRetrievable() {
        var command = new ActivationCommand("creation-001", "customer-001", "product-001");

        var registration = registrationService.register(command, "correlation-001", "local-demo");
        var found = registrationService.get(registration.request().getId(), "local-demo");

        assertThat(registration.created()).isTrue();
        assertThat(found.getExternalId()).isEqualTo("creation-001");
        assertThat(found.getCustomerId()).isEqualTo("customer-001");
        assertThat(found.getProductCode()).isEqualTo("product-001");
        assertThat(found.getStatus()).isEqualTo(ActivationStatus.PENDING);
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isEqualTo(found.getCreatedAt());
    }

    @Test
    void returnsExistingRequestWhenSameContentIsRepeated() {
        var command = new ActivationCommand("repeated-001", "customer-001", "product-001");
        var first = registrationService.register(command, "correlation-first", "local-demo");

        var repeated = registrationService.register(command, "correlation-repeated", "local-demo");

        assertThat(repeated.created()).isFalse();
        assertThat(repeated.request().getId()).isEqualTo(first.request().getId());
        assertThat(repeated.request().getCorrelationId()).isEqualTo("correlation-first");
    }

    @Test
    void rejectsAnExternalIdReusedWithDifferentContent() {
        var first = new ActivationCommand("conflict-001", "customer-001", "product-001");
        registrationService.register(first, "correlation-001", "local-demo");
        var changed = new ActivationCommand("conflict-001", "customer-002", "product-001");

        assertThatThrownBy(() -> registrationService.register(changed, "correlation-002", "local-demo"))
                .isInstanceOf(DuplicateRequestException.class);
    }

    @Test
    void concurrentIdenticalRequestsCreateExactlyOneRegistration() throws Exception {
        int callers = 12;
        var ready = new CountDownLatch(callers);
        var start = new CountDownLatch(1);
        var command = new ActivationCommand("concurrent-001", "customer-001", "product-001");
        var results = new ArrayList<RegistrationService.Registration>();

        try (var executor = Executors.newFixedThreadPool(callers)) {
            var futures = new ArrayList<Future<RegistrationService.Registration>>();
            for (int caller = 0; caller < callers; caller++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                    return registrationService.register(command, "concurrent-correlation", "local-demo");
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (var future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }
        }

        assertThat(results).filteredOn(RegistrationService.Registration::created).hasSize(1);
        assertThat(results).extracting(result -> result.request().getId()).containsOnly(results.getFirst().request().getId());
    }
}
