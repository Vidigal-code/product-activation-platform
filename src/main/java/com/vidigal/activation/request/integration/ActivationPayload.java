package com.vidigal.activation.request.integration;

import java.util.UUID;

public record ActivationPayload(
        UUID requestId, String externalId, String customerId, String productCode, String correlationId) {
}
