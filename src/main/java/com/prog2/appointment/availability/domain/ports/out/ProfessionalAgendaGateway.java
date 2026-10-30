package com.prog2.appointment.availability.domain.ports.out;

import java.util.Optional;
import com.prog2.appointment.availability.domain.exception.AvailabilityGatewayException;
import com.prog2.appointment.availability.domain.model.ProfessionalAgenda;

/** Puerto de lectura de la agenda vigente, adaptado al contrato propio de disponibilidad. */
public interface ProfessionalAgendaGateway {
    /**
     * @param professionalId identificador solicitado
     * @return agenda completa de una misma lectura vigente; vacío solo por ausencia contractual
     * @throws AvailabilityGatewayException si no hay vigencia o no se puede verificar la respuesta
     */
    Optional<ProfessionalAgenda> find(long professionalId);
}
