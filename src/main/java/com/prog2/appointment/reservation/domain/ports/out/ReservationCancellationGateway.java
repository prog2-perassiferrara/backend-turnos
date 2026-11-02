package com.prog2.appointment.reservation.domain.ports.out;

import com.prog2.appointment.reservation.domain.model.CancellationEvidence;
import com.prog2.appointment.reservation.domain.model.ReservationProcess;

/** Efecto REST de cancelación; no adquiere otra identidad ni repite el POST por su cuenta. */
public interface ReservationCancellationGateway {
    /**
     * @param process reserva propia CONFIRMED con intención e identificadores conservados
     * @return evidencia central de cancelación correlacionada
     * @throws com.prog2.appointment.reservation.domain.exception.ReservationGatewayException
     *     ante fase incompatible (sin HTTP), rechazo, falta de acceso o resultado incierto
     */
    CancellationEvidence cancel(ReservationProcess process);
}
