package com.challenge.starwars.config;

import com.challenge.starwars.controllers.support.ListingApi;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;

/**
 * Documento OpenAPI: esquema Bearer JWT aplicado por defecto a cada operación (login lo excluye), un
 * esquema {@code ProblemDetail} según RFC 9457 y las respuestas de error compartidas por todos los endpoints de listado.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    public static final String BEARER_SCHEME = "bearerAuth";
    public static final String PROBLEM_JSON = "application/problem+json";
    public static final String PROBLEM_SCHEMA = "ProblemDetail";
    public static final String PROBLEM_SCHEMA_REF = "#/components/schemas/" + PROBLEM_SCHEMA;

    private static void addIfAbsent(io.swagger.v3.oas.models.Operation operation, String status, String description) {
        if (operation.getResponses().get(status) == null) {
            operation.getResponses().addApiResponse(status, problemResponse(description));
        }
    }

    private static ApiResponse problemResponse(String description) {
        return new ApiResponse().description(description).content(new Content().addMediaType(PROBLEM_JSON,
                new MediaType().schema(new Schema<>().$ref(PROBLEM_SCHEMA_REF))));
    }

    @Bean
    OpenAPI starWarsOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Star Wars API")
                        .version("v1")
                        .description("Authenticated, paginated access to People, Films, Starships and Vehicles "
                                + "served from swapi.tech. Obtain a token with POST /api/v1/auth/login, then press "
                                + "'Authorize' and paste it."))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }

    @Bean
    OpenApiCustomizer problemDetailSchema() {
        return openApi -> openApi.getComponents().addSchemas(PROBLEM_SCHEMA, new ObjectSchema()
                .description("RFC 9457 problem details")
                .addProperty("type", new StringSchema().example("about:blank"))
                .addProperty("title", new StringSchema().example("Bad Request"))
                .addProperty("status", new IntegerSchema().example(400))
                .addProperty("detail", new StringSchema().example("One or more request parameters are invalid."))
                .addProperty("errors", new Schema<>().type("array")
                        .description("Present on 400 responses: the offending parameters")
                        .items(new ObjectSchema()
                                .addProperty("field", new StringSchema().example("page"))
                                .addProperty("message", new StringSchema()))));
    }

    @Bean
    OperationCustomizer listingErrorResponses() {
        return (operation, handlerMethod) -> {
            if (AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), ListingApi.class)) {
                addIfAbsent(operation, "400", "Invalid page, id, name or sort");
                addIfAbsent(operation, "401", "Missing, invalid or expired Bearer token");
                addIfAbsent(operation, "502", "swapi.tech failed or answered something unreadable");
                addIfAbsent(operation, "503", "swapi.tech rate limit reached; see Retry-After when present");
                addIfAbsent(operation, "504", "swapi.tech did not answer in time");
            }
            return operation;
        };
    }
}
