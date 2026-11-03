package com.prog2.appointment.reservation.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import com.prog2.appointment.reservation.infrastructure.persistence.entity.ProcessedReservationEventEntity;

/** Acceso SQL a las claves procesadas; no conserva payload ni metadata del transporte. */
public interface JpaProcessedReservationEventRepository
        extends JpaRepository<ProcessedReservationEventEntity, String> {
    /** INSERT estricto: dos transacciones nunca pueden acreditar el mismo eventId. */
    @Modifying
    @Query(value = "INSERT INTO reservation_processed_event (event_id) VALUES (:eventId)", nativeQuery = true)
    void markProcessed(String eventId);
}
