package com.prog2.appointment.reservation.infrastructure.client.adapter;

import lombok.RequiredArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.prog2.appointment.integration.domain.ports.in.TechnicalAccessUseCase;
import com.prog2.appointment.reservation.domain.enums.ReservationFailure;
import com.prog2.appointment.reservation.domain.exception.ReservationGatewayException;
import com.prog2.appointment.reservation.domain.model.CancellationEvidence;
import com.prog2.appointment.reservation.domain.model.ReservationProcess;
import com.prog2.appointment.reservation.domain.ports.out.ReservationCancellationGateway;
import com.prog2.appointment.reservation.infrastructure.client.dto.CancellationResponse;
import java.time.Duration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/** Adapter del POST idempotente; la reconciliación y los reintentos pertenecen a aplicación. */
@RequiredArgsConstructor
public final class WebClientReservationCancellationAdapter implements ReservationCancellationGateway {
    private final WebClient client;
    private final TechnicalAccessUseCase access;
    private final Duration timeout;

    @Override
    public CancellationEvidence cancel(ReservationProcess process) {
        if (process.phase() != com.prog2.appointment.reservation.domain.enums.ReservationPhase.CONFIRMED) {
            throw new ReservationGatewayException(ReservationFailure.CANCELLATION_NOT_ALLOWED, false, true);
        }
        try {
            String token = access.getAccess().token().value();
            var result = client.post()
                    .uri("/api/appointments/{id}/cancel", process.hold().centralProcessId())
                    .headers(headers -> headers.setBearerAuth(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchangeToMono(response -> response.statusCode().value() == 200
                            ? response.bodyToMono(CancellationResponse.class)
                            : rejection(response, token))
                    .block(timeout.multipliedBy(2));
            if (result == null || !"CANCELLED".equals(result.status())) throw new IllegalArgumentException();
            var evidence = new CancellationEvidence(result.reservationProcessId(),
                    result.reservationId(), result.cancelledAt());
            if (!evidence.belongsTo(process)) throw new IllegalArgumentException();
            return evidence;
        } catch (ReservationGatewayException failure) {
            throw failure;
        } catch (RuntimeException _) {
            throw failure(ReservationFailure.CANCELLATION_UNCERTAIN, false);
        }
    }

    private Mono<CancellationResponse> rejection(ClientResponse response, String token) {
        int status = response.statusCode().value();
        if (status == 401) access.rejectAccess(token);
        return response.bodyToMono(Problem.class).onErrorReturn(new Problem(null))
                .defaultIfEmpty(new Problem(null))
                .flatMap(problem -> Mono.error(classify(status, problem.code())));
    }

    private static ReservationGatewayException classify(int status, String code) {
        ReservationFailure reason = switch (status) {
            case 404 -> "RESERVATION_NOT_FOUND".equals(code)
                    ? ReservationFailure.CANCELLATION_NOT_FOUND : ReservationFailure.CANCELLATION_REJECTED;
            case 403 -> "RESERVATION_OWNER_MISMATCH".equals(code)
                    ? ReservationFailure.CANCELLATION_OWNER_MISMATCH : ReservationFailure.CANCELLATION_REJECTED;
            default -> ReservationFailure.CANCELLATION_REJECTED;
        };
        return failure(status == 400 || status == 403 || status == 404 || status == 409
                ? reason : ReservationFailure.CANCELLATION_UNCERTAIN,
                status == 400 || status == 403 || status == 404 || status == 409);
    }

    private static ReservationGatewayException failure(ReservationFailure code, boolean definitive) {
        return new ReservationGatewayException(code, true, definitive);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Problem(String code) {}
}
