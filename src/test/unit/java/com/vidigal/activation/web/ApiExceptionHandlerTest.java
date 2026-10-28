package com.vidigal.activation.web;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiExceptionHandlerTest {

    @Test
    void hidesUnexpectedFailureDetailsBehindCorrelatedProblem() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new FailureEndpoint())
                .setControllerAdvice(new ApiExceptionHandler())
                .addFilter(new CorrelationIdFilter()).build();

        mvc.perform(get("/test/failure").header("X-Correlation-ID", "unexpected-001"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("internal-error"))
                .andExpect(jsonPath("$.detail").value("Não foi possível processar a solicitação."))
                .andExpect(jsonPath("$.correlationId").value("unexpected-001"))
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @RestController
    @TestConfiguration(proxyBeanMethods = false)
    static class FailureEndpoint {
        @GetMapping("/test/failure")
        String fail() {
            throw new IllegalStateException("Internal database details must stay private");
        }
    }
}
