package com.prog2.appointment.reservation.domain.ports.in;

import com.prog2.appointment.reservation.domain.model.ReservationStatus;
import com.prog2.appointment.reservation.domain.model.SubmitPhoneRequest;

import java.util.UUID;

/** Aceptación durable propia; no espera ni exige disponibilidad del broker. */
public interface SubmitReservationPhoneUseCase {
    /**
     * @param ownerId usuario autenticado
     * @param processId proceso local propio
     * @param request identidad estable y teléfono
     * @return avance conservado y distinción entre inserción y repetición
     */
    Result submit(long ownerId, UUID processId, SubmitPhoneRequest request);

    record Result(ReservationStatus status, boolean replayed) {}
}
