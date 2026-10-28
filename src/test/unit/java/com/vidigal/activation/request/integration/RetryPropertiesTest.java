package com.vidigal.activation.request.integration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class RetryPropertiesTest {

    @ParameterizedTest
    @CsvSource({"0,1,30", "3,0,30", "3,-1,30", "3,31,30", "3,1,0"})
    void rejectsInvalidRetryConfiguration(int attempts, long initialDelay, long maxDelay) {
        Duration initial = Duration.ofSeconds(initialDelay);
        Duration maximum = Duration.ofSeconds(maxDelay);
        assertThatThrownBy(() -> new RetryProperties(attempts, initial, maximum, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, 1.0, Double.NaN, Double.POSITIVE_INFINITY})
    void rejectsJitterThatRemovesTheDelayOrCannotBeCalculated(double factor) {
        Duration initial = Duration.ofSeconds(1);
        Duration maximum = Duration.ofSeconds(30);

        assertThatThrownBy(() -> new RetryProperties(3, initial, maximum, factor))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
