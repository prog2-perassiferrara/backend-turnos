package com.prog2.appointment.reservation.infrastructure.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.List;
import java.time.Instant;
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

    @Query(value = "SELECT id FROM reservation_process WHERE cancellation_status = 'PENDING'"
            + " AND cancellation_attempts < 3 AND cancellation_next_attempt_at <= :now"
            + " ORDER BY cancellation_next_attempt_at, id", nativeQuery = true)
    List<String> dueCancellations(@Param("now") Instant now, Pageable page);

    Optional<ReservationProcessEntity> findByCentralProcessId(String centralProcessId);

    @Query(value = "SELECT id FROM reservation_process WHERE (closed_at IS NULL OR cancellation_status = 'PENDING')"
            + " AND (cancellation_status IS NULL OR cancellation_status <> 'PENDING' OR cancellation_attempts >= 3)"
            + " AND ((recovery_next_attempt_at IS NULL AND updated_at <= :initialCutoff)"
            + " OR recovery_next_attempt_at <= :now)"
            + " ORDER BY COALESCE(recovery_next_attempt_at, updated_at), id", nativeQuery = true)
    List<String> dueRecovery(@Param("now") Instant now, @Param("initialCutoff") Instant initialCutoff, Pageable page);

    @Query(
            value =
                    "SELECT * FROM reservation_process WHERE owner_id = :owner AND active_owner_id"
                            + " IS NULL ORDER BY appointment_date DESC, start_time DESC, id ASC",
            countQuery =
                    "SELECT COUNT(*) FROM reservation_process WHERE owner_id = :owner AND"
                            + " active_owner_id IS NULL",
            nativeQuery = true)
    Page<ReservationProcessEntity> history(@Param("owner") long ownerId, Pageable pageable);
}
