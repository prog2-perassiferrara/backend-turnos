package com.prog2.appointment.user.infrastructure.persistence.service;

import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.prog2.appointment.user.domain.model.RegistrationData;
import com.prog2.appointment.user.domain.model.UserAccount;
import com.prog2.appointment.user.domain.model.UserProfile;
import com.prog2.appointment.user.infrastructure.persistence.entity.UserAccountEntity;
import com.prog2.appointment.user.infrastructure.persistence.mapper.UserAccountMapper;
import com.prog2.appointment.user.infrastructure.persistence.repository.JpaUserAccountRepository;

/**
 * Límite transaccional invocado por otro bean a través del proxy Spring.
 * El proxy confirma antes de devolver al adaptador, que puede traducir también un fallo de commit.
 */
@Component
public class TransactionalUserAccountStore {
    private final JpaUserAccountRepository repository;
    private final UserAccountMapper mapper;
    private final Clock clock;

    public TransactionalUserAccountStore(JpaUserAccountRepository repository, UserAccountMapper mapper,
            Clock clock) {
        this.repository = repository;
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * Cuenta y colecciones se confirman juntas; una colisión revierte toda esta transacción.
     * @param data perfil normalizado, sin campos internos controlados por el cliente
     * @param passwordHash hash calculado antes de abrir la transacción
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
    public void create(RegistrationData data, String passwordHash) {
        repository.saveAndFlush(new UserAccountEntity(data, passwordHash, clock.instant()));
    }

    /**
     * @param value login/email normalizado
     * @return modelo sin entidades/colecciones pendientes de cargar al cerrar la lectura
     */
    @Transactional(readOnly = true)
    public Optional<UserAccount> findByIdentifier(String value) {
        return repository.findByIdentifier(value).map(mapper::toDomain);
    }
    /** @return perfil materializado dentro de la lectura transaccional */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<UserProfile> findProfileById(long id) {
        return repository.findById(id).map(mapper::toProfile);
    }
}
