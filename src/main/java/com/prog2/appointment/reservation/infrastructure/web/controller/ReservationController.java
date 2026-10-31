package com.prog2.appointment.reservation.infrastructure.web.controller;

import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import com.prog2.appointment.reservation.domain.ports.in.SubmitReservationPhoneUseCase;
import com.prog2.appointment.reservation.domain.ports.in.GetReservationProcessUseCase;
import com.prog2.appointment.reservation.domain.model.SubmitPhoneRequest;
import com.prog2.appointment.reservation.infrastructure.web.dto.SubmitPhoneBody;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import com.prog2.appointment.reservation.domain.ports.in.StartReservationUseCase;
import com.prog2.appointment.reservation.infrastructure.web.dto.ReservationProcessResponse;
import com.prog2.appointment.reservation.infrastructure.web.dto.StartReservationBody;
import com.prog2.appointment.reservation.infrastructure.web.mapper.ReservationDtoMapper;
import jakarta.validation.Valid;

/** Adaptador entrante; los filtros existentes validan usuario/ROLE_USER antes del caso de uso. */
@RestController
public final class ReservationController {
    private final StartReservationUseCase start;
    private final ReservationDtoMapper mapper;
    private final SubmitReservationPhoneUseCase submit;
    private final GetReservationProcessUseCase get;

    public ReservationController(
            StartReservationUseCase start,
            ReservationDtoMapper mapper,
            SubmitReservationPhoneUseCase submit,
            GetReservationProcessUseCase get) {
        this.start = start;
        this.mapper = mapper;
        this.submit = submit;
        this.get = get;
    }

    /**
     * @return 202 por nueva operación o 200 por repetición propia, sin ejecutar nuevamente sus
     *     efectos
     */
    @PostMapping(path = "/api/reservation-processes", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReservationProcessResponse> start(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody StartReservationBody body) {
        var result = start.start(Long.parseLong(jwt.getSubject()), mapper.toRequest(body));
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.ACCEPTED)
                .cacheControl(CacheControl.noStore())
                .body(mapper.toResponse(result.process()));
    }

    /**
     * @return 202 solo después del commit local; un replay equivalente devuelve 200
     */
    @PostMapping(
            path = "/api/reservation-processes/{processId}/phone",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReservationProcessResponse> phone(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID processId,
            @Valid @RequestBody SubmitPhoneBody body) {
        var result =
                submit.submit(
                        Long.parseLong(jwt.getSubject()),
                        processId,
                        new SubmitPhoneRequest(body.requestId(), body.phoneNumber()));
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.ACCEPTED)
                .cacheControl(CacheControl.noStore())
                .body(mapper.toResponse(result.status()));
    }

    /**
     * @return progreso propio vigente o histórico, sin exponer teléfono ni IDs centrales
     */
    @GetMapping(
            path = "/api/reservation-processes/{processId}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReservationProcessResponse> get(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID processId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(mapper.toResponse(get.get(Long.parseLong(jwt.getSubject()), processId)));
    }
}
