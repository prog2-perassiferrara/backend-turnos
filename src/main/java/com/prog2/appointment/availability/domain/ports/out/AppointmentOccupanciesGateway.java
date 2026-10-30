package com.prog2.appointment.availability.domain.ports.out;

import java.time.LocalDate;
import java.util.List;
import com.prog2.appointment.availability.domain.exception.AvailabilityGatewayException;
import com.prog2.appointment.availability.domain.model.AppointmentOccupancy;

/** Puerto de ocupaciones del profesional y día indicados; no expone credenciales o pacientes. */
public interface AppointmentOccupanciesGateway {
    /**
     * @param professionalId identificador de cátedra
     * @param date único día del rango inclusivo
     * @return ocupaciones completas del día, posiblemente vacías; sin escrituras
     * @throws AvailabilityGatewayException por sesión no utilizable, fallo o respuesta incompatible
     */
    List<AppointmentOccupancy> get(long professionalId, LocalDate date);
}
