package com.prog2.appointment.reservation.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Campos usados para verificar identidad/resultado; se ignoran datos personales ajenos al contrato.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CentralAppointmentResponse(
        Long reservationId,
        String reservationProcessId,
        String externalPatientId,
        LocalDate appointmentDate,
        LocalTime startTime,
        Long professionalId,
        String status,
        String professionalFirstName,
        String professionalLastName) {}
