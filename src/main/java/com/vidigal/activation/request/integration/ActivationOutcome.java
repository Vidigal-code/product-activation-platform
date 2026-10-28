package com.vidigal.activation.request.integration;

import java.time.Duration;

public record ActivationOutcome(Kind kind, String code, Duration retryAfter) {

    public enum Kind {
        SUCCESS,
        PERMANENT_FAILURE,
        TRANSIENT_FAILURE,
        UNCERTAIN
    }
}
