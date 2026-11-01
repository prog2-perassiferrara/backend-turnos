package com.prog2.appointment.reservation.domain.model;

import com.prog2.appointment.reservation.domain.enums.VerificationStatus;

import java.time.Instant;
import java.util.UUID;

/** Proyección propia de una consulta; no contiene teléfono ni identificadores técnicos. */
public record ReservationStatus(
        ReservationProcess process,
        UUID phoneRequestId,
        boolean canSubmitPhone,
        VerificationStatus verificationStatus,
        Instant verifiedAt,
        String warningCode) {
    /** Vista local sin atribuir a esta lectura una consulta central reciente. */
    public static ReservationStatus local(
            ReservationProcess process, PhoneSubmission phone, Instant now) {
        return new ReservationStatus(
                process,
                phone == null ? null : phone.id(),
                process.canSubmitPhone(now) && (phone == null || phone.closedAt() != null),
                VerificationStatus.LOCAL,
                null,
                null);
    }
}
