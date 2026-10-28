package com.vidigal.activation.request.api;

import com.vidigal.activation.request.domain.ActivationRequest;
import com.vidigal.activation.request.domain.ActivationStatus;
import java.time.Instant;
import java.util.UUID;

public record ActivationRequestResponse(UUID id, String externalId, String customerId, String productCode,
        ActivationStatus status, Instant createdAt, Instant updatedAt, int attemptCount, String failureCode) {

    public static ActivationRequestResponse from(ActivationRequest request) {
        return new ActivationRequestResponse(request.getId(), request.getExternalId(), request.getCustomerId(),
                request.getProductCode(), request.getStatus(), request.getCreatedAt(), request.getUpdatedAt(),
                request.getAttemptCount(), request.getFailureCode());
    }
}
