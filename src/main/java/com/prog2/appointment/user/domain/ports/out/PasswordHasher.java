package com.prog2.appointment.user.domain.ports.out;

/** Protege y comprueba contraseñas completas; el algoritmo pertenece al adaptador de infraestructura. */
public interface PasswordHasher {
    /**
     * @param password contraseña previamente validada de al menos 6 caracteres y hasta 72 bytes UTF-8
     * @return hash con sal aleatoria, sin conservar el valor original
     * @throws com.prog2.appointment.user.domain.exception.UserSecurityException si no se puede proteger la contraseña
     */
    String encode(String password);

    /**
     * @param password contraseña validada, sin truncar ni normalizar
     * @param encodedPassword hash persistido
     * @return true solo si coincide el valor completo
     * @throws com.prog2.appointment.user.domain.exception.UserSecurityException si el hash no se puede verificar
     */
    boolean matches(String password, String encodedPassword);
}
