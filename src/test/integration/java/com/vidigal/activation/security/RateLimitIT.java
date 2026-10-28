package com.vidigal.activation.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.springframework.context.annotation.Import;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"activation.security.mode=local", "activation.worker.enabled=false",
        "activation.rate-limit.max-requests=2", "activation.rate-limit.window=1m"})
@AutoConfigureMockMvc
@Import(com.vidigal.activation.support.PostgresTestConfiguration.class)
class RateLimitIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ConsumerRateLimiter limiter;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void reservesExactlyTheQuotaDuringConcurrentRequests() throws Exception {
        String consumerId = UUID.randomUUID().toString();
        var attempts = IntStream.range(0, 12)
                .mapToObj(index -> (Callable<Boolean>) () -> limiter.acquire(consumerId).allowed()).toList();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            int allowed = 0;
            for (var result : executor.invokeAll(attempts)) {
                if (result.get()) {
                    allowed++;
                }
            }
            assertThat(allowed).isEqualTo(2);
        }
        assertThat(limiter.acquire(UUID.randomUUID().toString()).allowed()).isTrue();
    }

    @Test
    void renewsExpiredWindowAndRemovesOnlyExpiredCounters() {
        String consumerId = UUID.randomUUID().toString();
        assertThat(limiter.acquire(consumerId).allowed()).isTrue();
        assertThat(limiter.acquire(consumerId).allowed()).isTrue();
        assertThat(limiter.acquire(consumerId).allowed()).isFalse();
        expire(consumerId);
        assertThat(limiter.acquire(consumerId).allowed()).isTrue();
        expire(consumerId);
        assertThat(limiter.removeExpiredWindows()).isPositive();
        assertThat(limiter.acquire(consumerId).remaining()).isEqualTo(1);
    }

    private void expire(String consumerId) {
        jdbc.update("UPDATE api_rate_limits SET window_started_at = clock_timestamp() - INTERVAL '2 minutes' WHERE consumer_id = ?", consumerId);
    }

    @Test
    void rejectsRequestsAboveConsumerQuotaWithRetryAfter() throws Exception {
        String path = "/api/v1/requests/00000000-0000-0000-0000-000000000000";
        mvc.perform(get(path)).andExpect(status().isNotFound());
        mvc.perform(get(path)).andExpect(status().isNotFound());
        mvc.perform(get(path).header("X-Correlation-ID", "rate-test"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("rate-limited"))
                .andExpect(jsonPath("$.correlationId").value("rate-test"));
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
