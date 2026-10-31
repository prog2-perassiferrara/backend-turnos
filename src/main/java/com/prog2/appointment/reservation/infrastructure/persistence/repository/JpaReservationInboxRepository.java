package com.prog2.appointment.reservation.infrastructure.persistence.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.prog2.appointment.reservation.infrastructure.persistence.entity.ReservationInboxEntity;

/** Dos identidades independientes: entrega de broker y eventId funcional validado. */
public interface JpaReservationInboxRepository extends JpaRepository<ReservationInboxEntity, Long> {
    Optional<ReservationInboxEntity> findByEventId(String eventId);

    @Query(
            "select r from ReservationInboxEntity r where r.topic=:topic and r.partition=:partition"
                    + " and r.offset=:offset")
    Optional<ReservationInboxEntity> findDelivery(
            @Param("topic") String topic,
            @Param("partition") int partition,
            @Param("offset") long offset);
}
