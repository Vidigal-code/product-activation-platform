package com.vidigal.activation.documentation;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

@ConfigurationProperties("activation.documentation")
public record ActivationDocumentationProperties(long pollIntervalMs, long timeoutMs, long requestTimeoutMs) {

    private static final long MIN_INTERVAL_MS = 100;
    private static final long MAX_POLL_INTERVAL_MS = 10_000;
    private static final long MIN_TIMEOUT_MS = 1_000;
    private static final long MAX_TIMEOUT_MS = 300_000;
    private static final long MAX_REQUEST_TIMEOUT_MS = 60_000;

    public ActivationDocumentationProperties {
        Assert.isTrue(pollIntervalMs >= MIN_INTERVAL_MS && pollIntervalMs <= MAX_POLL_INTERVAL_MS,
                "Documentation polling interval must be between 100 and 10000 milliseconds");
        Assert.isTrue(timeoutMs >= MIN_TIMEOUT_MS && timeoutMs <= MAX_TIMEOUT_MS,
                "Documentation timeout must be between 1000 and 300000 milliseconds");
        Assert.isTrue(requestTimeoutMs >= MIN_INTERVAL_MS && requestTimeoutMs <= MAX_REQUEST_TIMEOUT_MS,
                "Documentation request timeout must be between 100 and 60000 milliseconds");
        Assert.isTrue(pollIntervalMs <= timeoutMs && requestTimeoutMs <= timeoutMs,
                "Documentation polling and request timeouts must not exceed the scenario timeout");
    }
}
