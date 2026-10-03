package com.bravoappointments.scheduling;

import com.bravoappointments.appointment.Appointment;
import com.bravoappointments.appointment.AppointmentRepository;
import com.bravoappointments.catalog.Staff;
import com.bravoappointments.catalog.StaffRepository;
import com.bravoappointments.catalog.StaffServiceLink;
import com.bravoappointments.catalog.StaffServiceLinkRepository;
import com.bravoappointments.config.BravoProperties;
import com.bravoappointments.scheduling.SlotCalculator.Range;
import com.bravoappointments.scheduling.SlotCalculator.Schedule;
import com.bravoappointments.scheduling.SlotCalculator.Window;
import com.bravoappointments.tenant.Tenant;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Loads a tenant's schedules and runs them through {@link SlotCalculator}. */
@Service
public class AvailabilityService {

    private final WorkingHoursRepository hoursRepository;
    private final TimeOffRepository timeOffRepository;
    private final AppointmentRepository appointmentRepository;
    private final StaffRepository staffRepository;
    private final StaffServiceLinkRepository linkRepository;
    private final BravoProperties properties;
    private final Clock clock;

    public AvailabilityService(
            WorkingHoursRepository hoursRepository,
            TimeOffRepository timeOffRepository,
            AppointmentRepository appointmentRepository,
            StaffRepository staffRepository,
            StaffServiceLinkRepository linkRepository,
            BravoProperties properties,
            Clock clock) {
        this.hoursRepository = hoursRepository;
        this.timeOffRepository = timeOffRepository;
        this.appointmentRepository = appointmentRepository;
        this.staffRepository = staffRepository;
        this.linkRepository = linkRepository;
        this.properties = properties;
        this.clock = clock;
    }

    /** Active staff who perform the service, in display order; narrowed to one barber when staffId is given. */
    @Transactional(readOnly = true)
    public List<Staff> candidateStaff(UUID tenantId, UUID serviceId, UUID staffId) {
        Set<UUID> offering = linkRepository.findByTenantIdAndServiceId(tenantId, serviceId).stream()
                .map(StaffServiceLink::getStaffId)
                .collect(Collectors.toSet());
        return staffRepository.findByTenantIdAndActiveTrueOrderBySortOrderAscDisplayNameAsc(tenantId).stream()
                .filter(s -> offering.contains(s.getId()))
                .filter(s -> staffId == null || s.getId().equals(staffId))
                .toList();
    }

    /**
     * Free slot starts per staff member for every calendar day in [from, to] (tenant timezone).
     * Every requested staff id and every date appears in the result, with an empty list when nothing is free.
     */
    @Transactional(readOnly = true)
    public Map<UUID, Map<LocalDate, List<Instant>>> slotsByStaff(
            Tenant tenant, int durationMinutes, Collection<UUID> staffIds, LocalDate from, LocalDate to) {
        Map<UUID, Map<LocalDate, List<Instant>>> result = new LinkedHashMap<>();
        if (staffIds.isEmpty()) {
            return result;
        }

        ZoneId zone = ZoneId.of(tenant.getTimezone());
        UUID tenantId = tenant.getId();
        Instant rangeStart = from.atStartOfDay(zone).toInstant();
        Instant rangeEnd = to.plusDays(1).atStartOfDay(zone).toInstant();

        Map<UUID, List<Window>> windows = new HashMap<>();
        for (WorkingHours h : hoursRepository.findByStaffIdInAndTenantId(staffIds, tenantId)) {
            windows.computeIfAbsent(h.getStaffId(), k -> new ArrayList<>())
                    .add(new Window(h.getDayOfWeek(), h.getStartTime(), h.getEndTime()));
        }

        Map<UUID, List<Range>> blocked = new HashMap<>();
        for (TimeOff t : timeOffRepository.findOverlapping(tenantId, staffIds, rangeStart, rangeEnd)) {
            blocked.computeIfAbsent(t.getStaffId(), k -> new ArrayList<>()).add(new Range(t.getStartAt(), t.getEndAt()));
        }
        for (Appointment a : appointmentRepository.findActiveOverlapping(tenantId, staffIds, rangeStart, rangeEnd)) {
            blocked.computeIfAbsent(a.getStaffId(), k -> new ArrayList<>()).add(new Range(a.getStartAt(), a.getEndAt()));
        }

        BravoProperties.Booking rules = properties.booking();
        Instant now = clock.instant();
        Instant earliest = now.plus(Duration.ofMinutes(rules.minNoticeMinutes()));
        LocalDate lastBookableDay = LocalDate.ofInstant(now, zone).plusDays(rules.maxAdvanceDays());

        for (UUID staffId : staffIds) {
            Schedule schedule = new Schedule(
                    windows.getOrDefault(staffId, List.of()), blocked.getOrDefault(staffId, List.of()));
            Map<LocalDate, List<Instant>> perDay = new LinkedHashMap<>();
            for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
                List<Instant> slots = day.isAfter(lastBookableDay)
                        ? List.of()
                        : SlotCalculator.slotsForDay(
                                day, zone, durationMinutes, rules.slotStepMinutes(), earliest, schedule);
                perDay.put(day, slots);
            }
            result.put(staffId, perDay);
        }
        return result;
    }
}
