package com.prog2.appointment.availability.domain.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.TreeSet;

/** Agenda obtenida para una consulta; no es una réplica persistida del catálogo. */
public record ProfessionalAgenda(long professionalId, boolean enabled, List<WeeklySchedule> schedules) {
    public ProfessionalAgenda {
        if (professionalId <= 0) {
            throw new IllegalArgumentException("Invalid professional identifier");
        }
        schedules = List.copyOf(schedules);
    }

    /**
     * @param date fecha local elegida
     * @return unión cronológica sin duplicados, sin descontar ocupaciones ni horas pasadas
     */
    public List<LocalTime> candidateStarts(LocalDate date) {
        if (!enabled) {
            return List.of();
        }
        var starts = new TreeSet<LocalTime>();
        schedules.forEach(schedule -> starts.addAll(schedule.startsOn(date)));
        return List.copyOf(starts);
    }
}
