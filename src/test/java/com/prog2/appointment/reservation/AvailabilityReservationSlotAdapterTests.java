package com.prog2.appointment.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prog2.appointment.availability.domain.model.Availability;
import com.prog2.appointment.availability.domain.ports.in.GetAvailabilityUseCase;
import com.prog2.appointment.reservation.domain.model.ProfessionalName;
import com.prog2.appointment.reservation.infrastructure.availability.adapter.AvailabilityReservationSlotAdapter;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class AvailabilityReservationSlotAdapterTests {
    @Test
    void acceptsACompleteNameAndKeepsIncompleteNamesOptional() {
        var selection = ReservationProcessTests.SLOT;
        var availability = mock(GetAvailabilityUseCase.class);
        var adapter = new AvailabilityReservationSlotAdapter(availability);
        when(availability.get(selection.professionalId(), selection.date())).thenReturn(
                new Availability(selection.professionalId(), selection.date(), ZoneOffset.UTC,
                        List.of(selection.startTime()), "Ana", "Torres"),
                new Availability(selection.professionalId(), selection.date(), ZoneOffset.UTC,
                        List.of(selection.startTime()), "Ana", null),
                new Availability(selection.professionalId(), selection.date(), ZoneOffset.UTC,
                        List.of(selection.startTime()), " ", "Torres"),
                new Availability(selection.professionalId(), selection.date(), ZoneOffset.UTC,
                        List.of(selection.startTime()), "A".repeat(256), "Torres"));

        assertThat(adapter.verify(selection)).isEqualTo(new ProfessionalName("Ana", "Torres"));
        assertThat(adapter.verify(selection)).isNull();
        assertThat(adapter.verify(selection)).isNull();
        assertThat(adapter.verify(selection)).isNull();
        verify(availability, times(4)).get(selection.professionalId(), selection.date());
    }
}
