package com.prog2.appointment.reservation.infrastructure.availability.adapter;

import com.prog2.appointment.availability.domain.exception.AvailabilityGatewayException;
import com.prog2.appointment.availability.domain.ports.in.GetAvailabilityUseCase;
import com.prog2.appointment.reservation.domain.enums.ReservationFailure;
import com.prog2.appointment.reservation.domain.exception.ReservationGatewayException;
import com.prog2.appointment.reservation.domain.model.ReservationSelection;
import com.prog2.appointment.reservation.domain.ports.out.ReservableSlotGateway;

/**
 * Adapter aprobado: CU-03 conserva vigencia/cálculo; no se importan sus implementaciones ni
 * excepciones internas.
 */
public final class AvailabilityReservationSlotAdapter implements ReservableSlotGateway {
    private final GetAvailabilityUseCase availability;

    public AvailabilityReservationSlotAdapter(GetAvailabilityUseCase availability) {
        this.availability = availability;
    }

    @Override
    public void verify(ReservationSelection selection) {
        try {
            var result = availability.get(selection.professionalId(), selection.date());
            if (!result.availableStartTimes().contains(selection.startTime())) {
                throw new ReservationGatewayException(
                        ReservationFailure.SLOT_NOT_AVAILABLE, false, true);
            }
        } catch (AvailabilityGatewayException _) {
            throw new ReservationGatewayException(
                    ReservationFailure.CATALOG_UNAVAILABLE, false, false);
        } catch (IllegalArgumentException _) {
            throw new ReservationGatewayException(
                    ReservationFailure.INVALID_RESERVATION_SELECTION, false, true);
        }
    }
}
