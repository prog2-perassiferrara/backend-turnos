package com.prog2.appointment.reservation.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

/** Frontera REST v1 de cancelación; los campos adicionales compatibles no cambian su identidad. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CancellationResponse(
        String reservationProcessId, Long reservationId, String status, Instant cancelledAt) {}
