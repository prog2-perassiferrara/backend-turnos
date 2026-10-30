package com.prog2.appointment.availability.application.exception;

import com.prog2.appointment.availability.domain.enums.AvailabilityFailure;

/** Rechazo de la consulta por sus reglas de entrada o condición del profesional. */
public final class AvailabilityException extends IllegalArgumentException {
    private final AvailabilityFailure failure;

    public AvailabilityException(AvailabilityFailure failure) {
        super(failure.name());
        this.failure = failure;
    }

    /** @return motivo de aplicación traducible en la frontera HTTP */
    public AvailabilityFailure failure() {
        return failure;
    }
}
