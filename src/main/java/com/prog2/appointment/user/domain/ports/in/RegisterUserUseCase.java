package com.prog2.appointment.user.domain.ports.in;

import com.prog2.appointment.user.domain.model.RegistrationData;

/** Registro local de usuarios finales, independiente de los proveedores técnicos externos. */
public interface RegisterUserUseCase {
    /**
     * Confirma una cuenta activa común antes de devolver el control. El cliente ejecuta luego CU-02.
     * @param data perfil validado; los campos internos los administra el backend
     * @param password contraseña validada, sin normalizar ni truncar; no se conserva en texto plano
     * @throws com.prog2.appointment.user.domain.exception.UserIdentifierAlreadyUsedException si un identificador pertenece a otra cuenta
     * @throws com.prog2.appointment.user.domain.exception.UserAccountUnavailableException si no se puede confirmar el alta; no demuestra rollback
     * @throws com.prog2.appointment.user.domain.exception.UserSecurityException si falla el hashing
     */
    void register(RegistrationData data, String password);
}
