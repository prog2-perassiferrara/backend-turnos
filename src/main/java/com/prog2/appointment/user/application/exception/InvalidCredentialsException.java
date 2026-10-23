package com.prog2.appointment.user.application.exception;

/** Rechazo común a contraseña incorrecta, cuenta inexistente o cuenta no habilitada. */
public final class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Invalid credentials");
    }
}
