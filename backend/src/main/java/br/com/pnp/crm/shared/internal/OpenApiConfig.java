package br.com.pnp.crm.shared.internal;

import br.com.pnp.crm.shared.api.ErroResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Superfície OpenAPI determinística e erros RFC 9457 em todas as operações. */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    private static final String PROBLEM_SCHEMA = "#/components/schemas/ErroResponse";

    @Bean
    OpenAPI crmOpenApi() {
        Components components = new Components();
        ModelConverters.getInstance().readAll(ErroResponse.class)
                .forEach(components::addSchemas);
        ModelConverters.getInstance().readAll(ErroResponse.ErroCampo.class)
                .forEach(components::addSchemas);
        return new OpenAPI()
                .info(new Info()
                        .title("CRM PNP API")
                        .version("1.0.0")
                        .description("Contrato HTTP do CRM PNP. Tenant é sempre derivado da credencial."))
                .components(components);
    }

    @Bean
    OperationCustomizer standardProblemResponses() {
        return (operation, handlerMethod) -> {
            addProblem(operation, "400", "Requisição ou JSON inválido");
            addProblem(operation, "401", "Autenticação ausente ou expirada");
            addProblem(operation, "403", "Acesso negado");
            addProblem(operation, "404", "Recurso não encontrado");
            addProblem(operation, "409", "Conflito de estado ou idempotência");
            addProblem(operation, "422", "Regra de negócio ou validação semântica");
            addProblem(operation, "429", "Limite de requisições excedido");
            addProblem(operation, "500", "Falha interna com detalhe seguro");
            return operation;
        };
    }

    private static void addProblem(io.swagger.v3.oas.models.Operation operation,
                                   String status, String description) {
        operation.getResponses().addApiResponse(status, new ApiResponse()
                .description(description)
                .content(new Content().addMediaType(
                        org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        new io.swagger.v3.oas.models.media.MediaType()
                                .schema(new Schema<>().$ref(PROBLEM_SCHEMA)))));
    }
}
