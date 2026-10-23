package com.prog2.appointment.user.domain.exception;

/** Fallo técnico de hashing o firma sin datos sensibles ni causas externas encadenadas. */
public final class UserSecurityException extends RuntimeException {
    public UserSecurityException() {
        super("User credential operation unavailable");
    }
}
