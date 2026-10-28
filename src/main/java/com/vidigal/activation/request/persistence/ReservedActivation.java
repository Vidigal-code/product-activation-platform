package com.vidigal.activation.request.persistence;

import java.util.UUID;

public record ReservedActivation(UUID requestId, UUID leaseToken, String externalId, String customerId,
                                 String productCode, String correlationId, int attemptCount, String traceParent) {
}
