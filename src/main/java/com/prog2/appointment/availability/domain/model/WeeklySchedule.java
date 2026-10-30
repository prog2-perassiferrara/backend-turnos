package com.prog2.appointment.availability.domain.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Regla semanal local; solo produce turnos completos dentro del mismo horario de atención. */
public record WeeklySchedule(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime,
        int slotDurationMinutes, boolean enabled) {
    public WeeklySchedule {
        Objects.requireNonNull(dayOfWeek);
        Objects.requireNonNull(startTime);
        Objects.requireNonNull(endTime);
        if (slotDurationMinutes <= 0 || !endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("Invalid weekly schedule");
        }
    }

    /**
     * @param date fecha local elegida; no transforma las horas según una zona del sistema
     * @return inicios completos de este horario, o vacío si no aplica
     */
    public List<LocalTime> startsOn(LocalDate date) {
        if (!enabled || !date.getDayOfWeek().equals(dayOfWeek)) {
            return List.of();
        }
        var starts = new ArrayList<LocalTime>();
        var cursor = date.atTime(startTime);
        var closing = date.atTime(endTime);
        // Anclar a una fecha evita que LocalTime vuelva a las 00:00 al sumar duración.
        while (!cursor.plusMinutes(slotDurationMinutes).isAfter(closing)) {
            starts.add(cursor.toLocalTime());
            cursor = cursor.plusMinutes(slotDurationMinutes);
        }
        return List.copyOf(starts);
    }
}
