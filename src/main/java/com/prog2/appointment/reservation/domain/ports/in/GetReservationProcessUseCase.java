package com.prog2.appointment.reservation.domain.ports.in;

import com.prog2.appointment.reservation.domain.model.ReservationStatus;

import java.util.UUID;

/** Lectura propia con reconciliación puntual; no ejecuta operaciones centrales con efectos. */
public interface GetReservationProcessUseCase {
    /**
     * @return progreso propio y certeza de la consulta, o rechazo seguro por ausencia/propiedad
     */
    ReservationStatus get(long ownerId, UUID processId);
}
