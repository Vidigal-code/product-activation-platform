package com.vidigal.activation.security;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

@ConfigurationProperties("activation.security")
public record SecurityProperties(String mode, String issuerUri, String audience) {

    public static final String JWT_MODE = "jwt";
    public static final String LOCAL_MODE = "local";

    public SecurityProperties {
        Assert.isTrue(JWT_MODE.equals(mode) || LOCAL_MODE.equals(mode), "Security mode must be jwt or local");
        if (JWT_MODE.equals(mode)) {
            Assert.hasText(issuerUri, "JWT issuer URI is required");
            Assert.hasText(audience, "JWT audience is required");
            validateIssuer(URI.create(issuerUri));
        }
    }

    private static void validateIssuer(URI issuer) {
        Assert.isTrue(issuer.getHost() != null && issuer.getUserInfo() == null
                        && issuer.getQuery() == null && issuer.getFragment() == null,
                "JWT issuer must have a host and no credentials, query or fragment");
        boolean loopback = "localhost".equalsIgnoreCase(issuer.getHost())
                || "127.0.0.1".equals(issuer.getHost()) || "[::1]".equals(issuer.getHost());
        Assert.isTrue("https".equalsIgnoreCase(issuer.getScheme())
                        || loopback && "http".equalsIgnoreCase(issuer.getScheme()),
                "JWT issuer must use HTTPS outside loopback addresses");
    }
}
