package com.vidigal.activation.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.ServletException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    @Test
    void propagatesSafeCorrelationAndCleansThreadAfterRequest() throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        request.addHeader("X-Correlation-ID", "request-123");

        new CorrelationIdFilter().doFilter(request, response, (incoming, outgoing) ->
                assertThat(MDC.get("correlationId")).isEqualTo("request-123"));

        assertThat(response.getHeader("X-Correlation-ID")).isEqualTo("request-123");
        assertThat(MDC.get("correlationId")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "invalid id", "injected\r\nline", "x" +
            "1234567890123456789012345678901234567890123456789012345678901234"})
    void replacesUnsafeIdentifiers(String incoming) throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        request.addHeader("X-Correlation-ID", incoming);

        new CorrelationIdFilter().doFilter(request, response, (ignoredRequest, ignoredResponse) -> { });

        String actual = response.getHeader("X-Correlation-ID");
        assertThat(UUID.fromString(actual)).hasToString(actual);
    }

    @Test
    void clearsCorrelationEvenWhenDownstreamFails() {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        assertThatThrownBy(() -> new CorrelationIdFilter().doFilter(request, response,
                (incoming, outgoing) -> { throw new ServletException("test failure"); }))
                .isInstanceOf(ServletException.class);

        assertThat(MDC.get("correlationId")).isNull();
    }
}
