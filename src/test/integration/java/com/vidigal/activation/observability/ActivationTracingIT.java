package com.vidigal.activation.observability;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.vidigal.activation.request.application.ActivationWorker;
import com.vidigal.activation.support.PostgresTestConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "activation.security.mode=local", "activation.worker.enabled=false", "management.tracing.sampling.probability=1.0",
        "management.otlp.tracing.export.enabled=false"})
@AutoConfigureObservability
@Import(PostgresTestConfiguration.class)
class ActivationTracingIT {

    private static final String TRACE_ID = "11111111111111111111111111111111";
    private static final String INCOMING_TRACE_PARENT = "00-" + TRACE_ID + "-2222222222222222-01";
    private static final String PROVIDER_PATH = "/provider/v1/activate";

    @RegisterExtension
    static WireMockExtension provider = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    @DynamicPropertySource
    static void providerProperties(DynamicPropertyRegistry properties) {
        properties.add("activation.provider.base-url", provider::baseUrl);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private ActivationWorker worker;

    @Autowired
    private ActivationTracing tracing;

    @Test
    void preservesTheHttpTraceAcrossPersistenceWorkerAndProvider() throws Exception {
        provider.stubFor(post(PROVIDER_PATH).willReturn(aResponse().withStatus(204)));
        URI endpoint = URI.create("http://localhost:" + port + "/api/v1/requests");
        HttpRequest request = HttpRequest.newBuilder(endpoint).header("Content-Type", "application/json")
                .header("traceparent", INCOMING_TRACE_PARENT)
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"externalId":"tracing-001","customerId":"CUSTOMER-001","productCode":"INTERNET-500"}
                        """)).build();
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
            var created = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertThat(created.statusCode()).isEqualTo(201);

            assertThat(worker.processNext()).isTrue();

            var outgoing = provider.findAll(postRequestedFor(urlEqualTo(PROVIDER_PATH)));
            assertThat(outgoing).hasSize(1);
            assertThat(outgoing.getFirst().getHeader("traceparent")).startsWith("00-" + TRACE_ID + "-")
                    .endsWith("-01").isNotEqualTo(INCOMING_TRACE_PARENT);
            URI location = endpoint.resolve(created.headers().firstValue("Location").orElseThrow());
            var stored = client.send(HttpRequest.newBuilder(location).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(stored.statusCode()).isEqualTo(200);
            assertThat(stored.body()).contains("COMPLETED");
        }
    }

    @Test
    void createsATraceForLegacyWorkAndClosesItsScopeWhenWorkFails() {
        var failure = new IllegalStateException("private-failure-detail");
        var processingContext = new AtomicReference<String>();
        assertThat(tracing.currentTraceParent()).isNull();

        assertThatThrownBy(() -> tracing.runWithProcessingSpan(null, () -> {
            processingContext.set(tracing.currentTraceParent());
            throw failure;
        })).isSameAs(failure);

        assertThat(processingContext.get()).matches("00-[a-f0-9]{32}-[a-f0-9]{16}-01");
        assertThat(tracing.currentTraceParent()).isNull();
    }
}
