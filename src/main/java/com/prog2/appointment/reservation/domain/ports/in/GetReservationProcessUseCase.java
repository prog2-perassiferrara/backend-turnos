package com.prog2.appointment.reservation.domain.ports.in;

import com.prog2.appointment.reservation.domain.model.ReservationStatus;

import java.util.UUID;

/** Lectura local propia; la reconciliación central corresponde a recuperación. */
public interface GetReservationProcessUseCase {
    /**
     * @return progreso durable propio, o rechazo seguro por ausencia/propiedad
     */
    ReservationStatus get(long ownerId, UUID processId);
}
