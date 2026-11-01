package com.prog2.appointment.reservation.domain.ports.out;

import com.prog2.appointment.reservation.domain.model.CentralAppointmentsBatch;

import com.prog2.appointment.reservation.domain.model.CentralAppointment;
import com.prog2.appointment.reservation.domain.model.ReservationProcess;

import java.util.Optional;

/** Lectura REST acotada de evidencia; no expone reservas de otros pacientes. */
public interface CentralAppointmentGateway {
    /**
     * @param process proceso propio con identificadores conocidos
     * @return coincidencia exacta o ausencia inconclusa; nunca prueba liberación
     * @throws com.prog2.appointment.reservation.domain.exception.ReservationGatewayException ante
     *     proveedor inaccesible o respuesta inconsistente
     */
    Optional<CentralAppointment> find(ReservationProcess process);

    /**
     * @param ownerId identidad propia desde la que se deriva el paciente estable
     * @return evidencia validada y alcance; un recorrido parcial no prueba ausencia
     */
    CentralAppointmentsBatch listOwn(long ownerId);
}
