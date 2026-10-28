package com.vidigal.activation.request.domain;

public record ActivationCommand(String externalId, String customerId, String productCode) {
}
