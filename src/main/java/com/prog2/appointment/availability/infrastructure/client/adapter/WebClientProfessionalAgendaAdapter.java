package com.prog2.appointment.availability.infrastructure.client.adapter;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;
import java.util.Optional;
import com.prog2.appointment.availability.domain.enums.AvailabilityFailure;
import com.prog2.appointment.availability.domain.exception.AvailabilityGatewayException;
import com.prog2.appointment.availability.domain.model.ProfessionalAgenda;
import com.prog2.appointment.availability.domain.model.WeeklySchedule;
import com.prog2.appointment.availability.domain.ports.out.ProfessionalAgendaGateway;
import com.prog2.appointment.availability.infrastructure.client.AvailabilityHttpClient;
import com.prog2.appointment.availability.infrastructure.client.dto.ProfessionalAgendaResponse;
import com.prog2.appointment.infrastructure.security.jwt.ServiceTokenIssuer;

/** Adapter de Catálogo: exige la lectura conjunta vigente sin reproducir su política de sincronización. */
public final class WebClientProfessionalAgendaAdapter implements ProfessionalAgendaGateway {
    private final AvailabilityHttpClient client;
    private final ServiceTokenIssuer issuer;

    public WebClientProfessionalAgendaAdapter(AvailabilityHttpClient client, ServiceTokenIssuer issuer) {
        this.client = client;
        this.issuer = issuer;
    }

    @Override
    public Optional<ProfessionalAgenda> find(long professionalId) {
        try {
            var response = client.get("/api/internal/catalog/professionals/" + professionalId,
                    issuer.issue(), ProfessionalAgendaResponse.class, WebClientProfessionalAgendaAdapter::classify);
            return Optional.of(toDomain(response, professionalId));
        } catch (AvailabilityGatewayException failure) {
            if (failure.failure() == AvailabilityFailure.PROFESSIONAL_NOT_FOUND) {
                return Optional.empty();
            }
            throw failure;
        } catch (IllegalArgumentException | DateTimeException | NullPointerException _) {
            throw new AvailabilityGatewayException(AvailabilityFailure.CATALOG_UNAVAILABLE);
        }
    }

    private static ProfessionalAgenda toDomain(ProfessionalAgendaResponse response, long id) {
        var catalog = Objects.requireNonNull(response.catalog());
        if (!"CURRENT".equals(Objects.requireNonNull(catalog.state()))) {
            throw new AvailabilityGatewayException(AvailabilityFailure.CATALOG_NOT_CURRENT);
        }
        if (!Boolean.TRUE.equals(catalog.copyAvailable()) || catalog.appliedVersion() == null
                || catalog.appliedVersion() < 0 || !catalog.appliedVersion().equals(catalog.observedVersion())
                || catalog.lastCheckedAt() == null || catalog.evaluatedAt() == null
                || catalog.lastCheckedAt().isAfter(catalog.evaluatedAt())) {
            throw new IllegalArgumentException("Inconsistent catalog metadata");
        }
        var professional = Objects.requireNonNull(response.professional());
        if (!Long.valueOf(id).equals(professional.id()) || professional.enabled() == null) {
            throw new IllegalArgumentException("Inconsistent professional");
        }
        var schedules = response.weeklySchedules().stream().map(schedule -> {
            if (!Long.valueOf(id).equals(schedule.professionalId()) || schedule.enabled() == null) {
                throw new IllegalArgumentException("Inconsistent weekly schedule");
            }
            return new WeeklySchedule(DayOfWeek.valueOf(schedule.dayOfWeek()), LocalTime.parse(schedule.startTime()),
                    LocalTime.parse(schedule.endTime()), schedule.slotDurationMinutes(), schedule.enabled());
        }).toList();
        return new ProfessionalAgenda(id, professional.enabled(), schedules);
    }

    private static AvailabilityFailure classify(int status, String code) {
        if (status == 404 && "PROFESSIONAL_NOT_FOUND".equals(code)) {
            return AvailabilityFailure.PROFESSIONAL_NOT_FOUND;
        }
        if (status == 503 && "CATALOG_NOT_CURRENT".equals(code)) {
            return AvailabilityFailure.CATALOG_NOT_CURRENT;
        }
        return AvailabilityFailure.CATALOG_UNAVAILABLE;
    }
}
