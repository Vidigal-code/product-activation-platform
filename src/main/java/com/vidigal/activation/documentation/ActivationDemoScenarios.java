package com.vidigal.activation.documentation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.vidigal.activation.request.domain.ActivationStatus;
import io.swagger.v3.oas.models.examples.Example;
import java.util.LinkedHashMap;
import java.util.Map;

final class ActivationDemoScenarios {

    private ActivationDemoScenarios() {
    }

    static Map<String, Scenario> scenarios() {
        Map<String, Example> requests = ActivationApiExamples.requests();
        Map<String, Scenario> scenarios = new LinkedHashMap<>();
        requests.forEach((id, example) -> scenarios.put(id, requestScenario(id, example)));
        Object fixture = requests.get("sucesso").getValue();
        scenarios.put("lookup", new Scenario("Consultar uma solicitação criada automaticamente", Kind.LOOKUP,
                fixture, 200, null, null));
        scenarios.put("missing", new Scenario("Consultar solicitação inexistente: 404", Kind.MISSING,
                null, 404, null, null));
        scenarios.put("invalidId", new Scenario("Consultar identificador inválido: 400", Kind.INVALID_ID,
                null, 400, null, null));
        scenarios.put("list", new Scenario("Listar e filtrar uma solicitação criada automaticamente", Kind.LIST,
                fixture, 200, null, null));
        scenarios.put("oversized", new Scenario("Enviar corpo acima do limite: 413", Kind.OVERSIZED,
                fixture, 413, null, null));
        scenarios.put("rateLimit", new Scenario("Atingir o limite de chamadas: 429", Kind.RATE_LIMIT,
                null, 429, null, null));
        return scenarios;
    }

    private static Scenario requestScenario(String id, Example example) {
        Kind kind = switch (id) {
            case "repeticao" -> Kind.REPLAY;
            case "conflito" -> Kind.CONFLICT;
            case "validacao", "campoDesconhecido" -> Kind.INVALID;
            default -> Kind.CREATE;
        };
        int expectedHttp = switch (kind) {
            case REPLAY -> 200;
            case CONFLICT -> 409;
            case INVALID -> 400;
            default -> 201;
        };
        ActivationStatus expectedStatus = null;
        if (kind == Kind.CREATE) {
            expectedStatus = "sucesso".equals(id) ? ActivationStatus.COMPLETED : ActivationStatus.FAILED;
        }
        String failureCode = switch (id) {
            case "erroNegocio" -> "PROVIDER_HTTP_400";
            case "falhaTemporaria" -> "PROVIDER_HTTP_500";
            case "timeout" -> "PROVIDER_IO_UNCERTAIN";
            case "limiteProvedor" -> "PROVIDER_HTTP_429";
            case "indisponivel" -> "PROVIDER_HTTP_503";
            default -> null;
        };
        return new Scenario(example.getSummary(), kind, example.getValue(), expectedHttp, expectedStatus, failureCode);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Scenario(String label, Kind kind, Object body, int expectedHttp, ActivationStatus expectedStatus,
            String expectedFailureCode) {
    }

    enum Kind {
        @JsonProperty("create") CREATE,
        @JsonProperty("replay") REPLAY,
        @JsonProperty("conflict") CONFLICT,
        @JsonProperty("invalid") INVALID,
        @JsonProperty("lookup") LOOKUP,
        @JsonProperty("missing") MISSING,
        @JsonProperty("invalidId") INVALID_ID,
        @JsonProperty("list") LIST,
        @JsonProperty("oversized") OVERSIZED,
        @JsonProperty("rateLimit") RATE_LIMIT
    }
}
