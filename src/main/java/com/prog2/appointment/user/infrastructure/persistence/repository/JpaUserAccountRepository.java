package com.prog2.appointment.user.infrastructure.persistence.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.prog2.appointment.user.infrastructure.persistence.entity.UserAccountEntity;

/** Consultas JPA locales; la clave global garantiza que la búsqueda no seleccione dos cuentas. */
public interface JpaUserAccountRepository extends JpaRepository<UserAccountEntity, Long> {
    @Query("select u from UserAccountEntity u join u.identifiers identifier where identifier = :value")
    Optional<UserAccountEntity> findByIdentifier(@Param("value") String value);
}
