package com.prog2.appointment.availability.domain.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

/** Resultado inmutable de lectura; no concede exclusividad ni un hold al usuario. */
public record Availability(long professionalId, LocalDate date, ZoneId timeZone,
        List<LocalTime> availableStartTimes) {
    public Availability {
        availableStartTimes = List.copyOf(availableStartTimes);
    }
}
