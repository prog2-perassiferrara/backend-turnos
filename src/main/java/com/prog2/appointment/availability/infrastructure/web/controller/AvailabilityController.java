package com.prog2.appointment.availability.infrastructure.web.controller;

import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import static com.prog2.appointment.infrastructure.config.OpenApiConfiguration.FORBIDDEN_RESPONSE;
import static com.prog2.appointment.infrastructure.config.OpenApiConfiguration.PROBLEM_SCHEMA;
import static com.prog2.appointment.infrastructure.config.OpenApiConfiguration.UNAUTHORIZED_RESPONSE;
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
@RequiredArgsConstructor
public final class AvailabilityController {
    private final GetAvailabilityUseCase availability;
    private final AvailabilityDtoMapper mapper;

    /** @return horarios libres o lista vacía válida; no representa una reserva ni un bloqueo */
    @Operation(summary = "Consultar disponibilidad", description = "JWT de usuario. Construye horarios libres con agenda vigente y ocupaciones centrales; una lista vacía es válida y no crea reservas.", tags = {"Disponibilidad"})
    @ApiResponse(responseCode = "200", description = "Consulta exitosa.", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "401", ref = UNAUTHORIZED_RESPONSE)
    @ApiResponse(responseCode = "403", ref = FORBIDDEN_RESPONSE)
    @ApiResponse(responseCode = "400", description = "INVALID_AVAILABILITY_QUERY o PROFESSIONAL_DISABLED",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(ref = PROBLEM_SCHEMA)))
    @ApiResponse(responseCode = "404", description = "PROFESSIONAL_NOT_FOUND",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(ref = PROBLEM_SCHEMA)))
    @ApiResponse(responseCode = "503", description = "CATALOG_NOT_CURRENT, CATALOG_UNAVAILABLE, OCCUPANCIES_UNAVAILABLE o TECHNICAL_INTEGRATION_UNAVAILABLE",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(ref = PROBLEM_SCHEMA)))
    @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(ref = PROBLEM_SCHEMA)))
    @GetMapping(path = "/api/appointment-availability", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AvailabilityResponse> get(@RequestParam("professionalId") long professionalId,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(mapper.toResponse(availability.get(professionalId, date)));
    }
}
