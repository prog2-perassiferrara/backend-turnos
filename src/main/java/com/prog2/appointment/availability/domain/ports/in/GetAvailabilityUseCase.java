package com.prog2.appointment.availability.domain.ports.in;

import java.time.LocalDate;
import com.prog2.appointment.availability.domain.exception.AvailabilityGatewayException;
import com.prog2.appointment.availability.domain.model.Availability;

/** Consulta de CU-03 independiente del transporte y de la sesión técnica. */
public interface GetAvailabilityUseCase {
    /**
     * @param professionalId identificador positivo de cátedra
     * @param date fecha de atención desde hoy, según la zona configurada
     * @return horarios libres ordenados, posiblemente vacíos; nunca crea una reserva
     * @throws IllegalArgumentException si la entrada no cumple la política de consulta
     * @throws AvailabilityGatewayException si no puede obtener datos necesarios de un proveedor
     */
    Availability get(long professionalId, LocalDate date);
}
