package com.prog2.appointment.user.domain.ports.out;

import java.util.Optional;
import com.prog2.appointment.user.domain.model.RegistrationData;
import com.prog2.appointment.user.domain.model.UserAccount;
import com.prog2.appointment.user.domain.model.UserProfile;

/** Repository: contrato de persistencia de cuentas sin entidades JPA ni excepciones SQL. */
public interface UserAccountRepository {
    /**
     * Guarda cuenta activa, identificadores y ROLE_USER en una sola unidad confirmada.
     * @param data perfil con login/email normalizados
     * @param passwordHash representación protegida, nunca contraseña en texto plano
     * @throws com.prog2.appointment.user.domain.exception.UserIdentifierAlreadyUsedException ante colisión, incluso cruzada o concurrente
     * @throws com.prog2.appointment.user.domain.exception.UserAccountUnavailableException ante fallo propio, incluido un commit incierto
     */
    void createActiveUser(RegistrationData data, String passwordHash);

    /**
     * @param normalizedIdentifier login/email normalizado; identifica como máximo una cuenta
     * @return cuenta con autoridades cargadas, o vacío si no existe
     * @throws com.prog2.appointment.user.domain.exception.UserAccountUnavailableException si falla la lectura
     */
    Optional<UserAccount> findByIdentifier(String normalizedIdentifier);
    /** @return perfil mínimo por ID, sin hash ni colecciones de autoridades */
    Optional<UserProfile> findProfileById(long id);
}
