package com.prog2.appointment.availability.infrastructure.client.adapter;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import com.prog2.appointment.availability.domain.enums.AvailabilityFailure;
import com.prog2.appointment.availability.domain.enums.OccupancyStatus;
import com.prog2.appointment.availability.domain.exception.AvailabilityGatewayException;
import com.prog2.appointment.availability.domain.model.AppointmentOccupancy;
import com.prog2.appointment.availability.domain.ports.out.AppointmentOccupanciesGateway;
import com.prog2.appointment.availability.infrastructure.client.AvailabilityHttpClient;
import com.prog2.appointment.availability.infrastructure.client.dto.AppointmentOccupanciesResponse;
import com.prog2.appointment.integration.domain.ports.in.TechnicalAccessUseCase;

/** Adapter de ocupaciones; compone únicamente el puerto público de integración, sin acceder a su estado interno. */
public final class WebClientAppointmentOccupanciesAdapter implements AppointmentOccupanciesGateway {
    private final AvailabilityHttpClient client;
    private final TechnicalAccessUseCase access;

    public WebClientAppointmentOccupanciesAdapter(AvailabilityHttpClient client, TechnicalAccessUseCase access) {
        this.client = client;
        this.access = access;
    }

    @Override
    public List<AppointmentOccupancy> get(long professionalId, LocalDate date) {
        String token = currentToken();
        try {
            String path = "/api/appointment-occupancies?professionalId=" + professionalId + "&from=" + date + "&to=" + date;
            var response = client.get(path, token, AppointmentOccupanciesResponse.class,
                    WebClientAppointmentOccupanciesAdapter::classify);
            return toDomain(response, professionalId, date);
        } catch (AvailabilityGatewayException failure) {
            if (failure.failure() == AvailabilityFailure.CENTRAL_TOKEN_REJECTED) {
                // El puerto invalida solo este token; un 401 tardío no borra una sesión más nueva.
                access.rejectAccess(token);
                throw new AvailabilityGatewayException(AvailabilityFailure.TECHNICAL_INTEGRATION_UNAVAILABLE);
            }
            throw failure;
        } catch (IllegalArgumentException | DateTimeException | NullPointerException _) {
            throw new AvailabilityGatewayException(AvailabilityFailure.OCCUPANCIES_UNAVAILABLE);
        }
    }

    private String currentToken() {
        try {
            return access.getAccess().token().value();
        } catch (IllegalStateException _) {
            throw new AvailabilityGatewayException(AvailabilityFailure.TECHNICAL_INTEGRATION_UNAVAILABLE);
        }
    }

    private static List<AppointmentOccupancy> toDomain(AppointmentOccupanciesResponse response, long id, LocalDate date) {
        if (!Long.valueOf(id).equals(response.professionalId()) || !date.equals(response.from())
                || !date.equals(response.to())) {
            throw new IllegalArgumentException("Inconsistent occupancy range");
        }
        return response.occupancies().stream().map(position -> {
            if (!date.equals(position.date())) {
                throw new IllegalArgumentException("Inconsistent occupancy date");
            }
            return new AppointmentOccupancy(position.date(), LocalTime.parse(position.startTime()),
                    OccupancyStatus.valueOf(position.status()), position.expiresAt());
        }).toList();
    }

    private static AvailabilityFailure classify(int status, String code) {
        if (status == 401) {
            return AvailabilityFailure.CENTRAL_TOKEN_REJECTED;
        }
        if (status == 403 && "STUDENT_INTEGRATION_NOT_FOUND".equals(code)) {
            return AvailabilityFailure.TECHNICAL_INTEGRATION_UNAVAILABLE;
        }
        return AvailabilityFailure.OCCUPANCIES_UNAVAILABLE;
    }
}
