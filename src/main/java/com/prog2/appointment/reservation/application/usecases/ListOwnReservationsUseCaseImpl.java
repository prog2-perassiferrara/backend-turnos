package com.prog2.appointment.reservation.application.usecases;

import com.prog2.appointment.reservation.application.exception.ReservationOperationException;
import com.prog2.appointment.reservation.domain.enums.ReservationFailure;
import com.prog2.appointment.reservation.domain.enums.VerificationStatus;
import com.prog2.appointment.reservation.domain.exception.ReservationGatewayException;
import com.prog2.appointment.reservation.domain.exception.ReservationStorageException;
import com.prog2.appointment.reservation.domain.model.CentralAppointment;
import com.prog2.appointment.reservation.domain.model.CentralAppointmentsBatch;
import com.prog2.appointment.reservation.domain.model.OwnReservationPage;
import com.prog2.appointment.reservation.domain.model.OwnReservations;
import com.prog2.appointment.reservation.domain.model.PhoneSubmission;
import com.prog2.appointment.reservation.domain.model.ReservationProcess;
import com.prog2.appointment.reservation.domain.model.ReservationStatus;
import com.prog2.appointment.reservation.domain.ports.in.ListOwnReservationsUseCase;
import com.prog2.appointment.reservation.domain.ports.out.CentralAppointmentGateway;
import com.prog2.appointment.reservation.domain.ports.out.ReservationProcessRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Orquesta evidencia y relectura propia; no importa reservas ni genera efectos centrales. */
public final class ListOwnReservationsUseCaseImpl implements ListOwnReservationsUseCase {
    private final ReservationProcessRepository repository;
    private final CentralAppointmentGateway central;
    private final Clock clock;

    public ListOwnReservationsUseCaseImpl(
            ReservationProcessRepository repository,
            CentralAppointmentGateway central,
            Clock clock) {
        this.repository = repository;
        this.central = central;
        this.clock = clock;
    }

    @Override
    public OwnReservations list(long ownerId, int page, int size) {
        try {
            OwnReservationPage.validate(page, size);
        } catch (IllegalArgumentException _) {
            throw new ReservationOperationException(
                    ReservationFailure.INVALID_RESERVATION_PAGE, null);
        }
        var initial = read(ownerId, page, size);
        var batch = evidence(ownerId);
        var observed = clock.instant();
        var selected = new ArrayList<>(initial.history());
        if (initial.pending() != null) selected.add(initial.pending());
        for (var process : selected) {
            match(batch, process)
                    .ifPresent(value -> repository.reconcile(process.id(), value, observed));
        }
        // Kafka y otra consulta pudieron avanzar durante HTTP: agrupar desde una sola lectura
        // nueva.
        var current = read(ownerId, page, size);
        var history =
                current.history().stream()
                        .map(value -> status(value, null, batch, observed))
                        .toList();
        var pending =
                current.pending() == null
                        ? null
                        : status(current.pending(), current.phone(), batch, observed);
        var verification = batch.status();
        if (verification == VerificationStatus.VERIFIED
                && (history.stream()
                                .anyMatch(value ->
                                        value.verificationStatus()
                                                == VerificationStatus.INCONCLUSIVE)
                        || pending != null
                                && pending.verificationStatus()
                                        == VerificationStatus.INCONCLUSIVE)) {
            verification = VerificationStatus.INCONCLUSIVE;
        }
        return new OwnReservations(
                pending,
                history,
                current.totalElements(),
                page,
                size,
                verification,
                verification == VerificationStatus.VERIFIED ? observed : null,
                warning(verification));
    }

    private OwnReservationPage read(long ownerId, int page, int size) {
        var result = repository.readOwn(ownerId, page, size);
        if (result.history().stream().anyMatch(value -> value.ownerId() != ownerId)
                || result.pending() != null && result.pending().ownerId() != ownerId) {
            throw new ReservationStorageException();
        }
        return result;
    }

    private CentralAppointmentsBatch evidence(long ownerId) {
        try {
            return central.listOwn(ownerId);
        } catch (ReservationGatewayException _) {
            return new CentralAppointmentsBatch(List.of(), VerificationStatus.UNAVAILABLE);
        }
    }

    private ReservationStatus status(
            ReservationProcess process,
            PhoneSubmission phone,
            CentralAppointmentsBatch batch,
            Instant observed) {
        var local = ReservationStatus.local(process, phone, clock.instant());
        var verification = verification(process, batch);
        return new ReservationStatus(
                process,
                local.phoneRequestId(),
                local.canSubmitPhone(),
                verification,
                verification == VerificationStatus.VERIFIED ? observed : null,
                warning(verification));
    }

    private static VerificationStatus verification(
            ReservationProcess process, CentralAppointmentsBatch batch) {
        if (process.hold() == null) return VerificationStatus.LOCAL;
        if (match(batch, process).filter(value -> value.agreesWith(process)).isPresent()) {
            return VerificationStatus.VERIFIED;
        }
        return batch.status() == VerificationStatus.UNAVAILABLE
                ? VerificationStatus.UNAVAILABLE
                : VerificationStatus.INCONCLUSIVE;
    }

    private static Optional<CentralAppointment> match(
            CentralAppointmentsBatch batch, ReservationProcess process) {
        var matches =
                batch.appointments().stream()
                        .filter(value -> value.belongsTo(process))
                        .distinct()
                        .toList();
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    private static String warning(VerificationStatus status) {
        return switch (status) {
            case UNAVAILABLE -> "APPOINTMENT_STATUS_UNAVAILABLE";
            case INCONCLUSIVE -> "APPOINTMENT_STATUS_INCONCLUSIVE";
            default -> null;
        };
    }
}
