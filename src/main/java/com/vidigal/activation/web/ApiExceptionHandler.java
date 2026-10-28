package com.vidigal.activation.web;

import com.vidigal.activation.request.application.RequestNotFoundException;
import com.vidigal.activation.request.application.DuplicateRequestException;
import com.vidigal.activation.request.application.RequestQueryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ProblemDetail;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(RequestNotFoundException.class)
    public ProblemDetail handleNotFound() {
        return ApiProblem.REQUEST_NOT_FOUND.create();
    }

    @ExceptionHandler(DuplicateRequestException.class)
    public ProblemDetail handleDuplicate() {
        return ApiProblem.DUPLICATE_REQUEST.create();
    }

    @ExceptionHandler(RequestQueryException.class)
    public ProblemDetail handleInvalidQuery() {
        return ApiProblem.INVALID_REQUEST.create();
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception exception) {
        LOG.error("Falha inesperada na API; tipo={}", exception.getClass().getName());
        return ApiProblem.INTERNAL_ERROR.create();
    }

    @ExceptionHandler(PayloadTooLargeException.class)
    public ProblemDetail handlePayloadTooLarge() {
        return ApiProblem.PAYLOAD_TOO_LARGE.create();
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (exception.contains(PayloadTooLargeException.class)) {
            return new ResponseEntity<>(ApiProblem.PAYLOAD_TOO_LARGE.create(), headers, HttpStatus.PAYLOAD_TOO_LARGE);
        }
        return handleExceptionInternal(exception, null, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var problem = ApiProblem.VALIDATION_ERROR.create();
        var violations = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
                .distinct().toList();
        problem.setProperty("errors", violations);
        return new ResponseEntity<>(problem, headers, status);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var problem = status.is5xxServerError() ? ApiProblem.INTERNAL_ERROR.create() : ApiProblem.INVALID_REQUEST.create();
        problem.setStatus(status.value());
        return super.handleExceptionInternal(exception, problem, headers, status, request);
    }

    public record FieldViolation(String field, String message) {
    }
}
