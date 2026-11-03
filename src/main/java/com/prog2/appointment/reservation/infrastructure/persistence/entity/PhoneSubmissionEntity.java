package com.prog2.appointment.reservation.infrastructure.persistence.entity;

import lombok.Getter;
import com.prog2.appointment.reservation.domain.model.PhoneSubmission;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/** Outbox e historial propios; el teléfono no tiene representación de texto ni exposición web. */
@Entity
@Table(name = "reservation_phone_submission")
public class PhoneSubmissionEntity {
    @Id
    @Column(name = "id", length = 36)
    @Getter
    private String id;

    @Column(name = "process_id", length = 36)
    @Getter
    private String processId;

    @Column(name = "group_id", length = 255)
    @Getter
    private String groupId;

    @Column(name = "central_process_id", length = 255)
    @Getter
    private String centralProcessId;

    @Column(name = "hold_id", length = 255)
    @Getter
    private String holdId;

    @Column(name = "request_event_id", length = 255)
    @Getter
    private String requestEventId;

    @Column(name = "phone_number", length = 16)
    @Getter
    private String phoneNumber;

    @Column(name = "occurred_at")
    @Getter
    private Instant occurredAt;

    @Column(name = "expires_at")
    @Getter
    private Instant expiresAt;

    @Column(name = "published_at")
    @Getter
    private Instant publishedAt;

    @Column(name = "closed_at")
    @Getter
    private Instant closedAt;

    @Column(name = "cycle_attempt")
    @Getter
    private int cycleAttempt;

    @Column(name = "next_attempt_at")
    @Getter
    private Instant nextAttemptAt;

    @Column(name = "last_failure", length = 64)
    @Getter
    private String lastFailure;

    @Version
    @Column(name = "version")
    private Long version;

    protected PhoneSubmissionEntity() {}

    public PhoneSubmissionEntity(PhoneSubmission phone) {
        id = phone.id().toString();
        processId = phone.processId().toString();
        groupId = phone.event().groupId();
        centralProcessId = phone.event().centralProcessId();
        holdId = phone.event().holdId();
        requestEventId = phone.event().requestEventId();
        phoneNumber = phone.event().phoneNumber();
        occurredAt = phone.event().occurredAt();
        expiresAt = phone.expiresAt();
        apply(phone);
    }

    /** Actualiza únicamente progreso; el evento confirmado permanece inmutable. */
    public void apply(PhoneSubmission phone) {
        if (!id.equals(phone.id().toString()))
            throw new IllegalArgumentException("Phone identity mismatch");
        publishedAt = phone.publishedAt();
        closedAt = phone.closedAt();
        cycleAttempt = phone.cycleAttempt();
        nextAttemptAt = phone.nextAttemptAt();
        lastFailure = phone.lastFailure();
    }

}
