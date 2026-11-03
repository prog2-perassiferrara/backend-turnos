package com.prog2.appointment.reservation.domain.ports.in;

import com.prog2.appointment.reservation.domain.model.ReservationAction;

/** Entrada de eventos: el retorno normal acredita almacenamiento confirmado, no el ACK de Kafka. */
public interface ReceiveReservationActionUseCase {
    /**
     * @param action hecho externo validado
     * @throws com.prog2.appointment.reservation.domain.exception.ReservationStorageException sin
     *     confirmar si la escritura no es verificable
     */
    void receive(ReservationAction action);
}
