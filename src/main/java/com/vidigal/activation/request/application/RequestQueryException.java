package com.vidigal.activation.request.application;

public class RequestQueryException extends RuntimeException {

    public RequestQueryException() {
        super("Parâmetros de paginação fora dos limites permitidos");
    }
}
