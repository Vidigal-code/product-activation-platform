package com.vidigal.activation.request.integration;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RetryPolicy {

    private static final int JITTER_PRECISION = 1_000_000;
    private final RetryProperties properties;
    private final DoubleSupplier random;

    @Autowired
    public RetryPolicy(RetryProperties properties) {
        this(properties, () -> ThreadLocalRandom.current().nextDouble());
    }

    RetryPolicy(RetryProperties properties, DoubleSupplier random) {
        this.properties = properties;
        this.random = random;
    }

    public Duration delayAfter(int attempt, Duration retryAfter) {
        Duration delay = properties.initialDelay();
        Duration maximum = properties.maxDelay();
        for (int previousAttempt = 1; previousAttempt < attempt && delay.compareTo(maximum) < 0; previousAttempt++) {
            delay = delay.compareTo(maximum.minus(delay)) >= 0 ? maximum : delay.multipliedBy(2);
        }
        long jitterParts = (long) (random.getAsDouble() * properties.jitterFactor() * JITTER_PRECISION);
        delay = delay.minus(delay.dividedBy(JITTER_PRECISION).multipliedBy(jitterParts));
        return retryAfter.compareTo(delay) > 0 ? retryAfter : delay;
    }

    public int maxAttempts() {
        return properties.maxAttempts();
    }

    public boolean canRetry(ActivationOutcome.Kind kind, int attempt, boolean idempotencySupported) {
        return attempt < properties.maxAttempts()
                && (kind == ActivationOutcome.Kind.TRANSIENT_FAILURE
                        || kind == ActivationOutcome.Kind.UNCERTAIN && idempotencySupported);
    }
}
