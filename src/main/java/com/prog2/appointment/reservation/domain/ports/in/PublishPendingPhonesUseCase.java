package com.prog2.appointment.reservation.domain.ports.in;

/** Recuperación acotada de respuestas aceptadas, independiente de KMP. */
public interface PublishPendingPhonesUseCase {
    /** Publica un lote limitado, preservando ID/contenido y vencimiento. */
    void publishPending();
}
