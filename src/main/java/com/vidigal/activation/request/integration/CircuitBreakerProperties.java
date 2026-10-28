package com.vidigal.activation.request.integration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

@ConfigurationProperties("activation.circuit-breaker")
public record CircuitBreakerProperties(int slidingWindowSize, int minimumNumberOfCalls, float failureRateThreshold,
        Duration openDuration, int halfOpenCalls) {

    public CircuitBreakerProperties {
        Assert.isTrue(slidingWindowSize > 0, "Circuit breaker window must be positive");
        Assert.isTrue(minimumNumberOfCalls > 0 && minimumNumberOfCalls <= slidingWindowSize,
                "Minimum circuit breaker calls must fit within the sliding window");
        Assert.isTrue(Float.isFinite(failureRateThreshold) && failureRateThreshold > 0 && failureRateThreshold <= 100,
                "Circuit breaker failure threshold must be finite and between zero exclusive and 100 inclusive");
        Assert.notNull(openDuration, "Circuit breaker open duration is required");
        Assert.isTrue(openDuration.compareTo(Duration.ZERO) > 0, "Circuit breaker open duration must be positive");
        Assert.isTrue(halfOpenCalls > 0, "Circuit breaker half-open calls must be positive");
    }
}
