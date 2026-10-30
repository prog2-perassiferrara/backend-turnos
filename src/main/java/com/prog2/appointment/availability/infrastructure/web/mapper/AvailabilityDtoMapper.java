package com.prog2.appointment.availability.infrastructure.web.mapper;

import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;
import com.prog2.appointment.availability.domain.model.Availability;
import com.prog2.appointment.availability.infrastructure.web.dto.AvailabilityResponse;

/** Adapta la salida a HH:mm:ss explícito; no recalcula reglas ni desplaza horas de atención. */
@Component
public final class AvailabilityDtoMapper {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** @return respuesta de una consulta existente; nunca entidades o modelos de sesión */
    public AvailabilityResponse toResponse(Availability availability) {
        return new AvailabilityResponse(availability.professionalId(), availability.date(),
                availability.timeZone().getId(), availability.availableStartTimes().stream().map(TIME::format).toList());
    }
}
