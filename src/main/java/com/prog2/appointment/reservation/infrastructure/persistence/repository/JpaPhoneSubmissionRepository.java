package com.prog2.appointment.reservation.infrastructure.persistence.repository;

import com.prog2.appointment.reservation.infrastructure.persistence.entity.PhoneSubmissionEntity;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Selección de outbox acotada e historial con orden estable aun con timestamps iguales. */
public interface JpaPhoneSubmissionRepository extends JpaRepository<PhoneSubmissionEntity, String> {
    @Query(
            value =
                    "SELECT * FROM reservation_phone_submission WHERE process_id = :id ORDER BY"
                            + " sequence_id DESC LIMIT 1",
            nativeQuery = true)
    Optional<PhoneSubmissionEntity> latest(@Param("id") String processId);

    @Query(
            value = "SELECT * FROM reservation_phone_submission WHERE active_process_id = :id",
            nativeQuery = true)
    Optional<PhoneSubmissionEntity> active(@Param("id") String processId);

    @Query(
            "select p.id from PhoneSubmissionEntity p where p.closedAt is null and p.publishedAt is"
                    + " null and p.nextAttemptAt <= :now order by p.nextAttemptAt, p.id")
    List<String> due(@Param("now") Instant now, Pageable pageable);
}
