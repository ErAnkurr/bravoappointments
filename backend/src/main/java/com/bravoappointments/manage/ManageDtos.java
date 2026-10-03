package com.bravoappointments.manage;

import com.bravoappointments.appointment.AppointmentStatus;
import com.bravoappointments.tenant.TenantView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** Request/response shapes for the manager API. */
public final class ManageDtos {

    private ManageDtos() {}

    // ---- account / business ----

    public record MeResponse(boolean onboarded, TenantView tenant) {}

    public record OnboardingRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank
                    @Pattern(
                            regexp = "^[a-z0-9][a-z0-9-]{1,38}[a-z0-9]$",
                            message = "Use 3-40 characters: lowercase letters, numbers and hyphens")
                    String slug,
            @NotBlank String timezone) {}

    public record TenantUpdateRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank String timezone,
            @Size(max = 2000) String description,
            @Size(max = 255) String address,
            @Size(max = 40) String phone,
            @NotBlank @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Use a hex colour like #1a2b3c") String primaryColor,
            @Size(max = 500) String logoUrl) {}

    // ---- services ----

    public record ServiceRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 2000) String description,
            @Min(5) @Max(480) int durationMinutes,
            @Min(0) @Max(1_000_000) int priceCents,
            Boolean active) {}

    public record ServiceResponse(
            UUID id, String name, String description, int durationMinutes, int priceCents, boolean active) {}

    // ---- staff ----

    public record StaffRequest(
            @NotBlank @Size(max = 80) String displayName,
            @Size(max = 2000) String bio,
            @Size(max = 500) String photoUrl,
            Boolean active,
            @NotNull List<UUID> serviceIds) {}

    public record StaffResponse(
            UUID id, String displayName, String bio, String photoUrl, boolean active, List<UUID> serviceIds) {}

    /** One weekly working window. dayOfWeek: 1 = Monday ... 7 = Sunday. Times are the business's local time. */
    public record WindowDto(
            @Min(1) @Max(7) int dayOfWeek, @NotNull LocalTime startTime, @NotNull LocalTime endTime) {}

    public record HoursRequest(@NotNull @Valid List<WindowDto> windows) {}

    /** Date range in the business's timezone. Without times it blocks whole days (endDate inclusive). */
    public record TimeOffRequest(
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            LocalTime startTime,
            LocalTime endTime,
            @Size(max = 200) String reason) {}

    public record TimeOffResponse(UUID id, Instant startAt, Instant endAt, String reason) {}

    // ---- appointments ----

    public record AppointmentRow(
            UUID id,
            Instant startAt,
            Instant endAt,
            String status,
            UUID serviceId,
            String serviceName,
            UUID staffId,
            String staffName,
            String customerName,
            String customerEmail,
            String customerPhone,
            String notes,
            int priceCents) {}

    /** Both fields optional: change the status, reassign the barber, or both. */
    public record AppointmentUpdateRequest(AppointmentStatus status, UUID staffId) {}
}
