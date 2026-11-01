package com.prog2.appointment.reservation.infrastructure.client.adapter;

import com.prog2.appointment.reservation.domain.model.ProfessionalName;

import com.prog2.appointment.integration.domain.ports.in.TechnicalAccessUseCase;
import com.prog2.appointment.reservation.domain.enums.CentralAppointmentState;
import com.prog2.appointment.reservation.domain.enums.ReservationFailure;
import com.prog2.appointment.reservation.domain.exception.ReservationGatewayException;
import com.prog2.appointment.reservation.domain.model.CentralAppointment;
import com.prog2.appointment.reservation.domain.model.ReservationProcess;
import com.prog2.appointment.reservation.domain.model.ReservationSelection;
import com.prog2.appointment.reservation.domain.ports.out.CentralAppointmentGateway;
import com.prog2.appointment.reservation.infrastructure.client.dto.CentralAppointmentResponse;

import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.HashSet;
import com.prog2.appointment.reservation.domain.model.CentralAppointmentsBatch;
import com.prog2.appointment.reservation.domain.enums.VerificationStatus;

/** Lecturas puntuales y paginadas acotadas; ausencia de coincidencia nunca demuestra liberación. */
public final class WebClientCentralAppointmentAdapter implements CentralAppointmentGateway {
    private final WebClient client;
    private final TechnicalAccessUseCase access;
    private final Duration timeout;

    public WebClientCentralAppointmentAdapter(
            WebClient client, TechnicalAccessUseCase access, Duration timeout) {
        this.client = client;
        this.access = access;
        this.timeout = timeout;
    }

    @Override
    public CentralAppointmentsBatch listOwn(long ownerId) {
        var matches = new HashMap<String, CentralAppointment>();
        var conflicts = new HashSet<String>();
        var status = VerificationStatus.VERIFIED;
        Long expectedTotal = null;
        long deadline = System.nanoTime() + timeout.multipliedBy(2).toNanos();
        try {
            String token = access.getAccess().token().value();
            String patient = "user-" + ownerId;
            for (int page = 0; page < 3; page++) {
                var result = page(patient, page, token, deadline);
                if (result == null) return batch(matches, VerificationStatus.INCONCLUSIVE);
                if (!collect(result, patient, matches, conflicts))
                    status = VerificationStatus.INCONCLUSIVE;
                long total = Long.parseLong(result.total());
                if (!validPage(result, page, total, expectedTotal)) {
                    return batch(matches, VerificationStatus.INCONCLUSIVE);
                }
                expectedTotal = total;
                if (!result.hasNext()) return batch(matches, status);
            }
            return batch(matches, VerificationStatus.INCONCLUSIVE);
        } catch (IllegalArgumentException _) {
            return batch(matches, VerificationStatus.INCONCLUSIVE);
        } catch (RuntimeException _) {
            return batch(matches, VerificationStatus.UNAVAILABLE);
        }
    }

    private static boolean collect(
            PageResult result,
            String patient,
            Map<String, CentralAppointment> matches,
            java.util.Set<String> conflicts) {
        boolean valid = true;
        for (var item : result.rows().stream().limit(100).toList()) {
            var candidate =
                    item != null && patient.equals(item.externalPatientId())
                            ? appointment(item)
                            : null;
            if (candidate == null || !collectCandidate(candidate, matches, conflicts))
                valid = false;
        }
        return valid;
    }

    private static boolean collectCandidate(
            CentralAppointment candidate,
            Map<String, CentralAppointment> matches,
            java.util.Set<String> conflicts) {
        String key = candidate.centralProcessId();
        if (conflicts.contains(key)) return false;
        var prior = matches.putIfAbsent(key, candidate);
        if (prior == null || prior.equals(candidate)) return true;
        matches.remove(key);
        conflicts.add(key);
        return false;
    }

    private static boolean validPage(PageResult result, int page, long total, Long expectedTotal) {
        int received = result.rows().size();
        long consumed = (long) page * 100 + received;
        return total >= consumed
                && received <= 100
                && total >= 0
                && (expectedTotal == null || total == expectedTotal)
                && result.hasNext() == (consumed < total)
                && (!result.hasNext() || received == 100);
    }

    private PageResult page(String patient, int page, String token, long deadline) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) throw unavailable();
        // La siguiente URL se construye localmente: nunca seguir hosts proporcionados en Link.
        return client.get()
                .uri(builder ->
                        builder.path("/api/appointments")
                                .queryParam("externalPatientId", patient)
                                .queryParam("page", page)
                                .queryParam("size", 100)
                                .build())
                .headers(headers -> headers.setBearerAuth(token))
                .accept(MediaType.APPLICATION_JSON)
                .exchangeToMono(result -> {
                    if (result.statusCode().value() == 200) {
                        String total = result.headers().asHttpHeaders().getFirst("X-Total-Count");
                        String links = result.headers().asHttpHeaders().getFirst("Link");
                        return result.bodyToMono(CentralAppointmentResponse[].class)
                                .map(rows -> new PageResult(Arrays.asList(rows), total, links));
                    }
                    if (result.statusCode().value() == 401) access.rejectAccess(token);
                    return result.releaseBody().then(Mono.error(unavailable()));
                })
                .block(Duration.ofNanos(remaining));
    }

    private static CentralAppointmentsBatch batch(
            Map<String, CentralAppointment> matches, VerificationStatus status) {
        return new CentralAppointmentsBatch(new ArrayList<>(matches.values()), status);
    }

    private record PageResult(List<CentralAppointmentResponse> rows, String total, String links) {
        boolean hasNext() {
            return links != null && (links.contains("rel=\"next\"") || links.contains("rel=next"));
        }
    }

    @Override
    public Optional<CentralAppointment> find(ReservationProcess process) {
        try {
            String token = access.getAccess().token().value();
            var response =
                    client.get()
                            .uri(
                                    builder ->
                                            builder.path("/api/appointments")
                                                    .queryParam(
                                                            "externalPatientId",
                                                            process.patient().externalPatientId())
                                                    .queryParam("from", process.selection().date())
                                                    .queryParam("to", process.selection().date())
                                                    .queryParam("page", 0)
                                                    .queryParam("size", 100)
                                                    .build())
                            .headers(headers -> headers.setBearerAuth(token))
                            .accept(MediaType.APPLICATION_JSON)
                            .exchangeToMono(
                                    result -> {
                                        if (result.statusCode().value() == 200)
                                            return result.bodyToMono(
                                                    CentralAppointmentResponse[].class);
                                        if (result.statusCode().value() == 401)
                                            access.rejectAccess(token);
                                        return result.releaseBody().then(Mono.error(unavailable()));
                                    })
                            .block(timeout.multipliedBy(2));
            if (response == null) return Optional.empty();
            return selected(response, process);
        } catch (RuntimeException _) {
            throw unavailable();
        }
    }

    private static Optional<CentralAppointment> selected(
            CentralAppointmentResponse[] response, ReservationProcess process) {
        CentralAppointment match = null;
        for (var item : response) {
            if (!sameIdentity(item, process)) continue;
            var candidate = appointment(item);
            if (candidate == null
                    || !candidate.belongsTo(process)
                    || match != null && !match.equals(candidate)) return Optional.empty();
            match = candidate;
        }
        return Optional.ofNullable(match);
    }

    private static boolean sameIdentity(
            CentralAppointmentResponse item, ReservationProcess process) {
        return item != null
                && process.hold().centralProcessId().equals(item.reservationProcessId())
                && process.patient().externalPatientId().equals(item.externalPatientId());
    }

    private static CentralAppointment appointment(CentralAppointmentResponse item) {
        if (item.professionalId() == null
                || item.appointmentDate() == null
                || item.startTime() == null
                || item.reservationId() == null
                || item.status() == null) return null;
        try {
            return new CentralAppointment(
                    item.reservationProcessId(),
                    item.externalPatientId(),
                    new ReservationSelection(
                            item.professionalId(), item.appointmentDate(), item.startTime()),
                    item.reservationId(),
                    CentralAppointmentState.valueOf(item.status()),
                    ProfessionalName.from(
                            item.professionalFirstName(), item.professionalLastName()));
        } catch (IllegalArgumentException _) {
            return null;
        }
    }

    private static ReservationGatewayException unavailable() {
        return new ReservationGatewayException(
                ReservationFailure.APPOINTMENT_STATUS_UNAVAILABLE, false, false);
    }
}
