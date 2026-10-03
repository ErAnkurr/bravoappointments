package com.bravoappointments.scheduling;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Pure slot maths: no Spring, no database, no clock. Everything it needs is passed in, which is
 * what makes it cheap to test (DST days included).
 *
 * A slot is the start instant of a bookable appointment. For one staff member and one calendar day
 * it is every grid step (e.g. every 15 minutes) inside a working window where
 * [start, start + duration) fits entirely inside the window and overlaps nothing in {@code blocked}
 * (time off and existing appointments). All ranges are half-open, so back-to-back appointments are fine.
 */
public final class SlotCalculator {

    /** A weekly working window. dayOfWeek is ISO (1 = Monday ... 7 = Sunday); times are wall-clock in the tenant's zone. */
    public record Window(int dayOfWeek, LocalTime start, LocalTime end) {}

    /** A half-open range [start, end) during which the staff member cannot take an appointment. */
    public record Range(Instant start, Instant end) {
        boolean overlaps(Instant from, Instant to) {
            return from.isBefore(end) && start.isBefore(to);
        }
    }

    public record Schedule(List<Window> windows, List<Range> blocked) {}

    private SlotCalculator() {}

    /**
     * @param date            the calendar day in {@code zone}
     * @param durationMinutes service length
     * @param stepMinutes     grid between consecutive slot starts, measured from the window start
     * @param earliest        slots starting before this instant are dropped (minimum notice)
     */
    public static List<Instant> slotsForDay(
            LocalDate date, ZoneId zone, int durationMinutes, int stepMinutes, Instant earliest, Schedule schedule) {
        if (durationMinutes <= 0 || stepMinutes <= 0) {
            throw new IllegalArgumentException("duration and step must be positive");
        }
        Duration duration = Duration.ofMinutes(durationMinutes);
        Duration step = Duration.ofMinutes(stepMinutes);
        int dayOfWeek = date.getDayOfWeek().getValue();

        TreeSet<Instant> slots = new TreeSet<>();
        for (Window window : schedule.windows()) {
            if (window.dayOfWeek() != dayOfWeek) {
                continue;
            }
            // Zone-aware conversion: on DST days the window's real length can be an hour more or less.
            Instant windowStart = date.atTime(window.start()).atZone(zone).toInstant();
            Instant windowEnd = date.atTime(window.end()).atZone(zone).toInstant();
            for (Instant start = windowStart; !start.plus(duration).isAfter(windowEnd); start = start.plus(step)) {
                if (start.isBefore(earliest)) {
                    continue;
                }
                if (isBlocked(schedule.blocked(), start, start.plus(duration))) {
                    continue;
                }
                slots.add(start);
            }
        }
        return new ArrayList<>(slots);
    }

    private static boolean isBlocked(List<Range> blocked, Instant from, Instant to) {
        for (Range range : blocked) {
            if (range.overlaps(from, to)) {
                return true;
            }
        }
        return false;
    }
}
