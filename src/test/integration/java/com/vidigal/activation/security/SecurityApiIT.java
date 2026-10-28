package com.vidigal.activation.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.BadJwtException;
import java.time.Instant;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.Import;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "activation.security.mode=jwt", "activation.worker.enabled=false",
        "activation.security.issuer-uri=http://localhost:19090/issuer",
        "activation.security.audience=product-activation-platform",
        "activation.pagination.default-size=2", "activation.pagination.max-size=5",
        "activation.pagination.max-page=2"
})
@AutoConfigureMockMvc
@AutoConfigureObservability(tracing = false)
@Import(com.vidigal.activation.support.PostgresTestConfiguration.class)
class SecurityApiIT {


    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JwtDecoder decoder;

    @Test
    void requiresBearerAuthenticationAndReturnsCorrelatedProblem() throws Exception {
        mvc.perform(get("/api/v1/requests/00000000-0000-0000-0000-000000000001")
                        .header("X-Correlation-ID", "unauthorized-001"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("unauthorized"))
                .andExpect(jsonPath("$.correlationId").value("unauthorized-001"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"JSESSIONID", "access_token"})
    void rejectsAuthenticationCookiesWithoutAnExplicitBearerHeader(String cookieName) throws Exception {
        when(decoder.decode("write-token")).thenReturn(tokenWithScope("requests:write"));
        String body = """
                {"externalId":"security-cookie-%s","customerId":"CUST-1","productCode":"PRODUCT-1"}
                """.formatted(cookieName);

        mvc.perform(post("/api/v1/requests").cookie(new Cookie(cookieName, "write-token"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.code").value("unauthorized"));

        mvc.perform(post("/api/v1/requests").header("Authorization", "Bearer write-token")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(cookie().doesNotExist("JSESSIONID"));
    }

    @Test
    void rejectsBasicAuthenticationForCreation() throws Exception {
        mvc.perform(post("/api/v1/requests").with(httpBasic("integration-client", "test-credential"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"externalId":"security-basic","customerId":"CUST-1","productCode":"PRODUCT-1"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.code").value("unauthorized"))
                .andExpect(cookie().doesNotExist("JSESSIONID"));
    }

    @Test
    void requiresWriteScopeForCreation() throws Exception {
        when(decoder.decode("read-token")).thenReturn(tokenWithScope("requests:read"));

        mvc.perform(post("/api/v1/requests").header("Authorization", "Bearer read-token")
                        .header("X-Correlation-ID", "forbidden-001")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"externalId":"security-read-only","customerId":"CUST-1","productCode":"PRODUCT-1"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("forbidden"))
                .andExpect(jsonPath("$.correlationId").value("forbidden-001"));
    }

    @Test
    void allowsCreationForWriteScopeButRejectsReadingWithThatScope() throws Exception {
        when(decoder.decode("write-token")).thenReturn(tokenWithScope("requests:write"));
        var creation = mvc.perform(post("/api/v1/requests").header("Authorization", "Bearer write-token")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"externalId":"security-write-only","customerId":"CUST-1","productCode":"PRODUCT-1"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(cookie().doesNotExist("JSESSIONID")).andReturn().getResponse();

        mvc.perform(get(creation.getHeader("Location")).header("Authorization", "Bearer write-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsReadScopeToQueryRequests() throws Exception {
        when(decoder.decode("write-token")).thenReturn(tokenWithScope("requests:write"));
        when(decoder.decode("read-token")).thenReturn(tokenWithScope("requests:read"));
        var creation = mvc.perform(post("/api/v1/requests").header("Authorization", "Bearer write-token")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"externalId":"security-read","customerId":"CUST-1","productCode":"PRODUCT-1"}
                                """))
                .andExpect(status().isCreated()).andReturn().getResponse();

        mvc.perform(get(creation.getHeader("Location")).header("Authorization", "Bearer read-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.externalId").value("security-read"));
    }

    @Test
    void protectsMetricsWithASeparateMonitoringScope() throws Exception {
        when(decoder.decode("read-token")).thenReturn(tokenWithScope("requests:read"));
        when(decoder.decode("monitor-token")).thenReturn(tokenWithScope("monitoring:read"));

        mvc.perform(get("/actuator/prometheus").header("Authorization", "Bearer read-token"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/actuator/prometheus").header("Authorization", "Bearer monitor-token"))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsInvalidBearerTokensWithoutDecoderDetails() throws Exception {
        when(decoder.decode("invalid-token")).thenThrow(new BadJwtException("Private signing key details"));

        mvc.perform(get("/api/v1/requests/00000000-0000-0000-0000-000000000001")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"))
                .andExpect(jsonPath("$.detail").value("Informe um token Bearer válido."));
    }

    @Test
    void exposesHealthProbesWithoutAuthenticationOrInternalDetails() throws Exception {
        mvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
        mvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void doesNotExposeSessionAuthenticationEndpoints() throws Exception {
        when(decoder.decode("read-token")).thenReturn(tokenWithScope("requests:read"));

        mvc.perform(get("/logout").header("Authorization", "Bearer read-token"))
                .andExpect(status().isForbidden());
    }

    private static Jwt tokenWithScope(String scope) {
        return tokenWithScope(scope, "integration-client");
    }

    @Test
    void hidesRequestsOwnedByAnotherConsumer() throws Exception {
        when(decoder.decode("owner-token")).thenReturn(tokenWithScope("requests:read requests:write", "owner-client"));
        when(decoder.decode("other-token")).thenReturn(tokenWithScope("requests:read", "other-client"));
        var creation = mvc.perform(post("/api/v1/requests").header("Authorization", "Bearer owner-token")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"externalId":"owned-request","customerId":"CUST-1","productCode":"PRODUCT-1"}
                                """))
                .andExpect(status().isCreated()).andReturn().getResponse();

        mvc.perform(get(creation.getHeader("Location")).header("Authorization", "Bearer owner-token"))
                .andExpect(status().isOk());
        mvc.perform(get(creation.getHeader("Location")).header("Authorization", "Bearer other-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("request-not-found"))
                .andExpect(jsonPath("$.customerId").doesNotExist());
    }

    private static Jwt tokenWithScope(String scope, String consumerId) {
        return Jwt.withTokenValue("decoded-token").header("alg", "RS256")
                .subject(consumerId).claim("scope", scope)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
    }

    @Test
    void rejectsAnotherConsumersReplayWithoutDisclosingTheExistingRequest() throws Exception {
        when(decoder.decode("owner-token")).thenReturn(tokenWithScope("requests:write", "owner-client"));
        when(decoder.decode("other-token")).thenReturn(tokenWithScope("requests:write", "other-client"));
        String body = """
                {"externalId":"owned-replay","customerId":"CUST-1","productCode":"PRODUCT-1"}
                """;
        mvc.perform(post("/api/v1/requests").header("Authorization", "Bearer owner-token")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/requests").header("Authorization", "Bearer other-token")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("duplicate-request"))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.customerId").doesNotExist());
    }

    @Test
    void listsOnlyTheAuthenticatedConsumersRequestsWithFiltersAndStablePages() throws Exception {
        when(decoder.decode("list-owner")).thenReturn(tokenWithScope("requests:read requests:write", "list-client"));
        when(decoder.decode("list-other")).thenReturn(tokenWithScope("requests:read requests:write", "other-list-client"));
        createFor("list-owner", "list-first", "MATCH");
        createFor("list-owner", "list-second", "MATCH");
        createFor("list-owner", "list-third", "MATCH");
        createFor("list-owner", "list-unmatched", "OTHER");
        createFor("list-other", "list-hidden", "MATCH");

        mvc.perform(get("/api/v1/requests").header("Authorization", "Bearer list-owner")
                        .param("customerId", "MATCH").param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].externalId").value("list-first"))
                .andExpect(jsonPath("$.items[1].externalId").value("list-second"))
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.hasNext").value(true));
        mvc.perform(get("/api/v1/requests").header("Authorization", "Bearer list-owner")
                        .param("customerId", "MATCH").param("status", "PENDING").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].externalId").value("list-third"))
                .andExpect(jsonPath("$.hasNext").value(false));
        mvc.perform(get("/api/v1/requests").header("Authorization", "Bearer list-owner")
                        .param("status", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    private void createFor(String token, String externalId, String customerId) throws Exception {
        mvc.perform(post("/api/v1/requests").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"externalId":"%s","customerId":"%s","productCode":"PRODUCT-1"}
                                """.formatted(externalId, customerId)))
                .andExpect(status().isCreated());
    }

    @ParameterizedTest
    @CsvSource({"size,6", "page,3", "size,0", "page,-1", "status,INVALID", "customerId,invalid value"})
    void rejectsInvalidOrExcessivePaginationParameters(String parameter, String value) throws Exception {
        when(decoder.decode("query-token")).thenReturn(tokenWithScope("requests:read"));

        mvc.perform(get("/api/v1/requests").header("Authorization", "Bearer query-token").param(parameter, value))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }
}
