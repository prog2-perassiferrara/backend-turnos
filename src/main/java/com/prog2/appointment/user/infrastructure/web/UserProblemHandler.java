package com.prog2.appointment.user.infrastructure.web;

import java.net.URI;
import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.prog2.appointment.user.application.exception.InvalidCredentialsException;
import com.prog2.appointment.user.domain.exception.UserAccountUnavailableException;
import com.prog2.appointment.user.domain.exception.UserIdentifierAlreadyUsedException;
import com.prog2.appointment.user.domain.exception.UserSecurityException;
import com.prog2.appointment.user.infrastructure.web.controller.AuthenticationController;
import com.prog2.appointment.user.infrastructure.web.controller.RegistrationController;

/** Errores seguros limitados al slice; nunca copia mensajes, cuerpos ni causas que contengan credenciales. */
@RestControllerAdvice(assignableTypes = {RegistrationController.class, AuthenticationController.class})
public final class UserProblemHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException failure, HttpServletRequest request) {
        var problem = problem(HttpStatus.BAD_REQUEST, "Invalid request", "VALIDATION_ERROR", request);
        List<Map<String, String>> fields = failure.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of("field", error.getField(),
                        "code", error.getCode() == null ? "Invalid" : error.getCode()))
                .distinct().toList();
        problem.setProperty("fieldErrors", fields);
        return response(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> malformedBody(HttpServletRequest request) {
        // El conversor puede incluir el cuerpo o el valor problemático en el mensaje original.
        return response(problem(HttpStatus.BAD_REQUEST, "Invalid request", "VALIDATION_ERROR", request));
    }

    @ExceptionHandler(UserIdentifierAlreadyUsedException.class)
    ResponseEntity<ProblemDetail> duplicate(HttpServletRequest request) {
        return response(problem(HttpStatus.BAD_REQUEST, "User identifier already used",
                "USER_IDENTIFIER_ALREADY_USED", request));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ProblemDetail> invalidCredentials(HttpServletRequest request) {
        return response(problem(HttpStatus.UNAUTHORIZED, "Invalid credentials", "INVALID_CREDENTIALS", request));
    }

    @ExceptionHandler({UserAccountUnavailableException.class, UserSecurityException.class})
    ResponseEntity<ProblemDetail> unavailable(HttpServletRequest request) {
        String code = "/api/register".equals(request.getServletPath())
                ? "USER_REGISTRATION_UNAVAILABLE" : "AUTHENTICATION_UNAVAILABLE";
        return response(problem(HttpStatus.SERVICE_UNAVAILABLE, "User operation unavailable", code, request));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(HttpServletRequest request) {
        return response(problem(HttpStatus.INTERNAL_SERVER_ERROR, "User operation failed", "INTERNAL_ERROR", request));
    }

    private static ProblemDetail problem(HttpStatus status, String title, String code,
            HttpServletRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(status, title);
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        return problem;
    }

    private static ResponseEntity<ProblemDetail> response(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus()).cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }
}
