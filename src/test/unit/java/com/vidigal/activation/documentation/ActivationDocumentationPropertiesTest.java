package com.vidigal.activation.documentation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ActivationDocumentationPropertiesTest {

    @ParameterizedTest
    @CsvSource({
            "0,60000,10000", "99,60000,10000", "10001,60000,10000",
            "500,0,10000", "500,999,100", "500,300001,10000",
            "500,60000,0", "500,60000,99", "500,300000,60001",
            "500,1000,2000", "2000,1000,500"
    })
    void rejectsIntervalsOutsideSafeBrowserExecutionLimits(long pollInterval, long timeout, long requestTimeout) {
        assertThatThrownBy(() -> new ActivationDocumentationProperties(pollInterval, timeout, requestTimeout))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
