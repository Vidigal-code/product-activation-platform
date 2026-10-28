package com.vidigal.activation.documentation;

import com.vidigal.activation.request.api.ActivationRequestController;
import com.vidigal.activation.request.api.ActivationRequestPageResponse;
import com.vidigal.activation.request.api.ActivationRequestResponse;
import com.vidigal.activation.web.ApiProblem;
import com.vidigal.activation.web.CorrelationIdFilter;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;

final class ActivationApiResponses {

    static final String PROBLEM_SCHEMA = "ApiProblem";
    private static final String SCHEMA_PREFIX = "#/components/schemas/";
    private static final String EXAMPLE_CORRELATION_ID = "swagger-demo-001";
    private static final String FIELD_TITLE = "title";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_DETAIL = "detail";
    private static final String VIOLATION_FIELD = "field";
    private static final String VIOLATION_MESSAGE = "message";

    private ActivationApiResponses() {
    }

    static ApiResponses creation() {
        return commonErrors()
                .addApiResponse("201", success("Solicitação criada; processamento assíncrono pendente.",
                        Map.of("pendente", ActivationApiExamples.responses().get("pendente")))
                        .addHeaderObject(HttpHeaders.LOCATION, new Header().description("Endereço relativo para consultar o id criado.")
                                .schema(new StringSchema()).example(ActivationRequestController.PATH + "/00000000-0000-0000-0000-000000000001")))
                .addApiResponse("200", success("Mesmo externalId e conteúdo: solicitação existente em seu estado atual.",
                        ActivationApiExamples.responses()))
                .addApiResponse("400", error(ApiProblem.VALIDATION_ERROR)
                        .description("Campos inválidos, JSON malformado ou propriedades desconhecidas.")
                        .content(problemContent(ApiProblem.VALIDATION_ERROR, ApiProblem.INVALID_REQUEST)))
                .addApiResponse("409", error(ApiProblem.DUPLICATE_REQUEST))
                .addApiResponse("413", error(ApiProblem.PAYLOAD_TOO_LARGE))
                .addApiResponse("415", errorWithStatus(ApiProblem.INVALID_REQUEST, 415)
                        .description("Content-Type não suportado; envie application/json."));
    }

    static ApiResponses lookup() {
        return commonErrors()
                .addApiResponse("200", success("Estado atual da solicitação; failureCode explica uma falha final.",
                        ActivationApiExamples.responses()))
                .addApiResponse("400", error(ApiProblem.INVALID_REQUEST).description("O parâmetro id deve ser um UUID válido."))
                .addApiResponse("404", error(ApiProblem.REQUEST_NOT_FOUND));
    }

    static ApiResponses listing(int pageSize) {
        Map<String, Object> page = Map.of("items", List.of(ActivationApiExamples.responses().get("concluida").getValue()),
                "page", 0, "size", pageSize, "totalElements", 1, "totalPages", 1, "hasNext", false);
        return commonErrors()
                .addApiResponse("200", correlatedResponse("Página de solicitações pertencentes ao consumidor autenticado.")
                        .content(new Content().addMediaType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                                new MediaType().schema(new Schema<>().$ref(SCHEMA_PREFIX + ActivationRequestPageResponse.class.getSimpleName()))
                                        .addExamples("pagina", new Example().summary("Primeira página com uma solicitação").value(page)))))
                .addApiResponse("400", error(ApiProblem.VALIDATION_ERROR)
                        .description("Filtro inválido ou paginação fora dos limites configurados.")
                        .content(problemContent(ApiProblem.VALIDATION_ERROR, ApiProblem.INVALID_REQUEST)));
    }

    static ObjectSchema problemSchema() {
        ObjectSchema violation = new ObjectSchema();
        violation.setRequired(List.of(VIOLATION_FIELD, VIOLATION_MESSAGE));
        violation.addProperty(VIOLATION_FIELD, new StringSchema());
        violation.addProperty(VIOLATION_MESSAGE, new StringSchema());
        ArraySchema errors = new ArraySchema();
        errors.setDescription("Violações de campos, quando houver.");
        errors.setItems(violation);
        ObjectSchema problem = new ObjectSchema();
        problem.setRequired(List.of("type", FIELD_TITLE, FIELD_STATUS, FIELD_DETAIL, "code", CorrelationIdFilter.MDC_KEY));
        problem.addProperty("type", new StringSchema().format("uri"));
        problem.addProperty(FIELD_TITLE, new StringSchema());
        problem.addProperty(FIELD_STATUS, new IntegerSchema());
        problem.addProperty(FIELD_DETAIL, new StringSchema());
        problem.addProperty("instance", new StringSchema().format("uri-reference"));
        problem.addProperty("code", new StringSchema());
        problem.addProperty(CorrelationIdFilter.MDC_KEY, new StringSchema());
        problem.addProperty("errors", errors);
        return problem;
    }

    private static ApiResponses commonErrors() {
        return new ApiResponses()
                .addApiResponse("401", error(ApiProblem.UNAUTHORIZED).description("JWT ausente ou inválido em modo JWT."))
                .addApiResponse("403", error(ApiProblem.FORBIDDEN).description("JWT sem o escopo exigido pela operação."))
                .addApiResponse("429", error(ApiProblem.RATE_LIMITED)
                        .addHeaderObject(HttpHeaders.RETRY_AFTER, new Header().description("Segundos mínimos antes de nova chamada.")
                                .schema(new IntegerSchema()).example(60)))
                .addApiResponse("500", error(ApiProblem.INTERNAL_ERROR));
    }

    private static ApiResponse success(String description, Map<String, Example> examples) {
        return correlatedResponse(description).content(new Content().addMediaType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                new MediaType().schema(new Schema<>().$ref(SCHEMA_PREFIX + ActivationRequestResponse.class.getSimpleName()))
                        .examples(examples)));
    }

    private static ApiResponse error(ApiProblem problem) {
        return correlatedResponse(problem.create().getTitle()).content(problemContent(problem));
    }

    private static ApiResponse errorWithStatus(ApiProblem problem, int status) {
        ProblemDetail detail = problem.create();
        detail.setStatus(status);
        return correlatedResponse(detail.getTitle()).content(new Content()
                .addMediaType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        new MediaType().schema(new Schema<>().$ref(SCHEMA_PREFIX + PROBLEM_SCHEMA))
                                .addExamples("invalid-request", problemExample(detail))));
    }

    private static Content problemContent(ApiProblem... problems) {
        MediaType media = new MediaType().schema(new Schema<>().$ref(SCHEMA_PREFIX + PROBLEM_SCHEMA));
        for (ApiProblem problem : problems) {
            ProblemDetail detail = problem.create();
            media.addExamples(problem.code(), problemExample(detail));
        }
        return new Content().addMediaType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE, media);
    }

    private static Example problemExample(ProblemDetail detail) {
        Map<String, Object> value = new LinkedHashMap<>();
        Map<String, Object> properties = detail.getProperties();
        if (properties != null) {
            value.putAll(properties);
        }
        value.put("type", detail.getType().toString());
        value.put(FIELD_TITLE, detail.getTitle());
        value.put(FIELD_STATUS, detail.getStatus());
        value.put(FIELD_DETAIL, detail.getDetail());
        value.put("instance", ActivationRequestController.PATH);
        value.put(CorrelationIdFilter.MDC_KEY, EXAMPLE_CORRELATION_ID);
        if ("validation-error".equals(value.get("code"))) {
            value.put("errors", List.of(Map.of(VIOLATION_FIELD, "externalId", VIOLATION_MESSAGE, "é obrigatório")));
        }
        return new Example().summary(detail.getTitle()).value(value);
    }

    private static ApiResponse correlatedResponse(String description) {
        return new ApiResponse().description(description).addHeaderObject(CorrelationIdFilter.HEADER,
                new Header().description("Identificador usado para correlacionar logs e chamadas.")
                        .schema(new StringSchema()).example(EXAMPLE_CORRELATION_ID));
    }
}
