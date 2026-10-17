package com.prog2.appointment.integration.domain.ports.in;

import com.prog2.appointment.integration.domain.model.TechnicalAccess;

/** Acceso interno a la sesión central; no representa una API pública ni un usuario de KMP. */
public interface TechnicalAccessUseCase {
    /**
     * @return sesión utilizable sin consultar al proveedor en cada lectura
     * @throws IllegalStateException si no hay sesión; solicita recuperación sin esperar su resultado
     */
    TechnicalAccess getAccess();

    /**
     * Invalida el token que cátedra rechazó sin borrar otra sesión más reciente.
     * No repite la operación de negocio ni inicia otro login central.
     * @param rejectedToken token exacto utilizado en la operación rechazada
     */
    void rejectAccess(String rejectedToken);
}
