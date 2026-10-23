package com.prog2.appointment.user.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;
import com.prog2.appointment.user.domain.model.UserAccount;
import com.prog2.appointment.user.infrastructure.persistence.entity.UserAccountEntity;

/** Mapea dentro de la transacción para no exponer colecciones LAZY al dominio. */
@Component
public final class UserAccountMapper {
    public UserAccount toDomain(UserAccountEntity entity) {
        return new UserAccount(entity.getId(), entity.getPasswordHash(),
                entity.isActivated(), entity.getAuthorities());
    }
}
