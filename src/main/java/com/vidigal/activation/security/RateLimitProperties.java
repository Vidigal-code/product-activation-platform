package com.vidigal.activation.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

@ConfigurationProperties("activation.rate-limit")
public record RateLimitProperties(int maxRequests, Duration window) {
    public RateLimitProperties {
        Assert.isTrue(maxRequests > 0 && maxRequests <= 1_000_000, "Rate limit must be between 1 and 1000000");
        Assert.notNull(window, "Rate limit window is required");
        Assert.isTrue(window.compareTo(Duration.ofSeconds(1)) >= 0
                && window.compareTo(Duration.ofDays(1)) <= 0, "Rate limit window must be between 1 second and 1 day");
    }
}
