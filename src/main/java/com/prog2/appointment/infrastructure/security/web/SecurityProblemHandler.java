package com.prog2.appointment.infrastructure.security.web;

import java.io.IOException;
import java.net.URI;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.json.JsonMapper;

/**
 * Traduce rechazos anteriores a MVC a errores 401/403, sin secretos ni causas originales.
 * El handler de registro/login conserva los errores producidos por sus propios casos de uso.
 */
public final class SecurityProblemHandler implements AuthenticationEntryPoint, AccessDeniedHandler {
    private final JsonMapper mapper;

    /** @param mapper serializador del proceso, copiado con soporte de extensiones ProblemDetail */
    public SecurityProblemHandler(JsonMapper mapper) {
        // Los filtros no usan el converter MVC que normalmente configura este mixin.
        this.mapper = mapper.rebuild().addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class).build();
    }

    /** Responde sin copiar la excepción de autenticación y anuncia el contrato bearer. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException failure) throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        write(response, HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED");
    }

    /** Rechaza una identidad sin permiso antes de ejecutar la operación protegida. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException failure) throws IOException {
        write(response, HttpStatus.FORBIDDEN, "ACCESS_DENIED");
    }

    private void write(HttpServletResponse response, HttpStatus status, String code) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setType(URI.create("about:blank"));
        problem.setProperty("code", code);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        mapper.writeValue(response.getOutputStream(), problem);
    }
}
