package com.vidigal.activation.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(properties = {
        "activation.security.mode=local", "activation.worker.enabled=false",
        "activation.rate-limit.cleanup-interval=50ms"
})
@Import(RateLimitMaintenanceIT.Database.class)
class RateLimitMaintenanceIT {
    @TestConfiguration(proxyBeanMethods = false)
    static class Database {
        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgres() {
            return new PostgreSQLContainer<>("postgres:17.11-alpine");
        }
    }

    @Autowired
    private ConsumerRateLimiter limiter;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void removesExpiredCountersWhileTheActivationWorkerIsDisabled() {
        String expiredConsumer = UUID.randomUUID().toString();
        String activeConsumer = UUID.randomUUID().toString();
        limiter.acquire(expiredConsumer);
        limiter.acquire(activeConsumer);
        jdbc.update("UPDATE api_rate_limits SET window_started_at = CURRENT_TIMESTAMP - INTERVAL '2 minutes' "
                + "WHERE consumer_id = ?", expiredConsumer);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(counterCount(expiredConsumer)).isZero());

        assertThat(counterCount(activeConsumer)).isEqualTo(1);
    }

    private int counterCount(String consumerId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM api_rate_limits WHERE consumer_id = ?",
                Integer.class, consumerId);
    }
}
