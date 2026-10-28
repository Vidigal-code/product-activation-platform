package com.vidigal.activation.request.api;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

@ConfigurationProperties("activation.pagination")
public record PaginationProperties(int defaultSize, int maxSize, int maxPage) {

    public PaginationProperties {
        Assert.isTrue(defaultSize > 0, "Default page size must be positive");
        Assert.isTrue(maxSize >= defaultSize, "Maximum page size must include the default size");
        Assert.isTrue(maxPage >= 0, "Maximum page number must not be negative");
        Assert.isTrue((long) maxPage * maxSize <= Integer.MAX_VALUE, "Maximum page offset exceeds the JPA integer limit");
    }
}
