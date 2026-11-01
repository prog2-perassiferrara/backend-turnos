package com.prog2.appointment.reservation.infrastructure.web.mapper;

import com.prog2.appointment.reservation.domain.model.OwnReservations;
import com.prog2.appointment.reservation.infrastructure.web.dto.OwnReservationsResponse;
import com.prog2.appointment.reservation.infrastructure.web.dto.ReservationHistoryResponse;

import java.time.format.DateTimeFormatter;
import com.prog2.appointment.reservation.domain.model.ReservationStatus;
import org.springframework.stereotype.Component;
import com.prog2.appointment.reservation.domain.model.ReservationProcess;
import com.prog2.appointment.reservation.domain.model.ReservationSelection;
import com.prog2.appointment.reservation.domain.model.StartReservationRequest;
import com.prog2.appointment.reservation.infrastructure.web.dto.ReservationProcessResponse;
import com.prog2.appointment.reservation.infrastructure.web.dto.StartReservationBody;

/**
 * Traduce DTO/modelos propios sin trasladar identidades proporcionadas libremente por el cliente.
 */
@Component
public final class ReservationDtoMapper {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * @return envelope estable, independiente de los tipos Page de Spring y de IDs centrales
     */
    public OwnReservationsResponse toResponse(OwnReservations result) {
        var history =
                new ReservationHistoryResponse(
                        result.history().stream().map(this::toResponse).toList(),
                        result.page(),
                        result.size(),
                        result.totalElements(),
                        result.totalElements() / result.size()
                                + (result.totalElements() % result.size() == 0 ? 0 : 1));
        return new OwnReservationsResponse(
                result.pending() == null ? null : toResponse(result.pending()),
                history,
                result.verificationStatus().name(),
                result.verifiedAt(),
                result.warningCode());
    }

    public StartReservationRequest toRequest(StartReservationBody body) {
        return new StartReservationRequest(
                body.requestId(),
                new ReservationSelection(body.professionalId(), body.date(), body.startTime()));
    }

    public ReservationProcessResponse toResponse(ReservationProcess process) {
        return toResponse(
                new ReservationStatus(
                        process,
                        null,
                        false,
                        com.prog2.appointment.reservation.domain.enums.VerificationStatus.LOCAL,
                        null,
                        null));
    }

    public ReservationProcessResponse toResponse(ReservationStatus status) {
        var process = status.process();
        return new ReservationProcessResponse(
                process.id(),
                process.selection().professionalId(),
                process.selection().date(),
                TIME.format(process.selection().startTime()),
                process.phase().name(),
                process.hold() == null ? null : process.hold().expiresAt(),
                process.reasonCode(),
                status.phoneRequestId(),
                process.requestEventId() == null || process.phase().isFinal()
                        ? null
                        : "PHONE_NUMBER",
                status.canSubmitPhone(),
                status.verificationStatus().name(),
                status.verifiedAt(),
                status.warningCode(),
                process.professionalName() == null ? null : process.professionalName().firstName(),
                process.professionalName() == null ? null : process.professionalName().lastName());
    }
}
