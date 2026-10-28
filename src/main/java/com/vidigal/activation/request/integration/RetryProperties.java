package com.vidigal.activation.request.integration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

@ConfigurationProperties("activation.retry")
public record RetryProperties(int maxAttempts, Duration initialDelay, Duration maxDelay) {

    public RetryProperties {
        Assert.isTrue(maxAttempts > 0, "Maximum attempts must be positive");
        Assert.notNull(initialDelay, "Initial delay is required");
        Assert.notNull(maxDelay, "Maximum delay is required");
        Assert.isTrue(initialDelay.compareTo(Duration.ZERO) > 0, "Initial delay must be positive");
        Assert.isTrue(maxDelay.compareTo(initialDelay) >= 0, "Maximum delay must not be less than initial delay");
    }
}
