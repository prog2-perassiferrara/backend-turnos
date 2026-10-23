package com.prog2.appointment.user.domain.ports.in;

import com.prog2.appointment.user.domain.model.UserToken;

/** Autenticación común al login independiente y al login automático posterior al registro. */
public interface AuthenticateUserUseCase {
    /**
     * @param identifier login o email validado; no distingue mayúsculas
     * @param password contraseña validada que se comprueba sin modificarla
     * @param rememberMe true para 30 días; false para 24 horas desde la emisión
     * @return JWT de usuario, sin modificar cuenta ni permisos y sin renovar otros tokens
     * @throws RuntimeException si las credenciales se rechazan; la aplicación define el rechazo concreto
     * @throws com.prog2.appointment.user.domain.exception.UserAccountUnavailableException si no se pueden comprobar los datos
     * @throws com.prog2.appointment.user.domain.exception.UserSecurityException si falla la comprobación técnica o firma
     */
    UserToken authenticate(String identifier, String password, boolean rememberMe);
}
