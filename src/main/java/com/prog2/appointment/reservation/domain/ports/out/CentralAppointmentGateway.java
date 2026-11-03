package com.prog2.appointment.reservation.domain.ports.out;


import com.prog2.appointment.reservation.domain.model.CentralAppointment;
import com.prog2.appointment.reservation.domain.model.ReservationProcess;

import java.util.Optional;

/** Lectura REST acotada de evidencia; no expone reservas de otros pacientes. */
public interface CentralAppointmentGateway {
    /**
     * @param process proceso propio con identificadores conocidos
     * @return coincidencia exacta tras un recorrido paginado completo y acotado, o ausencia
     *     inconclusa; nunca prueba liberación
     * @throws com.prog2.appointment.reservation.domain.exception.ReservationGatewayException ante
     *     proveedor inaccesible
     */
    Optional<CentralAppointment> find(ReservationProcess process);

}
