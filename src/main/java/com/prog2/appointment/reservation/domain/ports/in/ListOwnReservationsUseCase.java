package com.prog2.appointment.reservation.domain.ports.in;

import com.prog2.appointment.reservation.domain.model.OwnReservations;

/** Consulta paginada propia del estado durable local. */
public interface ListOwnReservationsUseCase {
    /**
     * @param ownerId identidad obtenida del usuario autenticado, nunca de la consulta HTTP
     * @param page página desde cero, sin incluir el pendiente
     * @param size tamaño entre 1 y 100
     * @return pendiente separado e historial propio
     * @throws com.prog2.appointment.reservation.domain.exception.ReservationStorageException si la
     *     persistencia no permite obtener el resultado
     */
    OwnReservations list(long ownerId, int page, int size);
}
