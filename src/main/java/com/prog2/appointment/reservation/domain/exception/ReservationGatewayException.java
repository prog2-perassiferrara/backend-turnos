package com.prog2.appointment.reservation.domain.exception;

import com.prog2.appointment.reservation.domain.enums.ReservationFailure;

/** Fallo de un puerto; distingue rechazo documentado de efecto remoto potencial. */
public final class ReservationGatewayException extends RuntimeException {
    private final ReservationFailure failure;
    private final boolean attempted;
    private final boolean definitive;

    /**
     * @param failure motivo seguro
     * @param attempted si se invocó el transporte con efectos, aunque no se conozca su resultado
     * @param definitive si el contrato acredita rechazo sin creación o vencimiento
     */
    public ReservationGatewayException(
            ReservationFailure failure, boolean attempted, boolean definitive) {
        super("Reservation dependency could not complete the operation");
        this.failure = failure;
        this.attempted = attempted;
        this.definitive = definitive;
    }

    public ReservationFailure failure() {
        return failure;
    }

    public boolean attempted() {
        return attempted;
    }

    public boolean definitive() {
        return definitive;
    }
}
