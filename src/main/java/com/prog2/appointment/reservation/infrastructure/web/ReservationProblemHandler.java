package com.prog2.appointment.reservation.infrastructure.web;

import java.net.URI;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.prog2.appointment.reservation.application.exception.ReservationOperationException;
import com.prog2.appointment.reservation.domain.enums.ReservationFailure;
import com.prog2.appointment.reservation.domain.exception.ReservationConflictException;
import com.prog2.appointment.reservation.domain.exception.ReservationGatewayException;
import com.prog2.appointment.reservation.domain.exception.ReservationStorageException;
import com.prog2.appointment.reservation.infrastructure.web.controller.ReservationController;
import jakarta.servlet.http.HttpServletRequest;

/** Problemas seguros del slice; no cambia los rechazos 401/403 de los filtros existentes. */
@RestControllerAdvice(assignableTypes = ReservationController.class)
public final class ReservationProblemHandler {
    @ExceptionHandler(ReservationOperationException.class)
    ResponseEntity<ProblemDetail> operation(
            ReservationOperationException failure, HttpServletRequest request) {
        return problem(failure.failure(), failure.processId(), request, failure.recheckAt());
    }

    @ExceptionHandler(ReservationGatewayException.class)
    ResponseEntity<ProblemDetail> provider(
            ReservationGatewayException failure, HttpServletRequest request) {
        return problem(failure.failure(), null, request);
    }

    @ExceptionHandler(ReservationStorageException.class)
    ResponseEntity<ProblemDetail> storage(HttpServletRequest request) {
        return problem(ReservationFailure.STORAGE_UNAVAILABLE, null, request);
    }

    @ExceptionHandler(ReservationConflictException.class)
    ResponseEntity<ProblemDetail> conflict(HttpServletRequest request) {
        return problem(ReservationFailure.REQUEST_ID_CONFLICT, null, request);
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        HttpMessageNotReadableException.class,
        IllegalArgumentException.class
    })
    ResponseEntity<ProblemDetail> invalid(HttpServletRequest request) {
        return problem(ReservationFailure.INVALID_RESERVATION_SELECTION, null, request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(HttpServletRequest request) {
        return problem(ReservationFailure.INTERNAL_ERROR, null, request);
    }

    private static ResponseEntity<ProblemDetail> problem(
            ReservationFailure failure, UUID processId, HttpServletRequest request) {
        return problem(failure, processId, request, null);
    }

    private static ResponseEntity<ProblemDetail> problem(
            ReservationFailure failure,
            UUID processId,
            HttpServletRequest request,
            java.time.Instant recheckAt) {
        HttpStatus status =
                switch (failure) {
                    case INVALID_RESERVATION_SELECTION, PROFILE_NOT_RESERVABLE ->
                            HttpStatus.BAD_REQUEST;
                    case ACTIVE_RESERVATION_PROCESS,
                            REQUEST_ID_CONFLICT,
                            SLOT_NOT_AVAILABLE,
                            HOLD_EXPIRED,
                            CONFIRMATION_REJECTED ->
                            HttpStatus.CONFLICT;
                    case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
                    default -> HttpStatus.SERVICE_UNAVAILABLE;
                };
        var body = ProblemDetail.forStatus(status);
        body.setType(URI.create("about:blank"));
        body.setInstance(URI.create(request.getRequestURI()));
        body.setProperty("code", failure.name());
        if (processId != null) {
            body.setProperty("processId", processId.toString());
        }
        if (recheckAt != null) {
            body.setProperty("recheckAt", recheckAt);
        }
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }
}
