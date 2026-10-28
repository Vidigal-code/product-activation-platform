package com.vidigal.activation.request.integration;

import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class RetryPolicy {

    private final RetryProperties properties;

    public RetryPolicy(RetryProperties properties) {
        this.properties = properties;
    }

    public Duration delayAfter(int attempt, Duration retryAfter) {
        Duration delay = properties.initialDelay();
        Duration maximum = properties.maxDelay();
        for (int previousAttempt = 1; previousAttempt < attempt && delay.compareTo(maximum) < 0; previousAttempt++) {
            delay = delay.compareTo(maximum.minus(delay)) >= 0 ? maximum : delay.multipliedBy(2);
        }
        return retryAfter.compareTo(delay) > 0 ? retryAfter : delay;
    }

    public boolean canRetry(ActivationOutcome.Kind kind, int attempt, boolean idempotencySupported) {
        return attempt < properties.maxAttempts()
                && (kind == ActivationOutcome.Kind.TRANSIENT_FAILURE
                        || kind == ActivationOutcome.Kind.UNCERTAIN && idempotencySupported);
    }
}
