package com.prog2.appointment.reservation.infrastructure.persistence.entity;

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
    private String id;

    @Column(name = "process_id", length = 36)
    private String processId;

    @Column(name = "group_id", length = 255)
    private String groupId;

    @Column(name = "central_process_id", length = 255)
    private String centralProcessId;

    @Column(name = "hold_id", length = 255)
    private String holdId;

    @Column(name = "request_event_id", length = 255)
    private String requestEventId;

    @Column(name = "phone_number", length = 16)
    private String phoneNumber;

    @Column(name = "occurred_at")
    private Instant occurredAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "cycle_attempt")
    private int cycleAttempt;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "last_failure", length = 64)
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

    public String getId() {
        return id;
    }

    public String getProcessId() {
        return processId;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getCentralProcessId() {
        return centralProcessId;
    }

    public String getHoldId() {
        return holdId;
    }

    public String getRequestEventId() {
        return requestEventId;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public int getCycleAttempt() {
        return cycleAttempt;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getLastFailure() {
        return lastFailure;
    }

    public Long getVersion() {
        return version;
    }
}
