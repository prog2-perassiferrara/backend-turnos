package com.prog2.appointment.reservation.domain.ports.in;

import com.prog2.appointment.reservation.domain.model.OwnReservations;

/** Consulta paginada propia con evidencia central acotada y respaldo durable. */
public interface ListOwnReservationsUseCase {
    /**
     * @param ownerId identidad obtenida del usuario autenticado, nunca de la consulta HTTP
     * @param page página desde cero, sin incluir el pendiente
     * @param size tamaño entre 1 y 100
     * @return pendiente separado, historial propio y límites de verificación
     * @throws com.prog2.appointment.reservation.domain.exception.ReservationStorageException si la
     *     persistencia no permite obtener un resultado verificable
     */
    OwnReservations list(long ownerId, int page, int size);
}
