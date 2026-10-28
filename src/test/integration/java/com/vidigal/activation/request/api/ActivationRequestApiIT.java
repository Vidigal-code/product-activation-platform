package com.vidigal.activation.request.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"activation.security.mode=local", "activation.worker.enabled=false"})
@AutoConfigureMockMvc
@Testcontainers
class ActivationRequestApiIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mvc;

    @Test
    void createsPendingRequestWithLocationAndUtcDates() throws Exception {
        mvc.perform(post("/api/v1/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-ID", "create-http-001")
                        .content("""
                                {"externalId":"api-create-001","customerId":"CUST-1","productCode":"INTERNET-500"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/v1/requests/")))
                .andExpect(header().string("X-Correlation-ID", "create-http-001"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.externalId").value("api-create-001"))
                .andExpect(jsonPath("$.customerId").value("CUST-1"))
                .andExpect(jsonPath("$.productCode").value("INTERNET-500"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.attemptCount").value(0))
                .andExpect(jsonPath("$.createdAt", endsWith("Z")))
                .andExpect(jsonPath("$.updatedAt", endsWith("Z")));
    }

    @Test
    void retrievesAnExistingRequestAtItsLocation() throws Exception {
        var creation = mvc.perform(post("/api/v1/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"externalId":"api-lookup-001","customerId":"CUST-2","productCode":"INTERNET-500"}
                                """))
                .andExpect(status().isCreated()).andReturn().getResponse();

        mvc.perform(get(creation.getHeader("Location")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.externalId").value("api-lookup-001"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void returnsTheSameRequestWithOkWhenRetried() throws Exception {
        String body = """
                {"externalId":"api-repeat-001","customerId":"CUST-1","productCode":"INTERNET-500"}
                """;
        var first = mvc.perform(post("/api/v1/requests").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse();

        mvc.perform(post("/api/v1/requests").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(content().json(first.getContentAsString()));
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    void rejectsInvalidFieldsWithCorrelatedProblemDetails(String body) throws Exception {
        mvc.perform(post("/api/v1/requests").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-ID", "invalid-http-001").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("validation-error"))
                .andExpect(jsonPath("$.correlationId").value("invalid-http-001"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[0].field").isNotEmpty())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    static Stream<String> invalidBodies() {
        return Stream.of(
                "{}",
                """
                        {"externalId":" ","customerId":"CUST-1","productCode":"PRODUCT-1"}
                        """,
                """
                        {"externalId":"BAD/ID","customerId":"CUST-1","productCode":"PRODUCT-1"}
                        """,
                """
                        {"externalId":"VALID-1","customerId":"客户","productCode":"PRODUCT-1"}
                        """,
                """
                        {"externalId":"VALID-1","customerId":"CUST-1","productCode":"PRODUCT 1"}
                        """,
                "{\"externalId\":\"" + "E".repeat(101) + "\",\"customerId\":\"C\",\"productCode\":\"P\"}",
                "{\"externalId\":\"E\",\"customerId\":\"" + "C".repeat(101) + "\",\"productCode\":\"P\"}",
                "{\"externalId\":\"E\",\"customerId\":\"C\",\"productCode\":\"" + "P".repeat(81) + "\"}");
    }

    @Test
    void returnsNotFoundProblemForAnUnknownRequest() throws Exception {
        mvc.perform(get("/api/v1/requests/00000000-0000-0000-0000-000000000001")
                        .header("X-Correlation-ID", "missing-http-001"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("request-not-found"))
                .andExpect(jsonPath("$.correlationId").value("missing-http-001"));
    }

    @Test
    void rejectsAnExternalIdReusedWithDifferentContent() throws Exception {
        mvc.perform(post("/api/v1/requests").contentType(MediaType.APPLICATION_JSON).content("""
                        {"externalId":"api-conflict-001","customerId":"CUST-1","productCode":"INTERNET-500"}
                        """))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/requests").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-ID", "conflict-http-001").content("""
                                {"externalId":"api-conflict-001","customerId":"CUST-1","productCode":"INTERNET-1000"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("duplicate-request"))
                .andExpect(jsonPath("$.correlationId").value("conflict-http-001"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "null", "{\"externalId\":\"E\",\"customerId\":\"C\",\"productCode\":\"P\",\"extra\":true}"})
    void rejectsMalformedOrUnknownJsonWithoutParserDetails(String body) throws Exception {
        mvc.perform(post("/api/v1/requests").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid-request"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andExpect(jsonPath("$.detail").value("O formato da requisição é inválido."));
    }

    @Test
    void rejectsMalformedRequestIdentifiersWithoutConversionDetails() throws Exception {
        mvc.perform(get("/api/v1/requests/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid-request"))
                .andExpect(jsonPath("$.detail").value("O formato da requisição é inválido."));
    }

    @Test
    void acceptsIdentifiersAtTheirDocumentedLengthLimits() throws Exception {
        String body = "{\"externalId\":\"%s\",\"customerId\":\"%s\",\"productCode\":\"%s\"}"
                .formatted("E".repeat(100), "C".repeat(100), "P".repeat(80));

        mvc.perform(post("/api/v1/requests").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsJsonPresentedAsPlainTextAndAcceptsTheCorrectMediaType() throws Exception {
        String body = """
                {"externalId":"api-content-type","customerId":"CUST-1","productCode":"PRODUCT-1"}
                """;

        mvc.perform(post("/api/v1/requests").contentType(MediaType.TEXT_PLAIN).content(body))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("invalid-request"));

        mvc.perform(post("/api/v1/requests").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }
}
