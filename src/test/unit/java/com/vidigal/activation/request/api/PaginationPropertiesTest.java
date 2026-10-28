package com.vidigal.activation.request.api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PaginationPropertiesTest {

    @Test
    void rejectsAnOffsetLargerThanJpaSupports() {
        assertThatThrownBy(() -> new PaginationProperties(20, 100, Integer.MAX_VALUE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("offset");
    }
}
