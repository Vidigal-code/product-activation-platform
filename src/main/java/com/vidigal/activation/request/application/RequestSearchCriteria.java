package com.vidigal.activation.request.application;

import com.vidigal.activation.request.domain.ActivationStatus;

public record RequestSearchCriteria(ActivationStatus status, String customerId, int page, int size) {
}
