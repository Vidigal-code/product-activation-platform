package com.vidigal.activation.web;

public class PayloadTooLargeException extends RuntimeException {
    public PayloadTooLargeException() {
        super("Request body limit exceeded");
    }
}
