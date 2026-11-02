package com.prog2.appointment.reservation.domain.ports.in;

/** Recuperación autónoma de intenciones persistidas; no crea otra reserva ni requiere KMP. */
public interface RecoverReservationProcessesUseCase {
    /** Procesa una pasada acotada de recibos asociables y procesos elegibles. */
    void recoverDue();
}
