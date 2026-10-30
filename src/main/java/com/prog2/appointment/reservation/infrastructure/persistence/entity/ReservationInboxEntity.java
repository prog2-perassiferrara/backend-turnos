package com.prog2.appointment.reservation.infrastructure.persistence.entity;

import java.time.Instant;
import com.prog2.appointment.reservation.domain.model.ReservationDelivery;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Recibo durable y contenido original, también para mensajes rechazados o aún no correlacionados.
 */
@Entity
@Table(name = "reservation_inbox")
public class ReservationInboxEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 249, nullable = false)
    private String topic;

    @Column(name = "partition_number", nullable = false)
    private int partition;

    @Column(name = "record_offset", nullable = false)
    private long offset;

    @Column(name = "message_key", length = 255)
    private String key;

    @Column(name = "event_id", length = 255)
    private String eventId;

    @Column(name = "process_id", length = 36)
    private String processId;

    @Column(length = 64, nullable = false)
    private String fingerprint;

    @Column(columnDefinition = "mediumtext", nullable = false)
    private String payload;

    @Column(length = 32, nullable = false)
    private String outcome;

    @Column(length = 64)
    private String rejection;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected ReservationInboxEntity() {}

    public ReservationInboxEntity(
            ReservationDelivery delivery, String acceptedEventId, Instant now) {
        topic = delivery.origin().topic();
        partition = delivery.origin().partition();
        offset = delivery.origin().offset();
        String original = delivery.origin().key();
        key = original == null ? null : original.substring(0, Math.min(255, original.length()));
        eventId = acceptedEventId;
        fingerprint = delivery.fingerprint();
        payload = delivery.payload();
        receivedAt = now;
        outcome = "REJECTED";
        rejection = delivery.rejection();
    }

    public void classify(String result, String reason, String localId) {
        outcome = result;
        rejection = reason;
        processId = localId;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public String getOutcome() {
        return outcome;
    }
}
