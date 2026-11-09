package com.prog2.appointment.reservation.domain.ports.out;

import com.prog2.appointment.reservation.domain.model.ReservationSelection;
import com.prog2.appointment.reservation.domain.model.ProfessionalName;

/** Revalidación de agenda vigente y posición ofrecida; no crea un hold. */
public interface ReservableSlotGateway {
    /**
     * @param selection profesional/fecha/inicio que deben seguir siendo reservables
     * @return nombre completo de la misma lectura vigente, o null si no está disponible
     */
    ProfessionalName verify(ReservationSelection selection);
}
