package com.vidigal.activation.request.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {

    private final RetryPolicy policy = new RetryPolicy(
            new RetryProperties(5, Duration.ofSeconds(1), Duration.ofSeconds(30), 0));

    @ParameterizedTest
    @CsvSource({"1,0,1", "2,0,2", "3,0,4", "6,0,30", "2147483647,0,30", "1,17,17",
            "2,300,300", "1,120,120"})
    void boundsExponentialBackoffWithoutAnticipatingProviderRetryAfter(int attempt, long retryAfter,
            long expectedSeconds) {
        Duration delay = policy.delayAfter(attempt, Duration.ofSeconds(retryAfter));

        assertThat(delay).isEqualTo(Duration.ofSeconds(expectedSeconds));
    }

    @ParameterizedTest
    @CsvSource({"TRANSIENT_FAILURE,1,false,true", "TRANSIENT_FAILURE,4,true,true",
            "TRANSIENT_FAILURE,5,true,false", "UNCERTAIN,1,false,false", "UNCERTAIN,1,true,true",
            "UNCERTAIN,5,true,false", "PERMANENT_FAILURE,1,true,false", "SUCCESS,1,true,false"})
    void retriesOnlySafeFailuresWithinAttemptLimit(ActivationOutcome.Kind kind, int attempt,
            boolean idempotencySupported, boolean expected) {
        assertThat(policy.canRetry(kind, attempt, idempotencySupported)).isEqualTo(expected);
    }

    @Test
    void spreadsBackoffWithBoundedJitterWithoutAnticipatingRetryAfter() {
        RetryPolicy jittered = new RetryPolicy(new RetryProperties(5, Duration.ofSeconds(1), Duration.ofSeconds(30), 0.2),
                () -> 1.0);

        assertThat(jittered.delayAfter(3, Duration.ZERO)).isEqualTo(Duration.ofMillis(3200));
        assertThat(jittered.delayAfter(10, Duration.ZERO)).isEqualTo(Duration.ofSeconds(24));
        assertThat(jittered.delayAfter(3, Duration.ofSeconds(5))).isEqualTo(Duration.ofSeconds(5));
    }
}
