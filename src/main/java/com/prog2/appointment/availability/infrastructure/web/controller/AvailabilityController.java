package com.prog2.appointment.availability.infrastructure.web.controller;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.prog2.appointment.availability.domain.ports.in.GetAvailabilityUseCase;
import com.prog2.appointment.availability.infrastructure.web.dto.AvailabilityResponse;
import com.prog2.appointment.availability.infrastructure.web.mapper.AvailabilityDtoMapper;

/** Adaptador HTTP de CU-03; los filtros existentes autentican/autorizan antes de consultar proveedores. */
@RestController
public final class AvailabilityController {
    private final GetAvailabilityUseCase availability;
    private final AvailabilityDtoMapper mapper;

    public AvailabilityController(GetAvailabilityUseCase availability, AvailabilityDtoMapper mapper) {
        this.availability = availability;
        this.mapper = mapper;
    }

    /** @return horarios libres o lista vacía válida; no representa una reserva ni un bloqueo */
    @GetMapping(path = "/api/appointment-availability", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AvailabilityResponse> get(@RequestParam("professionalId") long professionalId,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(mapper.toResponse(availability.get(professionalId, date)));
    }
}
