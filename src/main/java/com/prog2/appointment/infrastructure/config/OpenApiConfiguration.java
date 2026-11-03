package com.prog2.appointment.infrastructure.config;

import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;

/** Metadatos documentales; describe contratos existentes sin invocar sus casos de uso. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.api-docs.enabled", havingValue = "true")
public class OpenApiConfiguration {
    public static final String PROBLEM_SCHEMA = "#/components/schemas/ApiProblem";
    public static final String UNAUTHORIZED_RESPONSE = "#/components/responses/Unauthorized";
    public static final String FORBIDDEN_RESPONSE = "#/components/responses/Forbidden";
    private static final String STATUS_PROPERTY = "status";
    private static final String PROBLEM_MEDIA_TYPE = "application/problem+json";

    /** @return documento común con bearer explícito y errores producidos por los filtros de seguridad */
    @Bean
    OpenAPI appointmentOpenApi() {
        var fieldError = new ObjectSchema()
                .addProperty("field", new StringSchema())
                .addProperty("code", new StringSchema());
        var problem = new ObjectSchema()
                .addProperty("type", new StringSchema().format("uri-reference").example("about:blank"))
                .addProperty("title", new StringSchema())
                .addProperty(STATUS_PROPERTY, new IntegerSchema().format("int32"))
                .addProperty("code", new StringSchema().description("Código estable de la respuesta."))
                .addProperty("instance", new StringSchema().format("uri-reference"))
                .addProperty("processId", new StringSchema().format("uuid")
                        .description("Proceso propio al que se refiere el error, cuando corresponde."))
                .addProperty("recheckAt", new StringSchema().format("date-time")
                        .description("Instante UTC sugerido para reconciliar el estado."))
                .addProperty("fieldErrors", new ArraySchema().items(fieldError))
                .required(List.of("type", STATUS_PROPERTY, "code"));
        var bearer = new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                .description("JWT propio emitido por Turnos: token_use=user, auth=ROLE_USER, "
                        + "iss=backend-turnos y aud=turnos. El JWT técnico de cátedra no autentica estas APIs.");
        return new OpenAPI().info(new Info().title("API de turnos y reservas").version("1.0")
                        .description("Try it out ejecuta las operaciones reales. Las operaciones públicas no requieren bearer."))
                .components(new Components().addSecuritySchemes("bearerAuth", bearer)
                        .addSchemas("ApiProblem", problem)
                        .addResponses("Unauthorized", securityProblem(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED"))
                        .addResponses("Forbidden", securityProblem(HttpStatus.FORBIDDEN, "ACCESS_DENIED")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .tags(List.of(new Tag().name("Cuenta").description("Registro y autenticación públicos."),
                        new Tag().name("Disponibilidad").description("Consulta de horarios libres para usuarios."),
                        new Tag().name("Reservas propias").description("Procesos y reservas del usuario autenticado.")));
    }

    private static ApiResponse securityProblem(HttpStatus status, String code) {
        return new ApiResponse().description(status == HttpStatus.UNAUTHORIZED
                        ? "JWT ausente, inválido o vencido." : "El perfil o permiso no autoriza esta operación.")
                .content(new Content().addMediaType(PROBLEM_MEDIA_TYPE, new MediaType()
                        .schema(new Schema<>().$ref(PROBLEM_SCHEMA))
                        .example(Map.of("type", "about:blank", "title", status.getReasonPhrase(),
                                STATUS_PROPERTY, status.value(), "code", code))));
    }
}
