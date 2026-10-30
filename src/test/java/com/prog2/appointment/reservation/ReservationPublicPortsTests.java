package com.prog2.appointment.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.prog2.appointment.reservation.domain.enums.ReservationFailure;
import com.prog2.appointment.reservation.domain.exception.ReservationGatewayException;
import com.prog2.appointment.reservation.infrastructure.user.adapter.UserReservationPatientAdapter;
import com.prog2.appointment.user.domain.exception.UserAccountUnavailableException;
import com.prog2.appointment.user.domain.model.UserProfile;
import com.prog2.appointment.user.domain.ports.in.GetUserProfileUseCase;

/** La composición pública valida el perfil antes de entregar datos de paciente al caso de uso. */
class ReservationPublicPortsTests {
    private final GetUserProfileUseCase profiles = mock(GetUserProfileUseCase.class);
    private final UserReservationPatientAdapter adapter =
            new UserReservationPatientAdapter(profiles);

    @Test
    void anActiveTwoCharacterProfileUsesOnlyThePublicAccountIdentity() {
        when(profiles.get(42)).thenReturn(Optional.of(new UserProfile(42, "Al", "Li", true)));
        var patient = adapter.get(42);
        assertThat(patient.ownerId()).isEqualTo(42);
        assertThat(patient.firstName()).isEqualTo("Al");
        assertThat(patient.lastName()).isEqualTo("Li");
        assertThat(patient.externalPatientId()).isEqualTo("user-42");
    }

    @ParameterizedTest
    @CsvSource({"42,A,Li,true", "42,Al,L,true", "42,Al,Li,false", "43,Al,Li,true"})
    void incompatibleOrForeignProfilesCannotBecomeAPatient(
            long id, String first, String last, boolean active) {
        when(profiles.get(42)).thenReturn(Optional.of(new UserProfile(id, first, last, active)));
        assertThatThrownBy(() -> adapter.get(42))
                .isInstanceOfSatisfying(
                        ReservationGatewayException.class,
                        failure -> {
                            assertThat(failure.failure())
                                    .isEqualTo(ReservationFailure.PROFILE_NOT_RESERVABLE);
                            assertThat(failure.attempted()).isFalse();
                        });
    }

    @Test
    void aMissingAccountAndStorageOutageRemainDifferentFailures() {
        when(profiles.get(42)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> adapter.get(42))
                .isInstanceOfSatisfying(
                        ReservationGatewayException.class,
                        failure ->
                                assertThat(failure.failure())
                                        .isEqualTo(ReservationFailure.PROFILE_NOT_RESERVABLE));
        when(profiles.get(42)).thenThrow(new UserAccountUnavailableException());
        assertThatThrownBy(() -> adapter.get(42))
                .isInstanceOfSatisfying(
                        ReservationGatewayException.class,
                        failure ->
                                assertThat(failure.failure())
                                        .isEqualTo(ReservationFailure.STORAGE_UNAVAILABLE));
    }
}
