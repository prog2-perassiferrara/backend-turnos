package com.prog2.appointment.reservation.infrastructure.client.adapter;

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

/** Reconciliación puntual: una página y un intento; ausencia de coincidencia sigue inconclusa. */
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
                    CentralAppointmentState.valueOf(item.status()));
        } catch (IllegalArgumentException _) {
            return null;
        }
    }

    private static ReservationGatewayException unavailable() {
        return new ReservationGatewayException(
                ReservationFailure.APPOINTMENT_STATUS_UNAVAILABLE, false, false);
    }
}
