package com.prog2.appointment.reservation.infrastructure.availability.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import com.prog2.appointment.availability.domain.exception.AvailabilityGatewayException;
import com.prog2.appointment.availability.domain.ports.in.GetAvailabilityUseCase;
import com.prog2.appointment.reservation.domain.enums.ReservationFailure;
import com.prog2.appointment.reservation.domain.exception.ReservationGatewayException;
import com.prog2.appointment.reservation.domain.model.ReservationSelection;
import com.prog2.appointment.reservation.domain.model.ProfessionalName;
import com.prog2.appointment.reservation.domain.ports.out.ReservableSlotGateway;

/**
 * Adapter aprobado: CU-03 conserva vigencia/cálculo; no se importan sus implementaciones ni
 * excepciones internas.
 */
@Component
@RequiredArgsConstructor
public final class AvailabilityReservationSlotAdapter implements ReservableSlotGateway {
    private final GetAvailabilityUseCase availability;

    @Override
    public ProfessionalName verify(ReservationSelection selection) {
        try {
            var result = availability.get(selection.professionalId(), selection.date());
            if (!result.availableStartTimes().contains(selection.startTime())) {
                throw new ReservationGatewayException(
                        ReservationFailure.SLOT_NOT_AVAILABLE, false, true);
            }
            return ProfessionalName.from(result.firstName(), result.lastName());
        } catch (AvailabilityGatewayException _) {
            throw new ReservationGatewayException(
                    ReservationFailure.CATALOG_UNAVAILABLE, false, false);
        } catch (IllegalArgumentException _) {
            throw new ReservationGatewayException(
                    ReservationFailure.INVALID_RESERVATION_SELECTION, false, true);
        }
    }
}
