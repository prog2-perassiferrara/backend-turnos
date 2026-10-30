package com.prog2.appointment.reservation.infrastructure.persistence.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.prog2.appointment.reservation.infrastructure.persistence.entity.ReservationProcessEntity;

/** Consultas locales; active_owner_id usa la misma condición que protege el índice único. */
public interface JpaReservationProcessRepository
        extends JpaRepository<ReservationProcessEntity, String> {
    @Query(
            value = "SELECT * FROM reservation_process WHERE active_owner_id = :owner",
            nativeQuery = true)
    Optional<ReservationProcessEntity> findPending(@Param("owner") long owner);

    Optional<ReservationProcessEntity> findByCentralProcessId(String centralProcessId);
}
