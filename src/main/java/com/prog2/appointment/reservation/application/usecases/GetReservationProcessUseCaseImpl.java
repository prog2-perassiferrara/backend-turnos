package com.prog2.appointment.reservation.application.usecases;

import com.prog2.appointment.reservation.application.exception.ReservationOperationException;
import com.prog2.appointment.reservation.domain.enums.ReservationFailure;
import com.prog2.appointment.reservation.domain.enums.ReservationPhase;
import com.prog2.appointment.reservation.domain.enums.VerificationStatus;
import com.prog2.appointment.reservation.domain.exception.ReservationGatewayException;
import com.prog2.appointment.reservation.domain.model.ReservationStatus;
import com.prog2.appointment.reservation.domain.ports.in.GetReservationProcessUseCase;
import com.prog2.appointment.reservation.domain.ports.out.CentralAppointmentGateway;
import com.prog2.appointment.reservation.domain.ports.out.ReservationProcessRepository;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

/** Lectura propia y una consulta central acotada solo cuando puede resolver incertidumbre. */
public final class GetReservationProcessUseCaseImpl implements GetReservationProcessUseCase {
    private final ReservationProcessRepository repository;
    private final CentralAppointmentGateway central;
    private final Clock clock;
    private final Duration verificationDelay;

    public GetReservationProcessUseCaseImpl(
            ReservationProcessRepository repository,
            CentralAppointmentGateway central,
            Clock clock,
            Duration verificationDelay) {
        this.repository = repository;
        this.central = central;
        this.clock = clock;
        this.verificationDelay = verificationDelay;
    }

    @Override
    public ReservationStatus get(long ownerId, UUID processId) {
        var process =
                repository
                        .find(processId)
                        .filter(value -> value.ownerId() == ownerId)
                        .orElseThrow(
                                () ->
                                        new ReservationOperationException(
                                                ReservationFailure.RESERVATION_PROCESS_NOT_FOUND,
                                                null));
        var phone = repository.latestPhone(processId).orElse(null);
        var now = clock.instant();
        boolean uncertain =
                process.phase() == ReservationPhase.CONFIRMATION_UNCERTAIN
                        || process.phase() == ReservationPhase.RECONCILIATION_REQUIRED;
        boolean overdue = process.hold() != null && !now.isBefore(process.hold().expiresAt());
        boolean delayed =
                phone != null
                        && phone.publishedAt() != null
                        && !now.isBefore(phone.publishedAt().plus(verificationDelay));
        if (process.hold() == null
                || process.phase().isFinal()
                || !(uncertain
                        || overdue
                        || process.phase() == ReservationPhase.WAITING_FOR_RESULT && delayed)) {
            return ReservationStatus.local(process, phone, now);
        }
        VerificationStatus verification;
        String warning;
        try {
            var evidence = central.find(process).filter(value -> value.belongsTo(process));
            if (evidence.isPresent()) {
                repository.reconcile(processId, evidence.get(), now);
                verification = VerificationStatus.VERIFIED;
                warning = null;
            } else {
                verification = VerificationStatus.INCONCLUSIVE;
                warning = "APPOINTMENT_STATUS_INCONCLUSIVE";
            }
        } catch (ReservationGatewayException _) {
            verification = VerificationStatus.UNAVAILABLE;
            warning = "APPOINTMENT_STATUS_UNAVAILABLE";
        }
        // Kafka pudo avanzar mientras se esperaba HTTP: nunca devolver la copia anterior.
        var current = repository.find(processId).orElseThrow();
        var latest = repository.latestPhone(processId).orElse(null);
        var local = ReservationStatus.local(current, latest, clock.instant());
        return new ReservationStatus(
                current,
                local.phoneRequestId(),
                local.canSubmitPhone(),
                verification,
                verification == VerificationStatus.VERIFIED ? now : null,
                warning);
    }
}
