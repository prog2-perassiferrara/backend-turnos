package com.prog2.appointment.reservation.infrastructure.user.adapter;

import com.prog2.appointment.reservation.domain.enums.ReservationFailure;
import com.prog2.appointment.reservation.domain.exception.ReservationGatewayException;
import com.prog2.appointment.reservation.domain.model.ReservationPatient;
import com.prog2.appointment.reservation.domain.ports.out.ReservationPatientGateway;
import com.prog2.appointment.user.domain.exception.UserAccountUnavailableException;
import com.prog2.appointment.user.domain.ports.in.GetUserProfileUseCase;

/**
 * Adapter aprobado: consume solo la lectura pública de user y devuelve un modelo propio sin
 * secretos.
 */
public final class UserReservationPatientAdapter implements ReservationPatientGateway {
    private final GetUserProfileUseCase profiles;

    public UserReservationPatientAdapter(GetUserProfileUseCase profiles) {
        this.profiles = profiles;
    }

    @Override
    public ReservationPatient get(long ownerId) {
        try {
            var profile = profiles.get(ownerId).orElseThrow(IllegalArgumentException::new);
            if (!profile.activated() || profile.id() != ownerId) {
                throw new IllegalArgumentException();
            }
            return new ReservationPatient(ownerId, profile.firstName(), profile.lastName());
        } catch (UserAccountUnavailableException _) {
            throw new ReservationGatewayException(
                    ReservationFailure.STORAGE_UNAVAILABLE, false, false);
        } catch (IllegalArgumentException _) {
            throw new ReservationGatewayException(
                    ReservationFailure.PROFILE_NOT_RESERVABLE, false, true);
        }
    }
}
