package com.bravoappointments.publicapi;

import java.time.Instant;

/** What a guest sees about their booking (confirmation page, cancel result). */
public record BookingView(
        String token,
        String status,
        String businessName,
        String businessSlug,
        String businessAddress,
        String businessPhone,
        String timezone,
        String serviceName,
        int priceCents,
        String staffName,
        Instant startAt,
        Instant endAt,
        String customerName,
        String customerEmail) {}
