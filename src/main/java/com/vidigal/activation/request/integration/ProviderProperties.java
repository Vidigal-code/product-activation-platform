package com.vidigal.activation.request.integration;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

@ConfigurationProperties("activation.provider")
public record ProviderProperties(
        URI baseUrl, Duration connectTimeout, Duration readTimeout, boolean idempotencySupported, String token,
        String activationPath) {

    public ProviderProperties {
        Assert.notNull(baseUrl, "Provider base URL is required");
        Assert.isTrue("http".equalsIgnoreCase(baseUrl.getScheme()) || "https".equalsIgnoreCase(baseUrl.getScheme()),
                "Provider base URL must use HTTP or HTTPS");
        Assert.isTrue(baseUrl.getHost() != null && baseUrl.getUserInfo() == null
                        && baseUrl.getQuery() == null && baseUrl.getFragment() == null,
                "Provider base URL must contain a host and no credentials, query or fragment");
        validateCredentialTransport(baseUrl, token);
        Assert.notNull(connectTimeout, "Connection timeout is required");
        Assert.notNull(readTimeout, "Read timeout is required");
        Assert.isTrue(connectTimeout.compareTo(Duration.ZERO) > 0, "Connection timeout must be positive");
        Assert.isTrue(readTimeout.compareTo(Duration.ZERO) > 0, "Read timeout must be positive");
        validateActivationPath(activationPath);
    }

    private static void validateCredentialTransport(URI baseUrl, String token) {
        boolean loopback = "localhost".equalsIgnoreCase(baseUrl.getHost())
                || "127.0.0.1".equals(baseUrl.getHost()) || "[::1]".equals(baseUrl.getHost());
        Assert.isTrue(token == null || token.isBlank() || "https".equalsIgnoreCase(baseUrl.getScheme()) || loopback,
                "Provider credentials require HTTPS outside loopback addresses");
    }

    private static void validateActivationPath(String activationPath) {
        Assert.hasText(activationPath, "Activation path is required");
        URI path = URI.create(activationPath);
        Assert.isTrue(activationPath.startsWith("/") && !activationPath.startsWith("//")
                        && path.getQuery() == null && path.getFragment() == null
                        && activationPath.equals(path.normalize().getPath()),
                "Activation path must be a local normalized path without query, fragment or encoded segments");
    }

    @Override
    public String toString() {
        return "ProviderProperties[baseUrl=%s, connectTimeout=%s, readTimeout=%s, idempotencySupported=%s]"
                .formatted(baseUrl, connectTimeout, readTimeout, idempotencySupported);
    }
}
