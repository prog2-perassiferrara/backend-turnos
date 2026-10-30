package com.prog2.appointment.reservation.infrastructure.web.mapper;

import java.time.format.DateTimeFormatter;
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

    public StartReservationRequest toRequest(StartReservationBody body) {
        return new StartReservationRequest(
                body.requestId(),
                new ReservationSelection(body.professionalId(), body.date(), body.startTime()));
    }

    public ReservationProcessResponse toResponse(ReservationProcess process) {
        return new ReservationProcessResponse(
                process.id(),
                process.selection().professionalId(),
                process.selection().date(),
                TIME.format(process.selection().startTime()),
                process.phase().name(),
                process.hold() == null ? null : process.hold().expiresAt(),
                process.reasonCode());
    }
}
