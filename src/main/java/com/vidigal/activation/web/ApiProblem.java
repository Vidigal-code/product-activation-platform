package com.vidigal.activation.web;

import java.net.URI;
import java.util.Locale;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

public enum ApiProblem {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Dados inválidos", "Um ou mais campos não passaram na validação."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "Requisição inválida", "O formato da requisição é inválido."),
    REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "Solicitação não encontrada", "A solicitação informada não existe."),
    DUPLICATE_REQUEST(HttpStatus.CONFLICT, "Solicitação conflitante", "O externalId já está associado a outro conteúdo."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno", "Não foi possível processar a solicitação."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Não autenticado", "Informe um token Bearer válido."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Acesso negado", "O token não permite esta operação.");

    private final HttpStatus status;
    private final String title;
    private final String detail;

    ApiProblem(HttpStatus status, String title, String detail) {
        this.status = status;
        this.title = title;
        this.detail = detail;
    }

    public ProblemDetail create() {
        String code = name().toLowerCase(Locale.ROOT).replace('_', '-');
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("urn:problem:" + code));
        problem.setProperty("code", code);
        problem.setProperty(CorrelationIdFilter.MDC_KEY, MDC.get(CorrelationIdFilter.MDC_KEY));
        return problem;
    }
}
