package com.vidigal.activation.request.api;

final class RequestInputConstraints {
    static final int IDENTIFIER_LIMIT = 100;
    static final int PRODUCT_LIMIT = 80;
    static final String IDENTIFIER_PATTERN = "[A-Za-z0-9][A-Za-z0-9._:-]*";

    private RequestInputConstraints() {
    }
}
