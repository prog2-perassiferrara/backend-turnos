package com.prog2.appointment.availability;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.prog2.appointment.availability.domain.enums.OccupancyStatus;
import com.prog2.appointment.availability.domain.model.AppointmentOccupancy;
import com.prog2.appointment.availability.domain.model.ProfessionalAgenda;
import com.prog2.appointment.availability.domain.model.WeeklySchedule;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Reglas con resultados conocidos, sin HTTP ni cálculo del esperado mediante el algoritmo productivo. */
@Timeout(5)
class AvailabilityRulesTests {
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);

    @Test
    void includesOnlyCompleteSlotsAndIncludesTheExactClosingBoundary() {
        assertThat(schedule("09:00", "10:10", 30, true).startsOn(MONDAY))
                .containsExactly(LocalTime.of(9, 0), LocalTime.of(9, 30));
        assertThat(schedule("09:00", "10:00", 30, true).startsOn(MONDAY))
                .containsExactly(LocalTime.of(9, 0), LocalTime.of(9, 30));
    }

    @Test
    void disabledOrOtherWeekdaySchedulesDoNotOfferSlots() {
        assertThat(schedule("09:00", "10:00", 30, false).startsOn(MONDAY)).isEmpty();
        assertThat(schedule("09:00", "10:00", 30, true).startsOn(MONDAY.plusDays(1))).isEmpty();
    }

    @Test
    void shortAndLargeDurationsNeverWrapAroundMidnight() {
        assertThat(schedule("23:40", "23:59", 30, true).startsOn(MONDAY)).isEmpty();
        assertThat(schedule("09:00", "10:00", Integer.MAX_VALUE, true).startsOn(MONDAY)).isEmpty();
        assertThat(schedule("23:00", "23:59", 29, true).startsOn(MONDAY))
                .containsExactly(LocalTime.of(23, 0), LocalTime.of(23, 29));
    }

    @Test
    void preservesSecondsAndOrdersTheUnionWithoutDuplicateStarts() {
        var agenda = new ProfessionalAgenda(15, true, List.of(
                schedule("09:30:15", "10:30:15", 30, true),
                schedule("09:00:15", "10:00:15", 30, true)));
        assertThat(agenda.candidateStarts(MONDAY)).containsExactly(
                LocalTime.of(9, 0, 15), LocalTime.of(9, 30, 15), LocalTime.of(10, 0, 15));
    }

    @Test
    void disabledProfessionalAndAnEmptyAgendaHaveNoCandidates() {
        var disabled = new ProfessionalAgenda(15, false, List.of(schedule("09:00", "10:00", 30, true)));
        assertThat(disabled.candidateStarts(MONDAY)).isEmpty();
        assertThat(new ProfessionalAgenda(15, true, List.of()).candidateStarts(MONDAY)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsNonPositiveDuration(int duration) {
        assertThatThrownBy(() -> schedule("09:00", "10:00", duration, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"09:00", "08:59"})
    void rejectsZeroOrReversedInterval(String end) {
        assertThatThrownBy(() -> schedule("09:00", end, 30, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void heldPositionStopsBlockingAtTheExactUtcExpiration() {
        Instant expiry = Instant.parse("2026-11-02T12:00:00Z");
        var held = new AppointmentOccupancy(MONDAY, LocalTime.of(9, 0), OccupancyStatus.HELD, expiry);
        assertThat(held.blocksAt(expiry.minusNanos(1))).isTrue();
        assertThat(held.blocksAt(expiry)).isFalse();
        assertThat(held.blocksAt(expiry.plusNanos(1))).isFalse();
    }

    @Test
    void confirmedPositionStillBlocksRegardlessOfAnotherHoldExpiration() {
        var confirmed = new AppointmentOccupancy(MONDAY, LocalTime.of(9, 0), OccupancyStatus.CONFIRMED, null);
        assertThat(confirmed.blocksAt(Instant.parse("2030-01-01T00:00:00Z"))).isTrue();
    }

    @Test
    void aHeldPositionWithoutAnExpirationCannotBeRepresentedAsFree() {
        LocalTime start = LocalTime.of(9, 0);
        assertThatThrownBy(() -> new AppointmentOccupancy(MONDAY, start, OccupancyStatus.HELD, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static WeeklySchedule schedule(String start, String end, int duration, boolean enabled) {
        return new WeeklySchedule(DayOfWeek.MONDAY, LocalTime.parse(start), LocalTime.parse(end), duration, enabled);
    }
}
