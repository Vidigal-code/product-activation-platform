package com.vidigal.activation.request.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ProviderPropertiesTest {

    private static final String ACTIVATION_PATH = "/provider/v1/activate";

    @Test
    void keepsProviderCredentialsOutOfConfigurationDescriptions() {
        ProviderProperties properties = new ProviderProperties(URI.create("https://provider.example"),
                Duration.ofSeconds(1), Duration.ofSeconds(2), true, "test-token", ACTIVATION_PATH);

        assertThat(properties.toString()).doesNotContain("test-token");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://provider.example", "http://192.168.1.10", "http://localhost.example",
            "http://127.0.0.1.example", "http://[::2]"})
    void rejectsCredentialsSentOverUnencryptedRemoteConnections(String baseUrl) {
        URI uri = URI.create(baseUrl);
        Duration timeout = Duration.ofSeconds(1);

        assertThatThrownBy(() -> new ProviderProperties(uri, timeout, timeout, false, "test-token", ACTIVATION_PATH))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({"ftp://provider.example,1,1", "https://user@provider.example,1,1",
            "https://provider.example?token=invalid,1,1", "https://provider.example#fragment,1,1",
            "https:/invalid,1,1", "https://provider.example,0,1", "https://provider.example,1,0",
            "https://provider.example,-1,1"})
    void rejectsUnsafeProviderConfiguration(String baseUrl, long connectTimeout, long readTimeout) {
        URI uri = URI.create(baseUrl);
        Duration connection = Duration.ofSeconds(connectTimeout);
        Duration read = Duration.ofSeconds(readTimeout);

        assertThatThrownBy(() -> new ProviderProperties(uri, connection, read, false, "", ACTIVATION_PATH))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "https://other.example/activate", "//other.example/activate", "/a/../activate",
            "/a/%2e%2e/activate", "/activate?token=invalid", "/activate#fragment"})
    void rejectsActivationPathsThatChangeOriginOrEscapeConfiguredEndpoint(String activationPath) {
        URI baseUrl = URI.create("https://provider.example");
        Duration timeout = Duration.ofSeconds(1);

        assertThatThrownBy(() -> new ProviderProperties(baseUrl, timeout, timeout, false, "", activationPath))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
