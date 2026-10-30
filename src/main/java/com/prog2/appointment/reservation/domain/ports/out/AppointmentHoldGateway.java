package com.prog2.appointment.reservation.domain.ports.out;

import java.util.Optional;
import com.prog2.appointment.reservation.domain.model.HoldDetails;
import com.prog2.appointment.reservation.domain.model.HoldOccupancy;
import com.prog2.appointment.reservation.domain.model.ReservationSelection;
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
     * Consulta fresca después de una creación incierta; no filtra HELD por el reloj local.
     *
     * @param selection profesional, fecha y hora de la posición
     * @return ocupación observada o vacío; no implica ausencia histórica del hold previo
     * @throws com.prog2.appointment.reservation.domain.exception.ReservationGatewayException si no
     *     se puede verificar la consulta
     */
    Optional<HoldOccupancy> findOccupancy(ReservationSelection selection);

    /**
     * @param process hold conocido e intención de confirmar guardada
     * @return aceptación inicial validada
     */
    HoldDetails confirm(ReservationProcess process);
}
