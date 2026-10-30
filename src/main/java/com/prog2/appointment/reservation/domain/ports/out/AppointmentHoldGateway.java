package com.prog2.appointment.reservation.domain.ports.out;

import com.prog2.appointment.reservation.domain.model.HoldDetails;
import com.prog2.appointment.reservation.domain.model.ReservationProcess;

/**
 * Efectos REST: un intento por llamada; jamás recibe JWT del usuario ni implementa un login propio.
 */
public interface AppointmentHoldGateway {
    /**
     * @param process intención guardada
     * @return hold creado con IDs/plazo centrales
     */
    HoldDetails create(ReservationProcess process);

    /**
     * @param process hold conocido e intención de confirmar guardada
     * @return aceptación inicial validada
     */
    HoldDetails confirm(ReservationProcess process);
}
