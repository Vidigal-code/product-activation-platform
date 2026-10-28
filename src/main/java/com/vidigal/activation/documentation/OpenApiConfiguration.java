package com.vidigal.activation.documentation;

import com.vidigal.activation.request.api.ActivationRequestController;
import com.vidigal.activation.request.api.PaginationProperties;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.math.BigDecimal;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
public class OpenApiConfiguration {

    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    OpenAPI activationOpenApi() {
        return new OpenAPI().info(new Info().title("Product Activation Platform").version("v1")
                        .description("""
                                Selecione um exemplo no POST e clique em Execute. A criação retorna 201 com id e Location;
                                copie esse id para o GET e execute novamente para acompanhar o processamento assíncrono.
                                Os exemplos estão preenchidos para o Docker Compose com WireMock. Para um novo ensaio,
                                altere o externalId; repetir o mesmo conteúdo retorna 200, sem reprocessar.
                                Em modo local não é necessário token. Em produção, use JWT obtido por Client Credentials:
                                escopo requests:write no POST e requests:read no GET.
                                """))
                .components(new Components().addSecuritySchemes(BEARER_AUTH,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }

    @Bean
    OpenApiCustomizer activationDocumentation(PaginationProperties pagination) {
        return api -> {
            api.getComponents().addSchemas(ActivationApiResponses.PROBLEM_SCHEMA, ActivationApiResponses.problemSchema());
            var creation = api.getPaths().get(ActivationRequestController.PATH).getPost()
                    .summary("Registrar solicitação de ativação")
                    .description("Valida e persiste antes de processar. A resposta 201 confirma o registro; consulte o GET para o resultado do provedor.")
                    .responses(ActivationApiResponses.creation());
            creation.getRequestBody().getContent().get(MediaType.APPLICATION_JSON_VALUE)
                    .examples(ActivationApiExamples.requests());
            var listing = api.getPaths().get(ActivationRequestController.PATH).getGet()
                    .summary("Listar solicitações do consumidor")
                    .description("Exibe somente solicitações do consumidor autenticado. Filtre por status ou customerId; a ordem é createdAt e id crescentes. A primeira página é zero.")
                    .responses(ActivationApiResponses.listing(pagination.defaultSize()));
            listing.getParameters().forEach(parameter -> configurePagination(parameter, pagination));
            api.getPaths().get(ActivationRequestController.PATH + "/{id}").getGet()
                    .summary("Consultar solicitação e processamento")
                    .description("Copie o id recebido no POST. Consulte novamente até COMPLETED ou FAILED; falhas do provedor aparecem no estado, com HTTP 200.")
                    .responses(ActivationApiResponses.lookup());
        };
    }

    private static void configurePagination(Parameter parameter, PaginationProperties pagination) {
        if ("page".equals(parameter.getName())) {
            parameter.setDescription("Página desejada, iniciando em zero.");
            parameter.setSchema(pageSchema(0, 0, pagination.maxPage()));
        } else if ("size".equals(parameter.getName())) {
            parameter.setDescription("Quantidade máxima de solicitações por página.");
            parameter.setSchema(pageSchema(pagination.defaultSize(), 1, pagination.maxSize()));
        }
    }

    private static IntegerSchema pageSchema(int defaultValue, int minimum, int maximum) {
        IntegerSchema schema = new IntegerSchema();
        schema.setDefault(defaultValue);
        schema.setMinimum(BigDecimal.valueOf(minimum));
        schema.setMaximum(BigDecimal.valueOf(maximum));
        return schema;
    }
}
