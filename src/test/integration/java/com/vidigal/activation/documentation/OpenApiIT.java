package com.vidigal.activation.documentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidigal.activation.support.PostgresTestConfiguration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"activation.security.mode=local", "activation.worker.enabled=false",
        "springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true"})
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class OpenApiIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Test
    void describesActivationOperationsAndBearerAuthentication() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Product Activation Platform"))
                .andExpect(jsonPath("$.paths['/api/v1/requests'].post.summary").value("Registrar solicitação de ativação"))
                .andExpect(jsonPath("$.paths['/api/v1/requests/{id}'].get.summary").value("Consultar solicitação e processamento"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"));
    }

    @Test
    void providesRunnableExamplesForCreationReplayConflictAndLookup() throws Exception {
        JsonNode examples = specification().at("/paths/~1api~1v1~1requests/post/requestBody/content/application~1json/examples");
        JsonNode creation = examples.path("sucesso").path("value");
        JsonNode replay = examples.path("repeticao").path("value");
        JsonNode conflict = examples.path("conflito").path("value");
        assertThat(creation.path("externalId").asText()).isEqualTo("swagger-success-001");
        assertThat(replay).isEqualTo(creation);
        assertThat(conflict.path("externalId").asText()).isEqualTo("swagger-success-001");

        var created = mvc.perform(post("/api/v1/requests").contentType(MediaType.APPLICATION_JSON)
                        .content(creation.toString())).andExpect(status().isCreated()).andReturn().getResponse();
        mvc.perform(get(created.getHeader("Location"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
        mvc.perform(post("/api/v1/requests").contentType(MediaType.APPLICATION_JSON).content(replay.toString()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/requests").contentType(MediaType.APPLICATION_JSON).content(conflict.toString()))
                .andExpect(status().isConflict());
    }

    @ParameterizedTest
    @CsvSource({"erroNegocio,BUSINESS-ERROR", "falhaTemporaria,TEMPORARY-ERROR", "timeout,TIMEOUT",
            "limiteProvedor,RATE-LIMITED", "indisponivel,UNAVAILABLE"})
    void providesReadyToSubmitExamplesForEverySimulatedProviderOutcome(String name, String productCode) throws Exception {
        JsonNode example = specification().at("/paths/~1api~1v1~1requests/post/requestBody/content/application~1json/examples")
                .path(name);
        assertThat(example.path("description").asText()).contains("201", "GET");
        assertThat(example.path("value").path("productCode").asText()).isEqualTo(productCode);

        mvc.perform(post("/api/v1/requests").contentType(MediaType.APPLICATION_JSON)
                        .content(example.path("value").toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productCode").value(productCode));
    }

    @Test
    void documentsSuccessfulResponsesAndCorrelatedProblemDetails() throws Exception {
        JsonNode specification = specification();
        JsonNode creation = specification.at("/paths/~1api~1v1~1requests/post/responses");
        JsonNode lookup = specification.at("/paths/~1api~1v1~1requests~1{id}/get/responses");
        for (String code : new String[]{"201", "200", "400", "401", "403", "409", "415", "500"}) {
            assertThat(creation.path(code).path("description").asText()).as("POST %s", code).isNotBlank();
        }
        for (String code : new String[]{"200", "400", "401", "403", "404", "500"}) {
            assertThat(lookup.path(code).path("description").asText()).as("GET %s", code).isNotBlank();
        }
        assertThat(creation.at("/201/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/ActivationRequestResponse");
        assertThat(creation.at("/201/headers/Location/schema/type").asText()).isEqualTo("string");
        assertThat(creation.at("/400/content/application~1problem+json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/ApiProblem");
        assertThat(creation.at("/400/content/application~1problem+json/examples/validation-error/value/status").asInt())
                .isEqualTo(400);
        assertThat(lookup.at("/404/content/application~1problem+json/examples/request-not-found/value/code").asText())
                .isEqualTo("request-not-found");
        assertThat(specification.at("/components/schemas/ApiProblem/properties/correlationId/type").asText())
                .isEqualTo("string");
        assertThat(lookup.at("/200/content/application~1json/examples/concluida/value/status").asText())
                .isEqualTo("COMPLETED");
        assertThat(lookup.at("/200/content/application~1json/examples/timeout/value/failureCode").asText())
                .isEqualTo("PROVIDER_IO_UNCERTAIN");
    }

    @ParameterizedTest
    @CsvSource({"validacao,validation-error", "campoDesconhecido,invalid-request"})
    void providesExecutableInvalidRequestExamples(String name, String problemCode) throws Exception {
        JsonNode body = specification().at("/paths/~1api~1v1~1requests/post/requestBody/content/application~1json/examples")
                .path(name).path("value");
        assertThat(body.isObject()).isTrue();

        mvc.perform(post("/api/v1/requests").contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(problemCode));
    }

    @Test
    void servesTheSwaggerUiWithItsOwnSpecificationAndTryItOutEnabled() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
        mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/v3/api-docs"))
                .andExpect(jsonPath("$.tryItOutEnabled").value(true));
    }

    @Test
    void documentsPaginatedSearchAndRequestGuardrails() throws Exception {
        JsonNode specification = specification();
        JsonNode listing = specification.at("/paths/~1api~1v1~1requests/get");
        assertThat(listing.path("summary").asText()).isEqualTo("Listar solicitações do consumidor");
        assertThat(listing.at("/responses/200/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/ActivationRequestPageResponse");
        Map<String, JsonNode> parameters = new HashMap<>();
        listing.path("parameters").forEach(parameter -> parameters.put(parameter.path("name").asText(), parameter));
        assertThat(parameters).containsKeys("status", "customerId", "page", "size");
        assertThat(parameters.get("page").at("/schema/default").asInt()).isZero();
        assertThat(parameters.get("size").at("/schema/default").asInt()).isEqualTo(20);
        assertThat(parameters.get("size").at("/schema/maximum").asInt()).isEqualTo(100);
        assertThat(listing.at("/responses/429/content/application~1problem+json/examples/rate-limited/value/status").asInt())
                .isEqualTo(429);
        assertThat(listing.at("/responses/429/headers/Retry-After/schema/type").asText()).isEqualTo("integer");
        assertThat(specification.at("/paths/~1api~1v1~1requests/post/responses/413/content/application~1problem+json/examples/payload-too-large/value/status").asInt())
                .isEqualTo(413);
    }

    private JsonNode specification() throws Exception {
        String json = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        return mapper.readTree(json);
    }
}
