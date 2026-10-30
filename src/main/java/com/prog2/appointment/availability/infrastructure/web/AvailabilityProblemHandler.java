package com.prog2.appointment.availability.infrastructure.web;

import java.net.URI;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import com.prog2.appointment.availability.application.exception.AvailabilityException;
import com.prog2.appointment.availability.domain.enums.AvailabilityFailure;
import com.prog2.appointment.availability.domain.exception.AvailabilityGatewayException;
import com.prog2.appointment.availability.infrastructure.web.controller.AvailabilityController;

/** Traducción segura de CU-03, sin modificar el manejo de errores de usuarios ni los filtros 401/403. */
@RestControllerAdvice(assignableTypes = AvailabilityController.class)
public final class AvailabilityProblemHandler {
    @ExceptionHandler(AvailabilityException.class)
    ResponseEntity<ProblemDetail> rejected(AvailabilityException failure, HttpServletRequest request) {
        return classified(failure.failure(), request);
    }

    @ExceptionHandler(AvailabilityGatewayException.class)
    ResponseEntity<ProblemDetail> provider(AvailabilityGatewayException failure, HttpServletRequest request) {
        return classified(failure.failure(), request);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    ResponseEntity<ProblemDetail> invalidInput(HttpServletRequest request) {
        return classified(AvailabilityFailure.INVALID_AVAILABILITY_QUERY, request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(HttpServletRequest request) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", request);
    }

    private static ResponseEntity<ProblemDetail> classified(AvailabilityFailure failure, HttpServletRequest request) {
        HttpStatus status = switch (failure) {
            case INVALID_AVAILABILITY_QUERY, PROFESSIONAL_DISABLED -> HttpStatus.BAD_REQUEST;
            case PROFESSIONAL_NOT_FOUND -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        String code = failure == AvailabilityFailure.CENTRAL_TOKEN_REJECTED
                ? AvailabilityFailure.TECHNICAL_INTEGRATION_UNAVAILABLE.name() : failure.name();
        return response(status, code, request);
    }

    private static ResponseEntity<ProblemDetail> response(HttpStatus status, String code, HttpServletRequest request) {
        var problem = ProblemDetail.forStatus(status);
        problem.setType(URI.create("about:blank"));
        problem.setProperty("code", code);
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }
}
