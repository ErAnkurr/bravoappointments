package com.bravoappointments.manage;

import com.bravoappointments.catalog.ServiceOffering;
import com.bravoappointments.catalog.ServiceOfferingRepository;
import com.bravoappointments.catalog.Staff;
import com.bravoappointments.catalog.StaffRepository;
import com.bravoappointments.catalog.StaffServiceLink;
import com.bravoappointments.catalog.StaffServiceLinkRepository;
import com.bravoappointments.common.ApiException;
import com.bravoappointments.manage.ManageDtos.ServiceRequest;
import com.bravoappointments.manage.ManageDtos.ServiceResponse;
import com.bravoappointments.manage.ManageDtos.StaffRequest;
import com.bravoappointments.manage.ManageDtos.StaffResponse;
import com.bravoappointments.manage.ManageDtos.TimeOffRequest;
import com.bravoappointments.manage.ManageDtos.TimeOffResponse;
import com.bravoappointments.manage.ManageDtos.WindowDto;
import com.bravoappointments.scheduling.TimeOff;
import com.bravoappointments.scheduling.TimeOffRepository;
import com.bravoappointments.scheduling.WorkingHours;
import com.bravoappointments.scheduling.WorkingHoursRepository;
import com.bravoappointments.tenant.Tenant;
import com.bravoappointments.tenant.TenantRepository;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Services, staff, weekly hours and time off. Every method is scoped by the caller's tenantId. */
@Service
public class CatalogAdminService {

    private final ServiceOfferingRepository serviceRepository;
    private final StaffRepository staffRepository;
    private final StaffServiceLinkRepository linkRepository;
    private final WorkingHoursRepository hoursRepository;
    private final TimeOffRepository timeOffRepository;
    private final TenantRepository tenantRepository;

    public CatalogAdminService(
            ServiceOfferingRepository serviceRepository,
            StaffRepository staffRepository,
            StaffServiceLinkRepository linkRepository,
            WorkingHoursRepository hoursRepository,
            TimeOffRepository timeOffRepository,
            TenantRepository tenantRepository) {
        this.serviceRepository = serviceRepository;
        this.staffRepository = staffRepository;
        this.linkRepository = linkRepository;
        this.hoursRepository = hoursRepository;
        this.timeOffRepository = timeOffRepository;
        this.tenantRepository = tenantRepository;
    }

    // ---------------------------------------------------------------- services

    @Transactional(readOnly = true)
    public List<ServiceResponse> listServices(UUID tenantId) {
        return serviceRepository.findByTenantIdOrderBySortOrderAscNameAsc(tenantId).stream()
                .map(CatalogAdminService::toResponse)
                .toList();
    }

    @Transactional
    public ServiceResponse createService(UUID tenantId, ServiceRequest r) {
        ServiceOffering service = new ServiceOffering(
                tenantId,
                r.name().trim(),
                blankToNull(r.description()),
                r.durationMinutes(),
                r.priceCents(),
                (int) serviceRepository.countByTenantId(tenantId));
        if (r.active() != null) {
            service.setActive(r.active());
        }
        return toResponse(serviceRepository.save(service));
    }

    @Transactional
    public ServiceResponse updateService(UUID tenantId, UUID id, ServiceRequest r) {
        ServiceOffering service = serviceRepository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> ApiException.notFound("Service"));
        service.setName(r.name().trim());
        service.setDescription(blankToNull(r.description()));
        service.setDurationMinutes(r.durationMinutes());
        service.setPriceCents(r.priceCents());
        if (r.active() != null) {
            service.setActive(r.active());
        }
        return toResponse(serviceRepository.save(service));
    }

    private static ServiceResponse toResponse(ServiceOffering s) {
        return new ServiceResponse(
                s.getId(), s.getName(), s.getDescription(), s.getDurationMinutes(), s.getPriceCents(), s.isActive());
    }

    // ------------------------------------------------------------------- staff

    @Transactional(readOnly = true)
    public List<StaffResponse> listStaff(UUID tenantId) {
        Map<UUID, List<UUID>> serviceIdsByStaff = linkRepository.findByTenantId(tenantId).stream()
                .collect(Collectors.groupingBy(
                        StaffServiceLink::getStaffId,
                        Collectors.mapping(StaffServiceLink::getServiceId, Collectors.toList())));
        return staffRepository.findByTenantIdOrderBySortOrderAscDisplayNameAsc(tenantId).stream()
                .map(s -> toResponse(s, serviceIdsByStaff.getOrDefault(s.getId(), List.of())))
                .toList();
    }

    @Transactional
    public StaffResponse createStaff(UUID tenantId, StaffRequest r) {
        List<UUID> serviceIds = validatedServiceIds(tenantId, r.serviceIds());
        Staff staff = new Staff(
                tenantId,
                r.displayName().trim(),
                blankToNull(r.bio()),
                blankToNull(r.photoUrl()),
                (int) staffRepository.countByTenantId(tenantId));
        if (r.active() != null) {
            staff.setActive(r.active());
        }
        staff = staffRepository.saveAndFlush(staff);
        saveLinks(tenantId, staff.getId(), serviceIds);
        return toResponse(staff, serviceIds);
    }

    @Transactional
    public StaffResponse updateStaff(UUID tenantId, UUID id, StaffRequest r) {
        Staff staff = requireStaff(tenantId, id);
        List<UUID> serviceIds = validatedServiceIds(tenantId, r.serviceIds());
        staff.setDisplayName(r.displayName().trim());
        staff.setBio(blankToNull(r.bio()));
        staff.setPhotoUrl(blankToNull(r.photoUrl()));
        if (r.active() != null) {
            staff.setActive(r.active());
        }
        // The bulk delete flushes the edits above first, then clears the persistence context.
        linkRepository.deleteForStaff(staff.getId(), tenantId);
        saveLinks(tenantId, staff.getId(), serviceIds);
        return toResponse(staff, serviceIds);
    }

    private void saveLinks(UUID tenantId, UUID staffId, List<UUID> serviceIds) {
        List<StaffServiceLink> links = new ArrayList<>();
        for (UUID serviceId : serviceIds) {
            links.add(new StaffServiceLink(tenantId, staffId, serviceId));
        }
        linkRepository.saveAll(links);
    }

    /** Every id must be one of this tenant's services; duplicates are collapsed. */
    private List<UUID> validatedServiceIds(UUID tenantId, List<UUID> requested) {
        List<UUID> distinct = requested.stream().distinct().toList();
        if (distinct.isEmpty()) {
            return distinct;
        }
        if (serviceRepository.findByIdInAndTenantId(distinct, tenantId).size() != distinct.size()) {
            throw ApiException.badRequest("INVALID_SERVICE", "One or more services don't exist");
        }
        return distinct;
    }

    private Staff requireStaff(UUID tenantId, UUID id) {
        return staffRepository.findByIdAndTenantId(id, tenantId).orElseThrow(() -> ApiException.notFound("Barber"));
    }

    private static StaffResponse toResponse(Staff s, List<UUID> serviceIds) {
        return new StaffResponse(s.getId(), s.getDisplayName(), s.getBio(), s.getPhotoUrl(), s.isActive(), serviceIds);
    }

    // ------------------------------------------------------------ weekly hours

    @Transactional(readOnly = true)
    public List<WindowDto> getHours(UUID tenantId, UUID staffId) {
        requireStaff(tenantId, staffId);
        return hoursRepository.findByStaffIdAndTenantIdOrderByDayOfWeekAscStartTimeAsc(staffId, tenantId).stream()
                .map(h -> new WindowDto(h.getDayOfWeek(), h.getStartTime(), h.getEndTime()))
                .toList();
    }

    /** Replaces the whole week. Windows on the same day must not overlap. */
    @Transactional
    public List<WindowDto> replaceHours(UUID tenantId, UUID staffId, List<WindowDto> windows) {
        requireStaff(tenantId, staffId);
        List<WindowDto> sorted = windows.stream()
                .sorted(Comparator.comparingInt(WindowDto::dayOfWeek).thenComparing(WindowDto::startTime))
                .toList();
        for (int i = 0; i < sorted.size(); i++) {
            WindowDto w = sorted.get(i);
            if (!w.endTime().isAfter(w.startTime())) {
                throw ApiException.badRequest("INVALID_HOURS", "A window must end after it starts");
            }
            if (i > 0) {
                WindowDto prev = sorted.get(i - 1);
                if (prev.dayOfWeek() == w.dayOfWeek() && w.startTime().isBefore(prev.endTime())) {
                    throw ApiException.badRequest("HOURS_OVERLAP", "Working windows on the same day can't overlap");
                }
            }
        }
        hoursRepository.deleteForStaff(staffId, tenantId);
        List<WorkingHours> rows = new ArrayList<>();
        for (WindowDto w : sorted) {
            rows.add(new WorkingHours(tenantId, staffId, w.dayOfWeek(), w.startTime(), w.endTime()));
        }
        hoursRepository.saveAll(rows);
        return sorted;
    }

    // ---------------------------------------------------------------- time off

    @Transactional(readOnly = true)
    public List<TimeOffResponse> listTimeOff(UUID tenantId, UUID staffId) {
        requireStaff(tenantId, staffId);
        return timeOffRepository.findByStaffIdAndTenantIdOrderByStartAtAsc(staffId, tenantId).stream()
                .map(CatalogAdminService::toResponse)
                .toList();
    }

    @Transactional
    public TimeOffResponse addTimeOff(UUID tenantId, UUID staffId, TimeOffRequest r) {
        requireStaff(tenantId, staffId);
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow(() -> ApiException.notFound("Business"));
        ZoneId zone = ZoneId.of(tenant.getTimezone());

        LocalTime startTime = r.startTime() != null ? r.startTime() : LocalTime.MIDNIGHT;
        Instant start = r.startDate().atTime(startTime).atZone(zone).toInstant();
        Instant end = r.endTime() != null
                ? r.endDate().atTime(r.endTime()).atZone(zone).toInstant()
                : r.endDate().plusDays(1).atStartOfDay(zone).toInstant();
        if (!end.isAfter(start)) {
            throw ApiException.badRequest("INVALID_RANGE", "Time off must end after it starts");
        }
        TimeOff saved = timeOffRepository.save(new TimeOff(tenantId, staffId, start, end, blankToNull(r.reason())));
        return toResponse(saved);
    }

    @Transactional
    public void deleteTimeOff(UUID tenantId, UUID staffId, UUID timeOffId) {
        TimeOff timeOff = timeOffRepository
                .findByIdAndStaffIdAndTenantId(timeOffId, staffId, tenantId)
                .orElseThrow(() -> ApiException.notFound("Time off"));
        timeOffRepository.delete(timeOff);
    }

    private static TimeOffResponse toResponse(TimeOff t) {
        return new TimeOffResponse(t.getId(), t.getStartAt(), t.getEndAt(), t.getReason());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
