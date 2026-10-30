package com.prog2.appointment.reservation.infrastructure.client.adapter;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.prog2.appointment.integration.domain.ports.in.TechnicalAccessUseCase;
import com.prog2.appointment.reservation.domain.enums.ReservationFailure;
import com.prog2.appointment.reservation.domain.exception.ReservationGatewayException;
import com.prog2.appointment.reservation.domain.model.HoldDetails;
import com.prog2.appointment.reservation.domain.model.HoldOccupancy;
import com.prog2.appointment.reservation.domain.model.ReservationSelection;
import com.prog2.appointment.reservation.domain.model.ReservationProcess;
import com.prog2.appointment.reservation.domain.ports.out.AppointmentHoldGateway;
import com.prog2.appointment.reservation.infrastructure.client.dto.HoldResponse;
import com.prog2.appointment.reservation.infrastructure.client.dto.HoldOccupanciesResponse;
import reactor.core.publisher.Mono;

/**
 * Adapter REST con un intento explícito; la recuperación de sesión no repite el efecto de negocio.
 */
public final class WebClientAppointmentHoldAdapter implements AppointmentHoldGateway {
    private final WebClient client;
    private final TechnicalAccessUseCase access;
    private final Duration timeout;

    public WebClientAppointmentHoldAdapter(
            WebClient client, TechnicalAccessUseCase access, Duration timeout) {
        this.client = client;
        this.access = access;
        this.timeout = timeout;
    }

    @Override
    public HoldDetails create(ReservationProcess process) {
        var slot = process.selection();
        var body =
                Map.of(
                        "professionalId",
                        slot.professionalId(),
                        "date",
                        slot.date().toString(),
                        "startTime",
                        slot.startTime().toString(),
                        "externalPatientId",
                        process.patient().externalPatientId());
        var response = post(null, body, 201);
        try {
            if (!"HELD".equals(response.status())) {
                throw new IllegalArgumentException();
            }
            return new HoldDetails(
                    response.holdId(), response.reservationProcessId(), response.expiresAt());
        } catch (RuntimeException _) {
            throw failure(ReservationFailure.HOLD_CREATION_UNCERTAIN, true, false);
        }
    }

    @Override
    public HoldDetails confirm(ReservationProcess process) {
        var patient = process.patient();
        var body =
                Map.of(
                        "externalPatientId",
                        patient.externalPatientId(),
                        "patientFirstName",
                        patient.firstName(),
                        "patientLastName",
                        patient.lastName());
        var response = post(process.hold().holdId(), body, 202);
        if (!"WAITING_FOR_PHONE".equals(response.status())
                || !process.hold().centralProcessId().equals(response.reservationProcessId())
                || !process.hold().expiresAt().equals(response.expiresAt())) {
            throw failure(ReservationFailure.CONFIRMATION_UNCERTAIN, true, false);
        }
        return process.hold();
    }

    @Override
    public Optional<HoldOccupancy> findOccupancy(ReservationSelection selection) {
        String token = accessToken();
        try {
            var response =
                    client.get()
                            .uri(
                                    builder ->
                                            builder.path("/api/appointment-occupancies")
                                                    .queryParam(
                                                            "professionalId",
                                                            selection.professionalId())
                                                    .queryParam("from", selection.date())
                                                    .queryParam("to", selection.date())
                                                    .build())
                            .headers(headers -> headers.setBearerAuth(token))
                            .accept(MediaType.APPLICATION_JSON)
                            .exchangeToMono(
                                    result -> {
                                        if (result.statusCode().value() == 200) {
                                            return result.bodyToMono(HoldOccupanciesResponse.class);
                                        }
                                        if (result.statusCode().value() == 401) {
                                            access.rejectAccess(token);
                                        }
                                        return result.releaseBody()
                                                .then(
                                                        Mono.error(
                                                                failure(
                                                                        ReservationFailure
                                                                                .OCCUPANCIES_UNAVAILABLE,
                                                                        false,
                                                                        false)));
                                    })
                            .block(timeout.multipliedBy(2));
            return selectedOccupancy(response, selection);
        } catch (ReservationGatewayException unavailable) {
            throw unavailable;
        } catch (RuntimeException _) {
            throw failure(ReservationFailure.OCCUPANCIES_UNAVAILABLE, false, false);
        }
    }

    private static Optional<HoldOccupancy> selectedOccupancy(
            HoldOccupanciesResponse response, ReservationSelection selection) {
        if (response == null
                || response.professionalId() == null
                || response.professionalId() != selection.professionalId()
                || !selection.date().equals(response.from())
                || !selection.date().equals(response.to())
                || response.occupancies() == null) {
            throw new IllegalArgumentException();
        }
        HoldOccupancy selected = null;
        for (var value : response.occupancies()) {
            var occupied = occupation(value, selection);
            if (value.startTime().equals(selection.startTime())) {
                if (selected == null
                        || occupied.confirmed()
                        || !selected.confirmed()
                                && occupied.expiresAt().isAfter(selected.expiresAt())) {
                    selected = occupied;
                }
            }
        }
        return Optional.ofNullable(selected);
    }

    private static HoldOccupancy occupation(
            HoldOccupanciesResponse.Occupancy value, ReservationSelection selection) {
        if (value == null
                || !selection.date().equals(value.date())
                || value.startTime() == null
                || value.startTime().getNano() != 0) {
            throw new IllegalArgumentException();
        }
        return switch (value.status() == null ? "" : value.status()) {
            case "HELD" ->
                    new HoldOccupancy(false, java.util.Objects.requireNonNull(value.expiresAt()));
            case "CONFIRMED" -> new HoldOccupancy(true, value.expiresAt());
            default -> throw new IllegalArgumentException();
        };
    }

    private String accessToken() {
        try {
            return access.getAccess().token().value();
        } catch (IllegalStateException _) {
            throw failure(ReservationFailure.TECHNICAL_INTEGRATION_UNAVAILABLE, false, false);
        }
    }

    private HoldResponse post(String holdId, Object body, int accepted) {
        String token = accessToken();
        try {
            var result =
                    client.post()
                            .uri(
                                    builder ->
                                            holdId == null
                                                    ? builder.path("/api/appointment-holds").build()
                                                    : builder.path("/api/appointment-holds")
                                                            .pathSegment(holdId, "confirm")
                                                            .build())
                            .headers(headers -> headers.setBearerAuth(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_JSON)
                            .bodyValue(body)
                            .exchangeToMono(
                                    response -> {
                                        int status = response.statusCode().value();
                                        if (status == accepted) {
                                            return response.bodyToMono(HoldResponse.class);
                                        }
                                        return response.bodyToMono(ProblemCode.class)
                                                .onErrorReturn(new ProblemCode(null))
                                                .defaultIfEmpty(new ProblemCode(null))
                                                .flatMap(
                                                        problem ->
                                                                Mono.error(
                                                                        classify(
                                                                                status,
                                                                                problem.code(),
                                                                                holdId != null,
                                                                                token)));
                                    })
                            .block(timeout.multipliedBy(2));
            if (result == null) {
                throw new IllegalArgumentException();
            }
            return result;
        } catch (ReservationGatewayException failure) {
            throw failure;
        } catch (RuntimeException _) {
            throw failure(
                    holdId == null
                            ? ReservationFailure.HOLD_CREATION_UNCERTAIN
                            : ReservationFailure.CONFIRMATION_UNCERTAIN,
                    true,
                    false);
        }
    }

    private ReservationGatewayException classify(
            int status, String code, boolean confirmation, String token) {
        if (status == 401) {
            access.rejectAccess(token);
            return failure(ReservationFailure.TECHNICAL_INTEGRATION_UNAVAILABLE, true, true);
        }
        if (status == 403 && "STUDENT_INTEGRATION_NOT_FOUND".equals(code)) {
            return failure(ReservationFailure.TECHNICAL_INTEGRATION_UNAVAILABLE, true, true);
        }
        if (confirmation && status == 404 && "HOLD_EXPIRED".equals(code)) {
            return failure(ReservationFailure.HOLD_EXPIRED, true, true);
        }
        if (status == 409
                && ("SLOT_ALREADY_RESERVED".equals(code) || "SLOT_ALREADY_HELD".equals(code))) {
            return failure(ReservationFailure.SLOT_NOT_AVAILABLE, true, true);
        }
        if (!confirmation && invalidSelection(status, code)) {
            return failure(ReservationFailure.INVALID_RESERVATION_SELECTION, true, true);
        }
        if (confirmation && (status == 400 || status == 403 || status == 404 || status == 409)) {
            return failure(ReservationFailure.CONFIRMATION_REJECTED, true, true);
        }
        return failure(
                confirmation
                        ? ReservationFailure.CONFIRMATION_UNCERTAIN
                        : ReservationFailure.HOLD_CREATION_UNCERTAIN,
                true,
                false);
    }

    private static boolean invalidSelection(int status, String code) {
        return status == 400
                        && ("INVALID_SLOT".equals(code)
                                || "PROFESSIONAL_DISABLED".equals(code)
                                || "VALIDATION_ERROR".equals(code))
                || status == 404 && "PROFESSIONAL_NOT_FOUND".equals(code);
    }

    private static ReservationGatewayException failure(
            ReservationFailure cause, boolean attempted, boolean definitive) {
        return new ReservationGatewayException(cause, attempted, definitive);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ProblemCode(String code) {}
}
