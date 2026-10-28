package com.vidigal.activation.documentation;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springdoc.webmvc.ui.SwaggerIndexPageTransformer;
import org.springdoc.webmvc.ui.SwaggerWelcomeCommon;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.ResourceTransformerChain;
import org.springframework.web.servlet.resource.TransformedResource;

final class SwaggerScenarioTransformer extends SwaggerIndexPageTransformer {

    private static final String DEFAULT_PLUGIN = "SwaggerUIBundle.plugins.DownloadUrl";

    SwaggerScenarioTransformer(SwaggerUiConfigProperties properties, SwaggerUiOAuthProperties oauth,
            SwaggerWelcomeCommon welcome, ObjectMapperProvider mapper) {
        super(properties, oauth, welcome, mapper);
    }

    @Override
    public Resource transform(HttpServletRequest request, Resource resource, ResourceTransformerChain chain)
            throws IOException {
        Resource transformed = super.transform(request, resource, chain);
        if (!"swagger-initializer.js".equals(resource.getFilename())) {
            return transformed;
        }
        String initializer = transformed.getContentAsString(StandardCharsets.UTF_8);
        int pluginIndex = initializer.indexOf(DEFAULT_PLUGIN);
        if (pluginIndex < 0 || initializer.indexOf(DEFAULT_PLUGIN, pluginIndex + DEFAULT_PLUGIN.length()) >= 0) {
            throw new IOException("Swagger initializer must contain exactly one default plugin registration");
        }
        String scripts = script("swagger/activation-demo-runner.js") + "\n;\n"
                + script("swagger/activation-demo-plugin.js") + "\n;\n";
        String content = scripts + initializer.replace(DEFAULT_PLUGIN, DEFAULT_PLUGIN + ", ActivationScenariosPlugin");
        return new TransformedResource(transformed, content.getBytes(StandardCharsets.UTF_8));
    }

    private static String script(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }
}
