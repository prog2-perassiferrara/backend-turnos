package com.prog2.appointment.reservation.infrastructure.persistence.entity;

import lombok.Getter;
import java.time.Duration;
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
    @Getter
    private String id;

    @Column(name = "owner_id")
    @Getter
    private long ownerId;

    @Column(name = "professional_id")
    @Getter
    private long professionalId;

    @Column(name = "appointment_date")
    @Getter
    private LocalDate appointmentDate;

    @Column(name = "start_time")
    @Getter
    private LocalTime startTime;

    @Column(name = "patient_first_name", length = 100)
    @Getter
    private String patientFirstName;

    @Column(name = "patient_last_name", length = 100)
    @Getter
    private String patientLastName;

    @Column(name = "professional_first_name", length = 255)
    @Getter
    private String professionalFirstName;

    @Column(name = "professional_last_name", length = 255)
    @Getter
    private String professionalLastName;

    @Column(name = "phase", length = 40)
    @Getter
    private String phase;

    @Column(name = "hold_id", length = 255)
    @Getter
    private String holdId;

    @Column(name = "central_process_id", length = 255)
    @Getter
    private String centralProcessId;

    @Column(name = "expires_at")
    @Getter
    private Instant expiresAt;

    @Column(name = "request_event_id", length = 255)
    @Getter
    private String requestEventId;

    @Column(name = "reservation_id")
    @Getter
    private Long reservationId;

    @Column(name = "reason_code", length = 64)
    @Getter
    private String reasonCode;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "write_id", length = 36)
    @Getter
    private String writeId;

    @Column(name = "closed_at")
    @Getter
    private Instant closedAt;


    @Column(name = "cancellation_status", length = 16)
    @Getter
    private String cancellationStatus;

    @Column(name = "created_at")
    @Getter
    private Instant createdAt;

    @Column(name = "updated_at")
    @Getter
    private Instant updatedAt;

    @Column(name = "recovery_next_attempt_at")
    private Instant recoveryNextAttemptAt;

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
        professionalFirstName =
                process.professionalName() == null ? null : process.professionalName().firstName();
        professionalLastName =
                process.professionalName() == null ? null : process.professionalName().lastName();
        phase = process.phase().name();
        holdId = process.hold() == null ? null : process.hold().holdId();
        centralProcessId = process.hold() == null ? null : process.hold().centralProcessId();
        expiresAt = process.hold() == null ? null : process.hold().expiresAt();
        requestEventId = process.requestEventId();
        reservationId = process.reservationId();
        reasonCode = process.reasonCode();
        writeId = mutation.toString();
        closedAt = process.closedAt();
        var cancellation = process.cancellation();
        cancellationStatus = cancellation == null ? null : cancellation.name();
        updatedAt = now;
    }

    /** Elegibilidad técnica, separada del presupuesto de efectos y del estado del agregado. */
    public boolean recoveryDue(Instant now, Duration initialDelay) {
        if (closedAt != null && cancellationStatus == null) return false;
        var next = recoveryNextAttemptAt;
        if (next == null) next = cancellationStatus != null ? updatedAt : updatedAt.plus(initialDelay);
        return !now.isBefore(next);
    }

    /** No cambia writeId, updatedAt ni contadores de teléfono/cancelación. */
    public void scheduleRecovery(Instant nextAttempt) {
        recoveryNextAttemptAt = nextAttempt;
    }

    /** Conserva una reserva posterior ya concedida a otro recorrido. */
    public void deferRecovery(Instant nextAttempt) {
        if (recoveryNextAttemptAt == null || recoveryNextAttemptAt.isBefore(nextAttempt))
            recoveryNextAttemptAt = nextAttempt;
    }

}
