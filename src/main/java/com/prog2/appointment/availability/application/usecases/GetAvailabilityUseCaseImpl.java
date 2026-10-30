package com.prog2.appointment.availability.application.usecases;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;
import com.prog2.appointment.availability.application.exception.AvailabilityException;
import com.prog2.appointment.availability.domain.enums.AvailabilityFailure;
import com.prog2.appointment.availability.domain.model.Availability;
import com.prog2.appointment.availability.domain.ports.in.GetAvailabilityUseCase;
import com.prog2.appointment.availability.domain.ports.out.AppointmentOccupanciesGateway;
import com.prog2.appointment.availability.domain.ports.out.ProfessionalAgendaGateway;

/** Orquesta CU-03 mediante puertos; no conoce JWT, HTTP, almacenamiento ni estado de otra consulta. */
public final class GetAvailabilityUseCaseImpl implements GetAvailabilityUseCase {
    private final ProfessionalAgendaGateway agendas;
    private final AppointmentOccupanciesGateway occupancies;
    private final Clock clock;
    private final ZoneId zone;

    public GetAvailabilityUseCaseImpl(ProfessionalAgendaGateway agendas, AppointmentOccupanciesGateway occupancies,
            Clock clock, ZoneId zone) {
        this.agendas = agendas;
        this.occupancies = occupancies;
        this.clock = clock;
        this.zone = zone;
    }

    @Override
    public Availability get(long professionalId, LocalDate date) {
        var admittedAt = clock.instant().atZone(zone).toLocalDateTime();
        if (professionalId <= 0 || date == null || date.isBefore(admittedAt.toLocalDate())) {
            throw new AvailabilityException(AvailabilityFailure.INVALID_AVAILABILITY_QUERY);
        }
        var agenda = agendas.find(professionalId).orElseThrow(
                () -> new AvailabilityException(AvailabilityFailure.PROFESSIONAL_NOT_FOUND));
        if (!agenda.enabled()) {
            throw new AvailabilityException(AvailabilityFailure.PROFESSIONAL_DISABLED);
        }
        var candidates = agenda.candidateStarts(date).stream()
                .filter(time -> date.atTime(time).isAfter(admittedAt)).toList();
        if (candidates.isEmpty()) {
            return new Availability(professionalId, date, zone, List.of());
        }
        var occupied = occupancies.get(professionalId, date);
        var now = clock.instant();
        var localNow = now.atZone(zone).toLocalDateTime();
        var blocked = occupied.stream().filter(position -> position.blocksAt(now))
                .map(position -> position.startTime()).collect(Collectors.toSet());
        var available = candidates.stream().filter(time -> date.atTime(time).isAfter(localNow))
                .filter(time -> !blocked.contains(time)).toList();
        return new Availability(professionalId, date, zone, available);
    }
}
