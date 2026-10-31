package com.prog2.appointment.reservation.infrastructure.persistence.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import com.prog2.appointment.reservation.domain.model.ReservationProcess;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Estado JPA propio, sin relaciones navegables hacia user ni serialización HTTP. */
@Entity
@Table(name = "reservation_process")
public class ReservationProcessEntity {
    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "owner_id")
    private long ownerId;

    @Column(name = "professional_id")
    private long professionalId;

    @Column(name = "appointment_date")
    private LocalDate appointmentDate;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "patient_first_name", length = 100)
    private String patientFirstName;

    @Column(name = "patient_last_name", length = 100)
    private String patientLastName;

    @Column(name = "phase", length = 40)
    private String phase;

    @Column(name = "hold_id", length = 255)
    private String holdId;

    @Column(name = "central_process_id", length = 255)
    private String centralProcessId;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "request_event_id", length = 255)
    private String requestEventId;

    @Column(name = "reservation_id")
    private Long reservationId;

    @Column(name = "reason_code", length = 64)
    private String reasonCode;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "write_id", length = 36)
    private String writeId;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "abandonment_pending")
    private boolean abandonmentPending;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected ReservationProcessEntity() {}

    public ReservationProcessEntity(ReservationProcess process, Instant now) {
        id = process.id().toString();
        createdAt = now;
        apply(process, process.writeId(), now);
    }

    /** Copia únicamente un cambio de este agregado; la identidad/selección son inmutables. */
    public void apply(ReservationProcess process, UUID mutation, Instant now) {
        if (!id.equals(process.id().toString()) || ownerId != 0 && ownerId != process.ownerId()) {
            throw new IllegalArgumentException("Cannot change reservation identity");
        }
        ownerId = process.ownerId();
        professionalId = process.selection().professionalId();
        appointmentDate = process.selection().date();
        startTime = process.selection().startTime();
        patientFirstName = process.patient().firstName();
        patientLastName = process.patient().lastName();
        phase = process.phase().name();
        holdId = process.hold() == null ? null : process.hold().holdId();
        centralProcessId = process.hold() == null ? null : process.hold().centralProcessId();
        expiresAt = process.hold() == null ? null : process.hold().expiresAt();
        requestEventId = process.requestEventId();
        reservationId = process.reservationId();
        reasonCode = process.reasonCode();
        writeId = mutation.toString();
        closedAt = process.closedAt();
        abandonmentPending = process.abandonmentPending();
        updatedAt = now;
    }

    public String getId() {
        return id;
    }

    public long getOwnerId() {
        return ownerId;
    }

    public long getProfessionalId() {
        return professionalId;
    }

    public LocalDate getAppointmentDate() {
        return appointmentDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public String getPatientFirstName() {
        return patientFirstName;
    }

    public String getPatientLastName() {
        return patientLastName;
    }

    public String getPhase() {
        return phase;
    }

    public String getHoldId() {
        return holdId;
    }

    public String getCentralProcessId() {
        return centralProcessId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public String getRequestEventId() {
        return requestEventId;
    }

    public Long getReservationId() {
        return reservationId;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public Long getVersion() {
        return version;
    }

    public String getWriteId() {
        return writeId;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public boolean getAbandonmentPending() {
        return abandonmentPending;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
