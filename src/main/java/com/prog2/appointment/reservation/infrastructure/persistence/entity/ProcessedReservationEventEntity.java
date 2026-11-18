package com.prog2.appointment.reservation.infrastructure.persistence.entity;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Clave durable de idempotencia, confirmada junto con los efectos del evento. */
@Entity
@Table(name = "reservation_processed_event")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedReservationEventEntity {
    @Id
    @Column(name = "event_id", nullable = false, length = 255)
    private String eventId;

}
