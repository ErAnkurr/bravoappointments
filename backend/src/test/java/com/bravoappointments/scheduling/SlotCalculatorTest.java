package com.bravoappointments.scheduling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bravoappointments.scheduling.SlotCalculator.Range;
import com.bravoappointments.scheduling.SlotCalculator.Schedule;
import com.bravoappointments.scheduling.SlotCalculator.Window;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class SlotCalculatorTest {

    private static final ZoneId VANCOUVER = ZoneId.of("America/Vancouver");
    private static final ZoneId LOS_ANGELES = ZoneId.of("America/Los_Angeles");
    private static final LocalDate TUESDAY = LocalDate.of(2026, 10, 6);
    private static final Instant LONG_AGO = Instant.parse("2020-01-01T00:00:00Z");

    private static Instant at(LocalDate date, String time) {
        return ZonedDateTime.of(date, LocalTime.parse(time), VANCOUVER).toInstant();
    }

    private static Window window(LocalDate date, String start, String end) {
        return new Window(date.getDayOfWeek().getValue(), LocalTime.parse(start), LocalTime.parse(end));
    }

    @Test
    void generatesEveryStepWhereTheServiceFits() {
        Schedule schedule = new Schedule(List.of(window(TUESDAY, "09:00", "12:00")), List.of());

        List<Instant> slots = SlotCalculator.slotsForDay(TUESDAY, VANCOUVER, 30, 15, LONG_AGO, schedule);

        assertEquals(11, slots.size()); // 09:00 ... 11:30
        assertEquals(at(TUESDAY, "09:00"), slots.get(0));
        assertEquals(at(TUESDAY, "11:30"), slots.get(10));
    }

    @Test
    void existingAppointmentBlocksOverlappingStartsButNotBackToBack() {
        Range busy = new Range(at(TUESDAY, "10:00"), at(TUESDAY, "10:30"));
        Schedule schedule = new Schedule(List.of(window(TUESDAY, "09:00", "12:00")), List.of(busy));

        List<Instant> slots = SlotCalculator.slotsForDay(TUESDAY, VANCOUVER, 30, 15, LONG_AGO, schedule);

        assertEquals(8, slots.size());
        assertTrue(slots.contains(at(TUESDAY, "09:30")), "ends exactly when the busy range starts");
        assertTrue(!slots.contains(at(TUESDAY, "09:45")), "would run into the busy range");
        assertTrue(!slots.contains(at(TUESDAY, "10:00")));
        assertTrue(!slots.contains(at(TUESDAY, "10:15")));
        assertTrue(slots.contains(at(TUESDAY, "10:30")), "starts exactly when the busy range ends");
    }

    @Test
    void minimumNoticeDropsEarlySlots() {
        Schedule schedule = new Schedule(List.of(window(TUESDAY, "09:00", "12:00")), List.of());

        List<Instant> slots = SlotCalculator.slotsForDay(TUESDAY, VANCOUVER, 30, 15, at(TUESDAY, "10:00"), schedule);

        assertEquals(at(TUESDAY, "10:00"), slots.get(0));
        assertEquals(7, slots.size()); // 10:00 ... 11:30
    }

    @Test
    void windowShorterThanServiceYieldsNothing() {
        Schedule schedule = new Schedule(List.of(window(TUESDAY, "09:00", "09:20")), List.of());

        assertTrue(SlotCalculator.slotsForDay(TUESDAY, VANCOUVER, 30, 15, LONG_AGO, schedule).isEmpty());
    }

    @Test
    void otherWeekdaysAreIgnored() {
        LocalDate wednesday = TUESDAY.plusDays(1);
        Schedule schedule = new Schedule(List.of(window(TUESDAY, "09:00", "12:00")), List.of());

        assertTrue(SlotCalculator.slotsForDay(wednesday, VANCOUVER, 30, 15, LONG_AGO, schedule).isEmpty());
    }

    @Test
    void splitShiftsLeaveTheLunchBreakEmpty() {
        Schedule schedule = new Schedule(
                List.of(window(TUESDAY, "09:00", "12:30"), window(TUESDAY, "13:30", "17:00")), List.of());

        List<Instant> slots = SlotCalculator.slotsForDay(TUESDAY, VANCOUVER, 30, 30, LONG_AGO, schedule);

        assertEquals(14, slots.size()); // 7 + 7
        assertTrue(slots.contains(at(TUESDAY, "12:00")));
        assertTrue(!slots.contains(at(TUESDAY, "12:30")));
        assertTrue(!slots.contains(at(TUESDAY, "13:00")));
        assertTrue(slots.contains(at(TUESDAY, "13:30")));
    }

    @Test
    void overlappingWindowsDoNotProduceDuplicateSlots() {
        Schedule schedule = new Schedule(
                List.of(window(TUESDAY, "09:00", "11:00"), window(TUESDAY, "10:00", "12:00")), List.of());

        List<Instant> slots = SlotCalculator.slotsForDay(TUESDAY, VANCOUVER, 60, 60, LONG_AGO, schedule);

        assertEquals(List.of(at(TUESDAY, "09:00"), at(TUESDAY, "10:00"), at(TUESDAY, "11:00")), slots);
    }

    // The DST tests use Los Angeles on purpose: current tz data has no DST transitions for America/Vancouver
    // after March 2026 (BC moved to permanent daylight time), so Vancouver can't exercise a fall-back day.

    @Test
    void springForwardDayHasNoSlotInTheSkippedHour() {
        // 2026-03-08 (Sunday) in Los Angeles: 02:00 PST jumps to 03:00 PDT. 01:00-04:00 wall clock is only 2 real hours.
        LocalDate springForward = LocalDate.of(2026, 3, 8);
        Schedule schedule = new Schedule(List.of(window(springForward, "01:00", "04:00")), List.of());

        List<Instant> slots = SlotCalculator.slotsForDay(springForward, LOS_ANGELES, 60, 60, LONG_AGO, schedule);

        assertEquals(2, slots.size());
        assertEquals(Instant.parse("2026-03-08T09:00:00Z"), slots.get(0)); // 01:00 PST
        assertEquals(Instant.parse("2026-03-08T10:00:00Z"), slots.get(1)); // 03:00 PDT
    }

    @Test
    void fallBackDayHasTheRepeatedHour() {
        // 2026-11-01 (Sunday): 02:00 PDT falls back to 01:00 PST, so 01:00-04:00 wall clock is 4 real hours.
        LocalDate fallBack = LocalDate.of(2026, 11, 1);
        Schedule schedule = new Schedule(List.of(window(fallBack, "01:00", "04:00")), List.of());

        List<Instant> slots = SlotCalculator.slotsForDay(fallBack, LOS_ANGELES, 60, 60, LONG_AGO, schedule);

        assertEquals(4, slots.size());
        assertEquals(Instant.parse("2026-11-01T08:00:00Z"), slots.get(0)); // 01:00 PDT (first pass)
        assertEquals(Instant.parse("2026-11-01T11:00:00Z"), slots.get(3)); // 03:00 PST
    }
}
