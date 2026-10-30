package com.prog2.appointment.reservation.infrastructure.web.controller;

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

    public ReservationController(StartReservationUseCase start, ReservationDtoMapper mapper) {
        this.start = start;
        this.mapper = mapper;
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
}
