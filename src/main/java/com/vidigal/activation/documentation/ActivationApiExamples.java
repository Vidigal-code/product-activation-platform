package com.vidigal.activation.documentation;

import com.vidigal.activation.request.domain.ActivationStatus;
import io.swagger.v3.oas.models.examples.Example;
import java.util.LinkedHashMap;
import java.util.Map;

final class ActivationApiExamples {

    private static final String SAMPLE_ID = "00000000-0000-0000-0000-000000000001";
    private static final String SUCCESS_EXTERNAL_ID = "swagger-success-001";
    private static final String SUCCESS_PRODUCT = "INTERNET-500";

    private ActivationApiExamples() {
    }

    static Map<String, Example> requests() {
        Map<String, Example> examples = new LinkedHashMap<>();
        Map<String, String> successfulRequest = request(SUCCESS_EXTERNAL_ID, SUCCESS_PRODUCT);
        examples.put("sucesso", new Example().summary("1. Criar ativação com sucesso")
                .description("Execute primeiro: retorna 201. Copie o id da resposta e consulte GET até COMPLETED.")
                .value(successfulRequest));
        examples.put("repeticao", new Example().summary("2. Repetir o mesmo conteúdo: 200")
                .description("Execute após sucesso. Retorna a mesma solicitação, com seu estado atual, sem novo registro.")
                .value(successfulRequest));
        examples.put("conflito", new Example().summary("3. Reutilizar externalId com conteúdo diferente: 409")
                .description("Execute após sucesso. O produto diferente torna o conteúdo conflitante.")
                .value(request(SUCCESS_EXTERNAL_ID, "INTERNET-1000")));
        examples.put("erroNegocio", new Example().summary("Provedor 400: falha definitiva")
                .description("POST retorna 201. No Compose, GET termina FAILED com PROVIDER_HTTP_400, sem repetição.")
                .value(request("swagger-business-001", "BUSINESS-ERROR")));
        examples.put("falhaTemporaria", new Example().summary("Provedor 500: tentativas limitadas")
                .description("POST retorna 201. No Compose, GET termina FAILED com PROVIDER_HTTP_500 após três tentativas.")
                .value(request("swagger-transient-001", "TEMPORARY-ERROR")));
        examples.put("timeout", new Example().summary("Timeout: resultado externo incerto")
                .description("POST retorna 201. No Compose, GET termina FAILED com PROVIDER_IO_UNCERTAIN após uma tentativa; reconciliar antes de repetir.")
                .value(request("swagger-timeout-001", "TIMEOUT")));
        examples.put("limiteProvedor", new Example().summary("Provedor 429: respeitar Retry-After")
                .description("POST retorna 201. No Compose, GET termina FAILED com PROVIDER_HTTP_429 após três tentativas, respeitando Retry-After.")
                .value(request("swagger-rate-limit-001", "RATE-LIMITED")));
        examples.put("indisponivel", new Example().summary("Provedor 503: indisponibilidade temporária")
                .description("POST retorna 201. No Compose, GET termina FAILED com PROVIDER_HTTP_503 após três tentativas, respeitando Retry-After.")
                .value(request("swagger-unavailable-001", "UNAVAILABLE")));
        examples.put("validacao", new Example().summary("Validação de entrada: 400")
                .description("externalId vazio viola um campo obrigatório; a resposta informa o campo em errors.")
                .value(request("", SUCCESS_PRODUCT)));
        Map<String, Object> unknownField = new LinkedHashMap<>(request("swagger-unknown-001", SUCCESS_PRODUCT));
        unknownField.put("extra", true);
        examples.put("campoDesconhecido", new Example().summary("Propriedade desconhecida no JSON: 400")
                .description("O campo extra não pertence ao contrato e provoca invalid-request.")
                .value(unknownField));
        return examples;
    }

    static Map<String, Example> responses() {
        Map<String, Example> examples = new LinkedHashMap<>();
        examples.put("pendente", new Example().summary("PENDING: aguardando reserva").value(response(ActivationStatus.PENDING, 0, null)));
        examples.put("processando", new Example().summary("PROCESSING: chamada em andamento").value(response(ActivationStatus.PROCESSING, 1, null)));
        examples.put("concluida", new Example().summary("COMPLETED: ativação confirmada").value(response(ActivationStatus.COMPLETED, 1, null)));
        examples.put("erroNegocio", new Example().summary("FAILED: provedor recusou a ativação").value(response(ActivationStatus.FAILED, 1, "PROVIDER_HTTP_400")));
        examples.put("falhaTemporaria", new Example().summary("FAILED: tentativas esgotadas").value(response(ActivationStatus.FAILED, 3, "PROVIDER_HTTP_500")));
        examples.put("timeout", new Example().summary("FAILED: resultado incerto, reconciliar").value(response(ActivationStatus.FAILED, 1, "PROVIDER_IO_UNCERTAIN")));
        examples.put("limiteProvedor", new Example().summary("FAILED: limite persistiu após tentativas").value(response(ActivationStatus.FAILED, 3, "PROVIDER_HTTP_429")));
        examples.put("indisponivel", new Example().summary("FAILED: indisponibilidade persistiu").value(response(ActivationStatus.FAILED, 3, "PROVIDER_HTTP_503")));
        return examples;
    }

    private static Map<String, Object> response(ActivationStatus status, int attempts, String failureCode) {
        Map<String, Object> response = new LinkedHashMap<>(request(SUCCESS_EXTERNAL_ID, SUCCESS_PRODUCT));
        response.put("id", SAMPLE_ID);
        response.put("status", status);
        response.put("createdAt", "2026-01-01T12:00:00Z");
        response.put("updatedAt", "2026-01-01T12:00:01Z");
        response.put("attemptCount", attempts);
        response.put("failureCode", failureCode);
        return response;
    }

    private static Map<String, String> request(String externalId, String productCode) {
        return Map.of("externalId", externalId, "customerId", "CUSTOMER-001", "productCode", productCode);
    }
}
