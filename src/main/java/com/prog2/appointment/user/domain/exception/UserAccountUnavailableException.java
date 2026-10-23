package com.prog2.appointment.user.domain.exception;

/** Fallo de persistencia seguro; no afirma que una escritura incierta haya sido revertida. */
public final class UserAccountUnavailableException extends RuntimeException {
    public UserAccountUnavailableException() {
        super("User account operation unavailable");
    }
}
