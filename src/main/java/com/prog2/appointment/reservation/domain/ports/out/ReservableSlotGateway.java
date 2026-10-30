package com.prog2.appointment.reservation.domain.ports.out;

import com.prog2.appointment.reservation.domain.model.ReservationSelection;

/** Revalidación de agenda vigente y posición ofrecida; no crea un hold. */
public interface ReservableSlotGateway {
    /**
     * @param selection profesional/fecha/inicio que deben seguir siendo reservables
     */
    void verify(ReservationSelection selection);
}
