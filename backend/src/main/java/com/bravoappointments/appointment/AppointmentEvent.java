package com.bravoappointments.appointment;

import java.util.UUID;

/** Published inside the booking/cancel transaction; emails are sent only after it commits. */
public record AppointmentEvent(UUID appointmentId, Type type) {

    public enum Type {
        BOOKED,
        CANCELLED
    }
}
