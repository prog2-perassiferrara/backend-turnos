package com.prog2.appointment.user.domain.model;

import java.util.Set;

/** Datos mínimos para autenticar una cuenta local; nunca se serializan como respuesta HTTP. */
public record UserAccount(long id, String passwordHash, boolean activated, Set<String> authorities) {
    public UserAccount {
        authorities = Set.copyOf(authorities);
    }

    @Override
    public String toString() {
        return "UserAccount[REDACTED]";
    }
}
