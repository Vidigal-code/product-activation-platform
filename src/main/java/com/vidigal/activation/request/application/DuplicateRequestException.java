package com.vidigal.activation.request.application;

public class DuplicateRequestException extends RuntimeException {
    public DuplicateRequestException() {
        super("O externalId já está associado a uma solicitação com conteúdo diferente.");
    }
}
