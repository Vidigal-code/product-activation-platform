package com.vidigal.activation.request.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ActivationProviderIT {

    private static final UUID REQUEST_ID = UUID.fromString("ff426717-1e72-4a60-b953-4acf3a683047");
    private static final String ACTIVATION_PATH = "/provider/v1/activate";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private static final ActivationPayload PAYLOAD = new ActivationPayload(
            REQUEST_ID, "external-1", "customer-1", "product-1", "correlation-1");

    @RegisterExtension
    static WireMockExtension provider = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private final List<ActivationProvider> clients = new ArrayList<>();

    @AfterEach
    void closeClients() {
        clients.forEach(ActivationProvider::close);
    }

    @Test
    void activatesWithStableIdempotencyKeyAndCorrelation() {
        provider.stubFor(post(ACTIVATION_PATH).willReturn(aResponse().withStatus(204)));

        ActivationOutcome result = client(Duration.ofSeconds(2)).activate(PAYLOAD);

        assertThat(result).isEqualTo(new ActivationOutcome(
                ActivationOutcome.Kind.SUCCESS, "PROVIDER_SUCCESS", Duration.ZERO));
        provider.verify(postRequestedFor(urlEqualTo(ACTIVATION_PATH))
                .withHeader("Idempotency-Key", equalTo(REQUEST_ID.toString()))
                .withHeader("X-Correlation-ID", equalTo("correlation-1"))
                .withHeader("Authorization", absent())
                .withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(equalToJson("""
                        {"externalId":"external-1","customerId":"customer-1","productCode":"product-1"}
                        """)));
    }

    @ParameterizedTest
    @CsvSource({"400,PERMANENT_FAILURE", "401,PERMANENT_FAILURE", "429,TRANSIENT_FAILURE",
            "500,TRANSIENT_FAILURE", "503,TRANSIENT_FAILURE", "302,PERMANENT_FAILURE"})
    void classifiesHttpFailuresWithoutExposingResponseBody(int status, ActivationOutcome.Kind expectedKind) {
        provider.stubFor(post(ACTIVATION_PATH).willReturn(aResponse().withStatus(status)
                .withHeader("Location", "http://127.0.0.1:1/redirect-target")
                .withBody("provider-internal-detail")));

        ActivationOutcome result = client(Duration.ofSeconds(2)).activate(PAYLOAD);

        assertThat(result).isEqualTo(new ActivationOutcome(
                expectedKind, "PROVIDER_HTTP_" + status, Duration.ZERO));
    }

    @Test
    void reportsAnUncertainResultWhenResponseExceedsConfiguredTimeout() {
        provider.stubFor(post(ACTIVATION_PATH).willReturn(aResponse().withStatus(200).withFixedDelay(500)));

        ActivationOutcome result = client(Duration.ofMillis(75)).activate(PAYLOAD);

        assertThat(result).isEqualTo(new ActivationOutcome(
                ActivationOutcome.Kind.UNCERTAIN, "PROVIDER_IO_UNCERTAIN", Duration.ZERO));
    }

    @ParameterizedTest
    @CsvSource({"429,17,17", "503,'Thu, 1 Jan 2026 00:00:05 GMT',5", "429,-1,0",
            "429,invalid,0", "503,999999999999999999999999999999999999,0",
            "503,9223372036854775807,0", "503,'Wed, 31 Dec 2025 23:59:59 GMT',0", "500,17,0"})
    void interpretsRetryAfterWithoutTrustingInvalidValues(int status, String retryAfter, long seconds) {
        provider.stubFor(post(ACTIVATION_PATH).willReturn(aResponse().withStatus(status)
                .withHeader("Retry-After", retryAfter)));

        ActivationOutcome result = client(Duration.ofSeconds(2)).activate(PAYLOAD);

        assertThat(result.retryAfter()).isEqualTo(Duration.ofSeconds(seconds));
    }

    @ParameterizedTest
    @ValueSource(strings = {"9000000000000000", "9223372036854775807", "999999999999999999999999999999999999"})
    void rejectsRetryAfterThatCannotBePersistedWithoutRetryingEarly(String retryAfter) {
        provider.stubFor(post(ACTIVATION_PATH).willReturn(aResponse().withStatus(503)
                .withHeader("Retry-After", retryAfter)));

        ActivationOutcome result = client(Duration.ofSeconds(2)).activate(PAYLOAD);

        assertThat(result).isEqualTo(new ActivationOutcome(ActivationOutcome.Kind.PERMANENT_FAILURE,
                "PROVIDER_RETRY_AFTER_UNSUPPORTED", Duration.ZERO));
    }

    @Test
    void authenticatesWithTheConfiguredProviderCredential() {
        provider.stubFor(post(ACTIVATION_PATH).willReturn(aResponse().withStatus(200)));
        ProviderProperties properties = new ProviderProperties(URI.create(provider.baseUrl()),
                Duration.ofSeconds(1), Duration.ofSeconds(2), true, "test-token", ACTIVATION_PATH);

        ActivationOutcome result = client(properties).activate(PAYLOAD);

        assertThat(result.kind()).isEqualTo(ActivationOutcome.Kind.SUCCESS);
        provider.verify(postRequestedFor(urlEqualTo(ACTIVATION_PATH))
                .withHeader("Authorization", equalTo("Bearer test-token")));
    }

    @Test
    void usesTheConfiguredActivationPath() {
        String activationPath = "/custom/v2/activate";
        provider.stubFor(post(activationPath).willReturn(aResponse().withStatus(204)));
        ProviderProperties properties = new ProviderProperties(URI.create(provider.baseUrl()),
                Duration.ofSeconds(1), Duration.ofSeconds(2), true, "", activationPath);

        ActivationOutcome result = client(properties).activate(PAYLOAD);

        assertThat(result.kind()).isEqualTo(ActivationOutcome.Kind.SUCCESS);
        provider.verify(postRequestedFor(urlEqualTo(activationPath)));
    }

    private ActivationProvider client(Duration readTimeout) {
        ProviderProperties properties = new ProviderProperties(URI.create(provider.baseUrl()),
                Duration.ofSeconds(1), readTimeout, true, "", ACTIVATION_PATH);
        return client(properties);
    }

    private ActivationProvider client(ProviderProperties properties) {
        ActivationProvider client = new ActivationProvider(properties, CLOCK);
        clients.add(client);
        return client;
    }
}
