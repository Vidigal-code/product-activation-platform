package com.vidigal.activation.web;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

@ConfigurationProperties("activation.http")
public record RequestBodyProperties(int maxBodyBytes) {
    public RequestBodyProperties {
        Assert.isTrue(maxBodyBytes > 0, "Maximum request body size must be positive");
    }
}
