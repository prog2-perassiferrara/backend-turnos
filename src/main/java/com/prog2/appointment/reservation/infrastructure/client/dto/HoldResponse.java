package com.prog2.appointment.reservation.infrastructure.client.dto;

import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Respuesta central: campos necesarios obligatorios al adaptar; extras v1 compatibles se ignoran.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HoldResponse(
        String holdId, String reservationProcessId, String status, Instant expiresAt) {}
