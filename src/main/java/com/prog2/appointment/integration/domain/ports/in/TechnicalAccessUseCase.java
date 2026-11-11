package com.prog2.appointment.integration.domain.ports.in;

import com.prog2.appointment.integration.domain.model.TechnicalAccess;

/** Acceso interno a la sesión central; no representa una API pública ni un usuario de KMP. */
public interface TechnicalAccessUseCase {
    /**
     * Si falta acceso, puede solicitar un intento acotado en segundo plano. La operación que
     * necesita la sesión conserva su presupuesto y puede consultarla nuevamente más adelante.
     * @return sesión técnica vigente con configuración validada
     * @throws IllegalStateException si todavía no existe acceso utilizable
     */
    TechnicalAccess getAccess();

    /**
     * Invalida la sesión actual si cátedra rechazó su token.
     * Conserva el rechazo para que una adquisición posterior descarte ese mismo JWT.
     * @param rejectedToken token exacto utilizado en la operación rechazada
     */
    void rejectAccess(String rejectedToken);
}
