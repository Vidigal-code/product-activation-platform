package com.vidigal.activation.request.domain;

public enum ActivationStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED;

    public boolean canTransitionTo(ActivationStatus target) {
        return switch (this) {
            case PENDING -> target == PROCESSING || target == FAILED;
            case PROCESSING -> target == PENDING || target == COMPLETED || target == FAILED;
            case COMPLETED, FAILED -> false;
        };
    }

    public ActivationStatus transitionTo(ActivationStatus target) {
        if (!canTransitionTo(target)) {
            throw new IllegalStateException("Transição de estado inválida: " + this + " -> " + target);
        }
        return target;
    }
}
