package com.prog2.appointment.reservation.domain.ports.in;

import com.prog2.appointment.reservation.domain.model.ReservationProcess;
import com.prog2.appointment.reservation.domain.model.StartReservationRequest;

/** Entrada de inicio: una repetición propia nunca vuelve a ejecutar el POST central. */
public interface StartReservationUseCase {
    /**
     * @param ownerId sujeto autenticado positivo
     * @param request identidad estable y selección de la acción
     * @return proceso propio creado o reconocido, no necesariamente confirmado
     */
    StartResult start(long ownerId, StartReservationRequest request);

    /**
     * Resultado del caso de uso para distinguir creación y repetición sin otra consulta susceptible
     * a carreras.
     */
    record StartResult(ReservationProcess process, boolean replayed) {}
}
