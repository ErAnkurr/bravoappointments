package com.bravoappointments.manage;

import com.bravoappointments.appointment.Appointment;
import com.bravoappointments.appointment.AppointmentEvent;
import com.bravoappointments.appointment.AppointmentRepository;
import com.bravoappointments.appointment.AppointmentStatus;
import com.bravoappointments.catalog.Staff;
import com.bravoappointments.catalog.StaffRepository;
import com.bravoappointments.catalog.StaffServiceLinkRepository;
import com.bravoappointments.common.ApiException;
import com.bravoappointments.manage.ManageDtos.AppointmentRow;
import com.bravoappointments.manage.ManageDtos.AppointmentUpdateRequest;
import com.bravoappointments.tenant.Tenant;
import com.bravoappointments.tenant.TenantLock;
import com.bravoappointments.tenant.TenantRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The manager's view of the calendar: list, change status, assign a barber. */
@Service
public class AppointmentAdminService {

    private static final int MAX_RANGE_DAYS = 93;

    private final AppointmentRepository appointmentRepository;
    private final StaffRepository staffRepository;
    private final StaffServiceLinkRepository linkRepository;
    private final TenantRepository tenantRepository;
    private final TenantLock tenantLock;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public AppointmentAdminService(
            AppointmentRepository appointmentRepository,
            StaffRepository staffRepository,
            StaffServiceLinkRepository linkRepository,
            TenantRepository tenantRepository,
            TenantLock tenantLock,
            ApplicationEventPublisher events,
            Clock clock) {
        this.appointmentRepository = appointmentRepository;
        this.staffRepository = staffRepository;
        this.linkRepository = linkRepository;
        this.tenantRepository = tenantRepository;
        this.tenantLock = tenantLock;
        this.events = events;
        this.clock = clock;
    }

    /** Appointments starting on any day in [from, to] (business timezone). Defaults to today through +7 days. */
    @Transactional(readOnly = true)
    public List<AppointmentRow> list(UUID tenantId, LocalDate from, LocalDate to, UUID staffId) {
        Tenant tenant = tenant(tenantId);
        ZoneId zone = ZoneId.of(tenant.getTimezone());
        LocalDate start = from != null ? from : LocalDate.now(clock.withZone(zone));
        LocalDate end = to != null ? to : start.plusDays(7);
        if (end.isBefore(start)) {
            throw ApiException.badRequest("INVALID_RANGE", "'to' must not be before 'from'");
        }
        if (ChronoUnit.DAYS.between(start, end) > MAX_RANGE_DAYS) {
            throw ApiException.badRequest("RANGE_TOO_LARGE", "Ask for at most " + MAX_RANGE_DAYS + " days at a time");
        }
        Instant rangeStart = start.atStartOfDay(zone).toInstant();
        Instant rangeEnd = end.plusDays(1).atStartOfDay(zone).toInstant();

        List<Appointment> appointments = staffId != null
                ? appointmentRepository.findByTenantIdAndStaffIdAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(
                        tenantId, staffId, rangeStart, rangeEnd)
                : appointmentRepository.findByTenantIdAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(
                        tenantId, rangeStart, rangeEnd);

        Map<UUID, String> staffNames = staffNames(tenantId);
        return appointments.stream().map(a -> toRow(a, staffNames)).toList();
    }

    @Transactional
    public AppointmentRow update(UUID tenantId, UUID id, AppointmentUpdateRequest request) {
        Appointment appointment = appointmentRepository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> ApiException.notFound("Appointment"));
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw ApiException.conflict("ALREADY_CANCELLED", "A cancelled appointment can't be changed. Ask the guest to rebook.");
        }

        boolean cancelling = false;
        if (request.status() != null && request.status() != appointment.getStatus()) {
            cancelling = request.status() == AppointmentStatus.CANCELLED;
            appointment.setStatus(request.status());
        }

        if (request.staffId() != null && !request.staffId().equals(appointment.getStaffId())) {
            reassign(tenantId, appointment, request.staffId());
        }

        try {
            appointmentRepository.saveAndFlush(appointment);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("SLOT_UNAVAILABLE", "That barber already has an appointment at this time");
        }
        if (cancelling) {
            events.publishEvent(new AppointmentEvent(appointment.getId(), AppointmentEvent.Type.CANCELLED));
        }

        return toRow(appointment, staffNames(tenantId));
    }

    /** Managers may override working hours, but never double-book a barber. */
    private void reassign(UUID tenantId, Appointment appointment, UUID newStaffId) {
        tenantLock.acquire(tenantId);
        Staff staff = staffRepository
                .findByIdAndTenantId(newStaffId, tenantId)
                .filter(Staff::isActive)
                .orElseThrow(() -> ApiException.badRequest("INVALID_STAFF", "That barber isn't available"));
        if (!linkRepository.existsByStaffIdAndServiceIdAndTenantId(staff.getId(), appointment.getServiceId(), tenantId)) {
            throw ApiException.badRequest("STAFF_DOES_NOT_OFFER_SERVICE", staff.getDisplayName() + " doesn't offer this service");
        }
        boolean busy = !appointmentRepository
                .findActiveOverlapping(tenantId, List.of(staff.getId()), appointment.getStartAt(), appointment.getEndAt())
                .isEmpty();
        if (busy) {
            throw ApiException.conflict("SLOT_UNAVAILABLE", staff.getDisplayName() + " already has an appointment at this time");
        }
        appointment.setStaffId(staff.getId());
    }

    private Tenant tenant(UUID tenantId) {
        return tenantRepository.findById(tenantId).orElseThrow(() -> ApiException.notFound("Business"));
    }

    private Map<UUID, String> staffNames(UUID tenantId) {
        return staffRepository.findByTenantIdOrderBySortOrderAscDisplayNameAsc(tenantId).stream()
                .collect(Collectors.toMap(Staff::getId, Staff::getDisplayName));
    }

    private static AppointmentRow toRow(Appointment a, Map<UUID, String> staffNames) {
        return new AppointmentRow(
                a.getId(),
                a.getStartAt(),
                a.getEndAt(),
                a.getStatus().name(),
                a.getServiceId(),
                a.getServiceName(),
                a.getStaffId(),
                staffNames.getOrDefault(a.getStaffId(), ""),
                a.getCustomerName(),
                a.getCustomerEmail(),
                a.getCustomerPhone(),
                a.getNotes(),
                a.getPriceCents());
    }
}
