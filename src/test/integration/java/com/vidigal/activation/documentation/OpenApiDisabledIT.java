package com.vidigal.activation.documentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vidigal.activation.support.PostgresTestConfiguration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"activation.security.mode=local", "activation.worker.enabled=false",
        "springdoc.api-docs.enabled=false", "springdoc.swagger-ui.enabled=false"})
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class OpenApiDisabledIT {

    @Autowired
    private MockMvc mvc;

    @ParameterizedTest
    @ValueSource(strings = {"/v3/api-docs", "/swagger-ui/swagger-initializer.js"})
    void doesNotExposeTheSpecificationOrScenarioScriptsWhenDocumentationIsDisabled(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isNotFound());
    }
}
