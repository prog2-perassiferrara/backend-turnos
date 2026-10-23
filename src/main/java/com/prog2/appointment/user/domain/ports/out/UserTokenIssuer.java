package com.prog2.appointment.user.domain.ports.out;

import java.time.Duration;
import java.util.Set;
import com.prog2.appointment.user.domain.model.UserToken;

/** Emite una credencial de usuario con identidad y permisos ya comprobados por la aplicación. */
public interface UserTokenIssuer {
    /**
     * @param userId identidad estable asignada por Turnos
     * @param authorities permisos administrados por el backend, no elegidos por el cliente
     * @param lifetime duración desde la emisión, decidida por el caso de uso
     * @return JWT de usuario compatible con ambos backends
     * @throws com.prog2.appointment.user.domain.exception.UserSecurityException si falla la firma
     */
    UserToken issue(long userId, Set<String> authorities, Duration lifetime);
}
