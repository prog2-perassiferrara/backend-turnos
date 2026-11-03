package com.prog2.appointment.integration.domain.ports.in;

import com.prog2.appointment.integration.domain.model.TechnicalAccess;

/** Acceso interno a la sesión central; no representa una API pública ni un usuario de KMP. */
public interface TechnicalAccessUseCase {
    /**
     * @return sesión adquirida al arrancar, sin HTTP ni renovación por esta lectura
     * @throws IllegalStateException si el arranque no obtuvo acceso, venció o cátedra lo rechazó; requiere reinicio
     */
    TechnicalAccess getAccess();

    /**
     * Invalida la sesión actual si cátedra rechazó su token.
     * No repite la operación de negocio ni adquiere otro token automáticamente.
     * @param rejectedToken token exacto utilizado en la operación rechazada
     */
    void rejectAccess(String rejectedToken);
}
