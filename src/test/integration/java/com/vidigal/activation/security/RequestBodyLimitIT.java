package com.vidigal.activation.security;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"activation.security.mode=local", "activation.worker.enabled=false", "activation.http.max-body-bytes=16384"})
@Import(com.vidigal.activation.support.PostgresTestConfiguration.class)
class RequestBodyLimitIT {

    @LocalServerPort
    private int port;

    @ParameterizedTest
    @CsvSource({"16384,201", "16385,413"})
    void enforcesTheConfiguredBoundaryWithContentLength(int bytes, int expectedStatus) throws Exception {
        String document = "{\"externalId\":\"body-" + bytes + "\",\"customerId\":\"C1\",\"productCode\":\"P1\"}";
        String body = document + " ".repeat(bytes - document.length());
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/requests"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        try (var client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()) {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(expectedStatus);
        }
    }

    @Test
    void rejectsOversizedWhitespaceAfterAValidJsonDocument() throws Exception {
        String body = "{\"externalId\":\"body-tail\",\"customerId\":\"C1\",\"productCode\":\"P1\"}" + " ".repeat(50_000);
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/requests"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(payload))).build();
        try (var client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()) {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(413);
        }
    }

    @Test
    void rejectsOversizedChunkedBodyWithoutContentLength() throws Exception {
        byte[] payload = ("{\"externalId\":\"" + "x".repeat(20_000) + "\"}").getBytes(StandardCharsets.UTF_8);
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/requests"))
                .header("Content-Type", "application/json")
                .header("X-Correlation-ID", "body-limit")
                .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(payload))).build();
        try (var client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()) {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(413);
            assertThat(response.body()).contains("payload-too-large", "body-limit").doesNotContain("stackTrace");
        }
    }
}
