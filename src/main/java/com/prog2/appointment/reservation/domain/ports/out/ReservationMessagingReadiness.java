package com.prog2.appointment.reservation.domain.ports.out;

/**
 * Disponibilidad técnica del canal de acciones, sin inferir publicación por la cantidad de
 * mensajes.
 */
public interface ReservationMessagingReadiness {
    /** Solicita preparación acotada en segundo plano; no espera al broker. */
    void requestPreparation();

    /** Exige consumidor/metadata preparados antes de un nuevo hold. */
    void requireReady();
}
