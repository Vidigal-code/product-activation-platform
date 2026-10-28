package com.vidigal.activation.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.BadJwtException;
import java.time.Instant;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
        "activation.security.audience=product-activation-platform"
})
@AutoConfigureMockMvc
@AutoConfigureObservability(tracing = false)
@Testcontainers
class SecurityApiIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>("postgres:17-alpine");

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
        return Jwt.withTokenValue("decoded-token").header("alg", "RS256")
                .subject("integration-client").claim("scope", scope)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
    }
}
