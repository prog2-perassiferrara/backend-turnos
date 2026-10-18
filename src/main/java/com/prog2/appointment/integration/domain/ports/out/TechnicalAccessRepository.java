package com.prog2.appointment.integration.domain.ports.out;

import java.util.Optional;
import com.prog2.appointment.integration.domain.model.TechnicalAccess;

/** Repository de sesión completa; exige publicación e invalidación atómicas. */
public interface TechnicalAccessRepository extends AutoCloseable {
    /** @return última sesión publicada, si existe; la aplicación comprueba vigencia al usarla */
    Optional<TechnicalAccess> current();

    /**
     * @param access sesión validada por aplicación
     * @return false si el token fue rechazado, venció o el repositorio ya fue cerrado
     */
    boolean publishIfAccepted(TechnicalAccess access);

    /** @param token token rechazado; invalida solo la sesión correspondiente, no una más reciente */
    void rejectToken(String token);

    /**
     * @param token token candidato a reutilizar
     * @return si ya fue rechazado durante este proceso
     */
    boolean isRejected(String token);

    /** Descarta acceso y prohíbe nuevas publicaciones durante el cierre del backend. */
    @Override
    void close();
}
