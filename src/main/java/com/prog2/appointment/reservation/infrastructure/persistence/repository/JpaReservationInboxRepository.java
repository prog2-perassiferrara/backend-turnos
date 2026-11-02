package com.prog2.appointment.reservation.infrastructure.persistence.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.prog2.appointment.reservation.infrastructure.persistence.entity.ReservationInboxEntity;

/** Dos identidades independientes: entrega de broker y eventId funcional validado. */
public interface JpaReservationInboxRepository extends JpaRepository<ReservationInboxEntity, Long> {
    Optional<ReservationInboxEntity> findByEventId(String eventId);

    @Query(value = "SELECT i.id FROM reservation_inbox i JOIN reservation_process p"
            + " ON p.central_process_id = i.message_key WHERE i.outcome = 'DEFERRED'"
            + " AND i.rejection = 'UNKNOWN_PROCESS' ORDER BY i.id", nativeQuery = true)
    List<Long> deferredKnown(Pageable page);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReservationInboxEntity r where r.id=:id")
    Optional<ReservationInboxEntity> lockedReceipt(@Param("id") long id);

    @Query(
            "select r from ReservationInboxEntity r where r.topic=:topic and r.partition=:partition"
                    + " and r.offset=:offset")
    Optional<ReservationInboxEntity> findDelivery(
            @Param("topic") String topic,
            @Param("partition") int partition,
            @Param("offset") long offset);
}
