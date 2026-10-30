package com.prog2.appointment.availability;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.prog2.appointment.availability.application.exception.AvailabilityException;
import com.prog2.appointment.availability.application.usecases.GetAvailabilityUseCaseImpl;
import com.prog2.appointment.availability.domain.enums.AvailabilityFailure;
import com.prog2.appointment.availability.domain.enums.OccupancyStatus;
import com.prog2.appointment.availability.domain.exception.AvailabilityGatewayException;
import com.prog2.appointment.availability.domain.model.AppointmentOccupancy;
import com.prog2.appointment.availability.domain.model.ProfessionalAgenda;
import com.prog2.appointment.availability.domain.model.WeeklySchedule;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Ejecuta el caso real con puertos controlados; protege fronteras temporales y orden de los efectos. */
class GetAvailabilityUseCaseTests {
    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final LocalDate DATE = LocalDate.of(2026, 11, 2);
    private static final Instant NOW = Instant.parse("2026-11-02T12:00:00Z");

    @Test
    void excludesStartedSlotsConfirmedPositionsAndActiveHoldsWithConfirmedPrecedence() {
        var useCase = new GetAvailabilityUseCaseImpl(_ -> Optional.of(agenda(true)), (_, date) -> List.of(
                new AppointmentOccupancy(date, LocalTime.of(9, 30), OccupancyStatus.HELD, NOW.plusSeconds(1)),
                new AppointmentOccupancy(date, LocalTime.of(10, 0), OccupancyStatus.HELD, NOW),
                new AppointmentOccupancy(date, LocalTime.of(10, 30), OccupancyStatus.HELD, NOW.minusSeconds(1)),
                new AppointmentOccupancy(date, LocalTime.of(10, 30), OccupancyStatus.CONFIRMED, null)),
                Clock.fixed(NOW, ZoneOffset.UTC), ZONE);
        var result = useCase.get(15, DATE);
        assertThat(result.availableStartTimes()).containsExactly(LocalTime.of(10, 0));
        assertThat(result.professionalId()).isEqualTo(15);
        assertThat(result.date()).isEqualTo(DATE);
        assertThat(result.timeZone()).isEqualTo(ZONE);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void rejectsInvalidIdBeforeEitherProvider(long id) {
        var useCase = withoutProviders();
        assertThatThrownBy(() -> useCase.get(id, DATE)).isInstanceOf(AvailabilityException.class)
                .hasMessage("INVALID_AVAILABILITY_QUERY");
    }

    @Test
    void rejectsNullAndPastDateBeforeEitherProvider() {
        var useCase = withoutProviders();
        LocalDate yesterday = DATE.minusDays(1);
        assertThatThrownBy(() -> useCase.get(15, null)).hasMessage("INVALID_AVAILABILITY_QUERY");
        assertThatThrownBy(() -> useCase.get(15, yesterday)).hasMessage("INVALID_AVAILABILITY_QUERY");
    }

    @Test
    void usesTheAttentionDateInsteadOfTheUtcDateAndDoesNotAddAFutureHorizon() {
        Instant afterUtcMidnight = Instant.parse("2026-11-03T01:00:00Z");
        var calls = new AtomicInteger();
        var useCase = new GetAvailabilityUseCaseImpl(_ -> {
            calls.incrementAndGet();
            return Optional.of(new ProfessionalAgenda(15, true, List.of()));
        }, (_, _) -> { throw new AssertionError("An empty agenda needs no occupancies"); },
                Clock.fixed(afterUtcMidnight, ZoneOffset.UTC), ZONE);
        assertThat(useCase.get(15, DATE).availableStartTimes()).isEmpty();
        assertThat(useCase.get(15, LocalDate.of(9999, 12, 31)).availableStartTimes()).isEmpty();
        assertThat(calls).hasValue(2);
    }

    @Test
    void distinguishesMissingAndDisabledProfessionalWithoutCallingCentral() {
        var centralCalls = new AtomicInteger();
        var missing = new GetAvailabilityUseCaseImpl(_ -> Optional.empty(), (_, _) -> {
            centralCalls.incrementAndGet();
            return List.of();
        }, Clock.fixed(NOW, ZoneOffset.UTC), ZONE);
        assertThatThrownBy(() -> missing.get(15, DATE)).hasMessage("PROFESSIONAL_NOT_FOUND");
        var disabled = new GetAvailabilityUseCaseImpl(_ -> Optional.of(agenda(false)), (_, _) -> {
            centralCalls.incrementAndGet();
            return List.of();
        }, Clock.fixed(NOW, ZoneOffset.UTC), ZONE);
        assertThatThrownBy(() -> disabled.get(15, DATE)).hasMessage("PROFESSIONAL_DISABLED");
        assertThat(centralCalls).hasValue(0);
    }

    @Test
    void doesNotNeedOccupanciesWhenAllCandidatesHaveAlreadyStarted() {
        var useCase = new GetAvailabilityUseCaseImpl(_ -> Optional.of(agenda(true)), (_, _) -> {
            throw new AssertionError("No candidate should reach central");
        }, Clock.fixed(NOW.plusSeconds(7200), ZoneOffset.UTC), ZONE);
        assertThat(useCase.get(15, DATE).availableStartTimes()).isEmpty();
    }

    @Test
    void readsTheClockAgainAfterHttpAndDoesNotReofferAStartedSlot() {
        var instant = new AtomicReference<>(NOW.minusSeconds(1));
        Clock clock = movingClock(instant);
        var useCase = new GetAvailabilityUseCaseImpl(_ -> Optional.of(agenda(true)), (_, _) -> {
            instant.set(NOW.plusSeconds(1800));
            return List.of();
        }, clock, ZONE);
        assertThat(useCase.get(15, DATE).availableStartTimes())
                .containsExactly(LocalTime.of(10, 0), LocalTime.of(10, 30));
    }

    @Test
    void aQueryAdmittedBeforeMidnightDoesNotReturnYesterdayAfterItsResponse() {
        var instant = new AtomicReference<>(Instant.parse("2026-11-03T02:57:00Z"));
        var late = new ProfessionalAgenda(15, true, List.of(new WeeklySchedule(DayOfWeek.MONDAY,
                LocalTime.of(23, 58), LocalTime.of(23, 59, 59), 1, true)));
        var useCase = new GetAvailabilityUseCaseImpl(_ -> Optional.of(late), (_, _) -> {
            instant.set(Instant.parse("2026-11-03T03:00:00Z"));
            return List.of();
        }, movingClock(instant), ZONE);
        assertThat(useCase.get(15, DATE).availableStartTimes()).isEmpty();
    }

    @Test
    void providerFailureIsNotConvertedToAnEmptyResult() {
        var useCase = new GetAvailabilityUseCaseImpl(_ -> Optional.of(agenda(true)), (_, _) -> {
            throw new AvailabilityGatewayException(AvailabilityFailure.OCCUPANCIES_UNAVAILABLE);
        }, Clock.fixed(NOW, ZoneOffset.UTC), ZONE);
        assertThatThrownBy(() -> useCase.get(15, DATE)).isInstanceOf(AvailabilityGatewayException.class);
    }

    private static ProfessionalAgenda agenda(boolean enabled) {
        return new ProfessionalAgenda(15, enabled, List.of(new WeeklySchedule(DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(11, 0), 30, true)));
    }

    private static GetAvailabilityUseCaseImpl withoutProviders() {
        return new GetAvailabilityUseCaseImpl(_ -> { throw new AssertionError("Unexpected catalog call"); },
                (_, _) -> { throw new AssertionError("Unexpected central call"); },
                Clock.fixed(NOW, ZoneOffset.UTC), ZONE);
    }

    private static Clock movingClock(AtomicReference<Instant> instant) {
        return new Clock() {
            @Override public ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
            @Override public Instant instant() { return instant.get(); }
        };
    }
}
