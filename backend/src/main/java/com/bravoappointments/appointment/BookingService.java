package com.bravoappointments.appointment;

import com.bravoappointments.catalog.ServiceOffering;
import com.bravoappointments.catalog.ServiceOfferingRepository;
import com.bravoappointments.catalog.Staff;
import com.bravoappointments.common.ApiException;
import com.bravoappointments.scheduling.AvailabilityService;
import com.bravoappointments.tenant.Tenant;
import com.bravoappointments.tenant.TenantLock;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Guest booking and cancellation. */
@Service
public class BookingService {

    private static final SecureRandom RANDOM = new SecureRandom();

    public record BookCommand(
            UUID serviceId,
            UUID staffId, // null = any available barber
            Instant startAt,
            String customerName,
            String customerEmail,
            String customerPhone,
            String notes) {}

    private final ServiceOfferingRepository serviceRepository;
    private final AppointmentRepository appointmentRepository;
    private final AvailabilityService availability;
    private final TenantLock tenantLock;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public BookingService(
            ServiceOfferingRepository serviceRepository,
            AppointmentRepository appointmentRepository,
            AvailabilityService availability,
            TenantLock tenantLock,
            ApplicationEventPublisher events,
            Clock clock) {
        this.serviceRepository = serviceRepository;
        this.appointmentRepository = appointmentRepository;
        this.availability = availability;
        this.tenantLock = tenantLock;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Books the slot, or throws 409 SLOT_UNAVAILABLE. The slot is re-validated server-side against
     * working hours, time off and existing appointments, so a stale or forged request can't book
     * something the calendar never offered.
     */
    @Transactional
    public Appointment book(Tenant tenant, BookCommand cmd) {
        UUID tenantId = tenant.getId();
        ServiceOffering service = serviceRepository
                .findByIdAndTenantId(cmd.serviceId(), tenantId)
                .filter(ServiceOffering::isActive)
                .orElseThrow(() -> ApiException.badRequest("SERVICE_UNAVAILABLE", "That service is not available"));

        List<Staff> candidates = availability.candidateStaff(tenantId, service.getId(), cmd.staffId());
        if (candidates.isEmpty()) {
            throw ApiException.badRequest("STAFF_UNAVAILABLE", "No barber is available for this service");
        }

        // From here until commit, nobody else can book for this tenant.
        tenantLock.acquire(tenantId);

        ZoneId zone = ZoneId.of(tenant.getTimezone());
        LocalDate date = cmd.startAt().atZone(zone).toLocalDate();
        List<UUID> candidateIds = candidates.stream().map(Staff::getId).toList();
        Map<UUID, Map<LocalDate, List<Instant>>> slots =
                availability.slotsByStaff(tenant, service.getDurationMinutes(), candidateIds, date, date);

        List<Staff> free = candidates.stream()
                .filter(s -> slots.get(s.getId()).get(date).contains(cmd.startAt()))
                .toList();
        if (free.isEmpty()) {
            throw slotTaken();
        }

        Staff chosen = free.get(0);
        if (free.size() > 1) {
            // "Any available": give it to the barber with the fewest bookings that day (ties: display order).
            Instant dayStart = date.atStartOfDay(zone).toInstant();
            Instant dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant();
            long lowest = Long.MAX_VALUE;
            for (Staff s : free) {
                long load = appointmentRepository.countActiveStartingBetween(tenantId, s.getId(), dayStart, dayEnd);
                if (load < lowest) {
                    lowest = load;
                    chosen = s;
                }
            }
        }

        Appointment appointment = new Appointment(
                tenantId,
                chosen.getId(),
                service.getId(),
                service.getName(),
                service.getPriceCents(),
                cmd.customerName().trim(),
                cmd.customerEmail().trim(),
                cmd.customerPhone().trim(),
                blankToNull(cmd.notes()),
                cmd.startAt(),
                cmd.startAt().plus(Duration.ofMinutes(service.getDurationMinutes())),
                newToken());
        try {
            appointmentRepository.saveAndFlush(appointment);
        } catch (DataIntegrityViolationException e) {
            // The exclusion constraint caught something the lock and slot check didn't.
            throw slotTaken();
        }
        events.publishEvent(new AppointmentEvent(appointment.getId(), AppointmentEvent.Type.BOOKED));
        return appointment;
    }

    @Transactional
    public Appointment cancelByToken(String token) {
        Appointment appointment = appointmentRepository
                .findByManagementToken(token)
                .orElseThrow(() -> ApiException.notFound("Booking"));
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            return appointment;
        }
        if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw ApiException.conflict("NOT_CANCELLABLE", "This booking can no longer be cancelled online");
        }
        if (!appointment.getStartAt().isAfter(clock.instant())) {
            throw ApiException.conflict("ALREADY_STARTED", "This appointment has already started");
        }
        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointmentRepository.save(appointment);
        events.publishEvent(new AppointmentEvent(appointment.getId(), AppointmentEvent.Type.CANCELLED));
        return appointment;
    }

    private static ApiException slotTaken() {
        return ApiException.conflict("SLOT_UNAVAILABLE", "That time was just taken. Please pick another slot.");
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
