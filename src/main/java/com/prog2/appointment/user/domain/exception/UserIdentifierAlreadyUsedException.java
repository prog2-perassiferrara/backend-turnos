package com.prog2.appointment.user.domain.exception;

/** Conflicto de identificador sin copiar el login/email ni el mensaje SQL. */
public final class UserIdentifierAlreadyUsedException extends RuntimeException {
    public UserIdentifierAlreadyUsedException() {
        super("User identifier already used");
    }
}
