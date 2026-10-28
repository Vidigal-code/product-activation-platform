package com.vidigal.activation.request.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ActivationStatusTest {

    @ParameterizedTest
    @CsvSource({
            "PENDING,PENDING,false", "PENDING,PROCESSING,true", "PENDING,COMPLETED,false", "PENDING,FAILED,true",
            "PROCESSING,PENDING,true", "PROCESSING,PROCESSING,false", "PROCESSING,COMPLETED,true", "PROCESSING,FAILED,true",
            "COMPLETED,PENDING,false", "COMPLETED,PROCESSING,false", "COMPLETED,COMPLETED,false", "COMPLETED,FAILED,false",
            "FAILED,PENDING,false", "FAILED,PROCESSING,false", "FAILED,COMPLETED,false", "FAILED,FAILED,false"
    })
    void enforcesTheCompleteTransitionMatrix(ActivationStatus source, ActivationStatus target, boolean permitted) {
        if (permitted) {
            assertThat(source.transitionTo(target)).isEqualTo(target);
        } else {
            assertThatThrownBy(() -> source.transitionTo(target)).isInstanceOf(IllegalStateException.class);
        }
    }
}
