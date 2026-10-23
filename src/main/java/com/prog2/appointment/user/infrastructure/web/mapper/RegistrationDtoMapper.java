package com.prog2.appointment.user.infrastructure.web.mapper;

import org.springframework.stereotype.Component;
import com.prog2.appointment.user.domain.model.RegistrationData;
import com.prog2.appointment.user.infrastructure.web.dto.RegistrationRequest;

/** Separa perfil y contraseña; solo copia campos de perfil admitidos por el contrato público. */
@Component
public final class RegistrationDtoMapper {
    public RegistrationData toDomain(RegistrationRequest request) {
        return new RegistrationData(request.login(), request.firstName(), request.lastName(),
                request.email(), request.imageUrl(), request.langKey());
    }
}
