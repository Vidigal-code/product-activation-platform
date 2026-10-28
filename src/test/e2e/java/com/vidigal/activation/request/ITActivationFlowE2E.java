package com.vidigal.activation.request;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "activation.security.mode=local",
        "activation.worker.enabled=true",
        "activation.worker.poll-interval=25ms",
        "activation.worker.lease-duration=10s",
        "activation.retry.max-attempts=3",
        "activation.retry.initial-delay=20ms",
        "activation.retry.max-delay=40ms",
        "activation.provider.activation-path=/provider/v1/activate",
        "activation.provider.connect-timeout=1s",
        "activation.provider.read-timeout=1s",
        "activation.provider.idempotency-supported=false"
})
@Import(ITActivationFlowE2E.Database.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ITActivationFlowE2E {

    private static final String API_PATH = "/api/v1/requests";
    private static final String PROVIDER_PATH = "/provider/v1/activate";
    private static final String CORRELATION_ID = "activation-e2e";

    @TestConfiguration(proxyBeanMethods = false)
    static class Database {
        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgres() {
            return new PostgreSQLContainer<>("postgres:17.11-alpine");
        }
    }

    @RegisterExtension
    static WireMockExtension provider = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort()).build();

    @DynamicPropertySource
    static void providerProperties(DynamicPropertyRegistry registry) {
        registry.add("activation.provider.base-url", provider::baseUrl);
    }

    @Autowired
    private TestRestTemplate http;

    @Test
    void createsAndCompletesAnActivationThroughHttpAndTheRealWorker() {
        provider.stubFor(post(PROVIDER_PATH).willReturn(aResponse().withStatus(204)));

        var created = create(input("INTERNET-500"));

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getHeaders().getLocation()).isNotNull();
        var completed = awaitStatus(created.getHeaders().getLocation(), "COMPLETED");
        assertThat(completed.path("attemptCount").asInt()).isEqualTo(1);
        provider.verify(1, postRequestedFor(urlEqualTo(PROVIDER_PATH))
                .withHeader("Idempotency-Key", equalTo(completed.path("id").asText()))
                .withHeader("X-Correlation-ID", equalTo(CORRELATION_ID)));
    }

    @Test
    void preservesBusinessRejectionWithoutSendingTheActivationAgain() {
        provider.stubFor(post(PROVIDER_PATH).willReturn(aResponse().withStatus(400)));

        var created = create(input("BUSINESS-ERROR"));

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var failed = awaitStatus(created.getHeaders().getLocation(), "FAILED");
        assertThat(failed.path("failureCode").asText()).isEqualTo("PROVIDER_HTTP_400");
        assertThat(failed.path("attemptCount").asInt()).isEqualTo(1);
        provider.verify(1, postRequestedFor(urlEqualTo(PROVIDER_PATH)));
    }

    @Test
    void stopsTransientFailuresAfterThreePersistedAttempts() {
        provider.stubFor(post(PROVIDER_PATH).willReturn(aResponse().withStatus(500)));

        var created = create(input("TEMPORARY-ERROR"));

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var failed = awaitStatus(created.getHeaders().getLocation(), "FAILED");
        assertThat(failed.path("failureCode").asText()).isEqualTo("PROVIDER_HTTP_500");
        assertThat(failed.path("attemptCount").asInt()).isEqualTo(3);
        provider.verify(3, postRequestedFor(urlEqualTo(PROVIDER_PATH)));
    }

    @Test
    void marksTimeoutAsUncertainAndDoesNotRetryWithoutProviderIdempotency() {
        provider.stubFor(post(PROVIDER_PATH).willReturn(aResponse().withStatus(204).withFixedDelay(3000)));

        var created = create(input("TIMEOUT"));

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var failed = awaitStatus(created.getHeaders().getLocation(), "FAILED");
        assertThat(failed.path("failureCode").asText()).isEqualTo("PROVIDER_IO_UNCERTAIN");
        assertThat(failed.path("attemptCount").asInt()).isEqualTo(1);
        await().during(Duration.ofMillis(250)).atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                provider.verify(1, postRequestedFor(urlEqualTo(PROVIDER_PATH))));
    }

    @Test
    void replaysIdenticalContentAndRejectsConflictsWithoutAnotherActivation() {
        provider.stubFor(post(PROVIDER_PATH).willReturn(aResponse().withStatus(204)));
        var input = input("INTERNET-500");
        var created = create(input);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var completed = awaitStatus(created.getHeaders().getLocation(), "COMPLETED");

        var repeated = create(input);
        var conflicting = create(Map.of("externalId", input.get("externalId"),
                "customerId", input.get("customerId"), "productCode", "INTERNET-1000"));

        assertThat(repeated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(Objects.requireNonNull(repeated.getBody()).path("id").asText())
                .isEqualTo(completed.path("id").asText());
        assertThat(conflicting.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(Objects.requireNonNull(conflicting.getBody()).path("code").asText()).isEqualTo("duplicate-request");
        assertThat(get(created.getHeaders().getLocation()).path("productCode").asText()).isEqualTo("INTERNET-500");
        provider.verify(1, postRequestedFor(urlEqualTo(PROVIDER_PATH)));
    }

    private ResponseEntity<JsonNode> create(Map<String, String> input) {
        var headers = new HttpHeaders();
        headers.set("X-Correlation-ID", CORRELATION_ID);
        return http.postForEntity(API_PATH, new HttpEntity<>(input, headers), JsonNode.class);
    }

    private JsonNode awaitStatus(URI location, String expectedStatus) {
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(50)).untilAsserted(() ->
                assertThat(get(location).path("status").asText()).isEqualTo(expectedStatus));
        return get(location);
    }

    private JsonNode get(URI location) {
        var response = http.getForEntity(location, JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return Objects.requireNonNull(response.getBody());
    }

    private Map<String, String> input(String productCode) {
        return Map.of("externalId", "E2E-" + UUID.randomUUID(), "customerId", "CUSTOMER-001", "productCode", productCode);
    }
}
