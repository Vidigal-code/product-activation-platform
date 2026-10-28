package com.vidigal.activation.documentation;

import com.vidigal.activation.security.SecurityProperties;
import com.vidigal.activation.security.RateLimitProperties;
import com.vidigal.activation.request.api.ActivationRequestController;
import com.vidigal.activation.request.integration.RetryProperties;
import com.vidigal.activation.web.RequestBodyProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springdoc.webmvc.ui.SwaggerIndexTransformer;
import org.springdoc.webmvc.ui.SwaggerWelcomeCommon;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "activation.security.mode", havingValue = SecurityProperties.LOCAL_MODE)
public class ActivationDemoConfiguration {

    @Bean
    @ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
    OpenApiCustomizer activationDemoMetadata(ActivationDocumentationProperties documentation,
            RequestBodyProperties body, RateLimitProperties rateLimit, RetryProperties retry) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("path", ActivationRequestController.PATH);
        metadata.put("pollIntervalMs", documentation.pollIntervalMs());
        metadata.put("timeoutMs", documentation.timeoutMs());
        metadata.put("requestTimeoutMs", documentation.requestTimeoutMs());
        metadata.put("maxBodyBytes", body.maxBodyBytes());
        metadata.put("rateLimitMaxRequests", rateLimit.maxRequests());
        metadata.put("rateLimitWindowMs", rateLimit.window().toMillis());
        metadata.put("retryMaxAttempts", retry.maxAttempts());
        metadata.put("scenarios", ActivationDemoScenarios.scenarios());
        return openApi -> openApi.addExtension("x-activation-demo", metadata);
    }

    @Bean
    @ConditionalOnProperty(name = {"springdoc.api-docs.enabled", "springdoc.swagger-ui.enabled"}, havingValue = "true")
    SwaggerIndexTransformer activationScenarioTransformer(SwaggerUiConfigProperties properties,
            SwaggerUiOAuthProperties oauth, SwaggerWelcomeCommon welcome, ObjectMapperProvider mapper) {
        return new SwaggerScenarioTransformer(properties, oauth, welcome, mapper);
    }
}
