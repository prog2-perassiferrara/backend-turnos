package com.prog2.appointment.user.domain.ports.in;

import java.util.Optional;
import com.prog2.appointment.user.domain.model.UserProfile;

/** Consulta Java interna por ID; no representa un endpoint público ni devuelve el modelo de autenticación. */
public interface GetUserProfileUseCase {
    /**
     * @param id identificador de la cuenta
     * @return perfil existente, posiblemente inactivo; el consumidor decide si permite operar
     */
    Optional<UserProfile> get(long id);
}
