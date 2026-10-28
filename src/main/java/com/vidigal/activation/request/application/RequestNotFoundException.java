package com.vidigal.activation.request.application;

public class RequestNotFoundException extends RuntimeException {
    public RequestNotFoundException() {
        super("Solicitação não encontrada.");
    }
}
