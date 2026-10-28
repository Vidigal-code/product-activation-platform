package com.vidigal.activation.request.integration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CircuitBreakerPropertiesTest {

    @ParameterizedTest
    @CsvSource({"0,1,50,30,1", "10,0,50,30,1", "10,11,50,30,1", "10,5,0,30,1",
            "10,5,101,30,1", "10,5,NaN,30,1", "10,5,50,0,1", "10,5,50,-1,1", "10,5,50,30,0"})
    void rejectsInvalidCircuitConfiguration(int window, int minimumCalls, float failureRate, long openSeconds,
            int halfOpenCalls) {
        Duration openDuration = Duration.ofSeconds(openSeconds);

        assertThatThrownBy(() -> new CircuitBreakerProperties(window, minimumCalls, failureRate, openDuration, halfOpenCalls))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
