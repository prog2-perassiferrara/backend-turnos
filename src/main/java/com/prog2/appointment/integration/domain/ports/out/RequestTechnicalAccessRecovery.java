package com.prog2.appointment.integration.domain.ports.out;

/** Puerto de ejecución: agrupa demandas de recuperación sin bloquear la solicitud. */
public interface RequestTechnicalAccessRecovery {
    /** Solicita un ciclo si no hay otro activo y terminó la espera; no encola operaciones de negocio. */
    void requestRecovery();
}
