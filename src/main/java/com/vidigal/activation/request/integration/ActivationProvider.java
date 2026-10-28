package com.vidigal.activation.request.integration;

import jakarta.annotation.PreDestroy;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
public class ActivationProvider implements AutoCloseable {

    private static final Instant POSTGRESQL_RETRY_HORIZON = Instant.parse("+294276-01-01T00:00:00Z");
    private final HttpClient httpClient;
    private final RestClient restClient;
    private final Clock clock;
    private final String activationPath;
    private final ProviderCircuitBreaker circuit;

    public ActivationProvider(ProviderProperties properties, Clock clock, RestClient.Builder clientBuilder,
            ProviderCircuitBreaker circuit) {
        this.clock = clock;
        this.activationPath = properties.activationPath();
        this.circuit = circuit;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = clientBuilder
                .baseUrl(properties.baseUrl().toString())
                .requestFactory(requestFactory)
                .defaultHeaders(headers -> {
                    if (properties.token() != null && !properties.token().isBlank()) {
                        headers.setBearerAuth(properties.token());
                    }
                })
                .build();
    }

    public ActivationOutcome activate(ActivationPayload payload) {
        return circuit.execute(() -> callProvider(payload));
    }

    private ActivationOutcome callProvider(ActivationPayload payload) {
        try {
            return restClient.post()
                    .uri(activationPath)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", payload.requestId().toString())
                    .header("X-Correlation-ID", payload.correlationId())
                    .body(new ProviderRequest(payload.externalId(), payload.customerId(), payload.productCode()))
                    .exchange((request, response) -> classify(response.getStatusCode(), response.getHeaders()));
        } catch (ResourceAccessException exception) {
            return new ActivationOutcome(ActivationOutcome.Kind.UNCERTAIN, "PROVIDER_IO_UNCERTAIN", Duration.ZERO);
        }
    }

    private ActivationOutcome classify(HttpStatusCode status, HttpHeaders headers) {
        if (status.is2xxSuccessful()) {
            return new ActivationOutcome(ActivationOutcome.Kind.SUCCESS, "PROVIDER_SUCCESS", Duration.ZERO);
        }
        ActivationOutcome.Kind kind = status.is5xxServerError() || status.value() == HttpStatus.TOO_MANY_REQUESTS.value()
                ? ActivationOutcome.Kind.TRANSIENT_FAILURE : ActivationOutcome.Kind.PERMANENT_FAILURE;
        Optional<Duration> retryAfter = status.value() == HttpStatus.TOO_MANY_REQUESTS.value()
                || status.value() == HttpStatus.SERVICE_UNAVAILABLE.value()
                ? parseRetryAfter(headers.getFirst(HttpHeaders.RETRY_AFTER)) : Optional.of(Duration.ZERO);
        return retryAfter.map(delay -> new ActivationOutcome(kind, "PROVIDER_HTTP_" + status.value(), delay))
                .orElseGet(() -> new ActivationOutcome(ActivationOutcome.Kind.PERMANENT_FAILURE,
                        "PROVIDER_RETRY_AFTER_UNSUPPORTED", Duration.ZERO));
    }

    private Optional<Duration> parseRetryAfter(String value) {
        if (value == null || value.isBlank()) {
            return Optional.of(Duration.ZERO);
        }
        try {
            Duration duration = value.matches("\\d+") ? Duration.ofSeconds(Long.parseLong(value))
                    : Duration.between(clock.instant(),
                            ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
            if (duration.compareTo(Duration.between(clock.instant(), POSTGRESQL_RETRY_HORIZON)) > 0) {
                return Optional.empty();
            }
            return Optional.of(duration.isNegative() ? Duration.ZERO : duration);
        } catch (NumberFormatException exception) {
            return Optional.empty();
        } catch (DateTimeParseException exception) {
            return Optional.of(Duration.ZERO);
        }
    }

    @Override
    @PreDestroy
    public void close() {
        httpClient.close();
    }

    private record ProviderRequest(String externalId, String customerId, String productCode) {
    }
}
