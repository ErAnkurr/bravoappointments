package com.bravoappointments.publicapi;

import com.bravoappointments.appointment.Appointment;
import com.bravoappointments.appointment.BookingService;
import com.bravoappointments.catalog.ServiceOffering;
import com.bravoappointments.catalog.ServiceOfferingRepository;
import com.bravoappointments.catalog.Staff;
import com.bravoappointments.catalog.StaffRepository;
import com.bravoappointments.catalog.StaffServiceLink;
import com.bravoappointments.catalog.StaffServiceLinkRepository;
import com.bravoappointments.common.ApiException;
import com.bravoappointments.scheduling.AvailabilityService;
import com.bravoappointments.tenant.Tenant;
import com.bravoappointments.tenant.TenantRepository;
import com.bravoappointments.tenant.TenantView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Everything a guest needs: no authentication. The tenant always comes from the URL slug or the booking token. */
@RestController
@RequestMapping("/api/public")
public class PublicController {

    private static final int MAX_AVAILABILITY_DAYS = 14;

    public record ServiceView(UUID id, String name, String description, int durationMinutes, int priceCents) {}

    public record StaffView(UUID id, String displayName, String bio, String photoUrl, List<UUID> serviceIds) {}

    public record ShopResponse(TenantView tenant, List<ServiceView> services, List<StaffView> staff) {}

    public record DaySlots(LocalDate date, List<Instant> slots) {}

    public record AvailabilityResponse(String timezone, List<DaySlots> days) {}

    public record BookRequest(
            @NotNull UUID serviceId,
            UUID staffId,
            @NotNull Instant startAt,
            @NotBlank @Size(max = 120) String customerName,
            @NotBlank @Email @Size(max = 320) String customerEmail,
            @NotBlank @Size(max = 40) String customerPhone,
            @Size(max = 1000) String notes) {}

    private final TenantRepository tenantRepository;
    private final ServiceOfferingRepository serviceRepository;
    private final StaffRepository staffRepository;
    private final StaffServiceLinkRepository linkRepository;
    private final BookingViewAssembler views;
    private final AvailabilityService availability;
    private final BookingService bookingService;

    public PublicController(
            TenantRepository tenantRepository,
            ServiceOfferingRepository serviceRepository,
            StaffRepository staffRepository,
            StaffServiceLinkRepository linkRepository,
            BookingViewAssembler views,
            AvailabilityService availability,
            BookingService bookingService) {
        this.tenantRepository = tenantRepository;
        this.serviceRepository = serviceRepository;
        this.staffRepository = staffRepository;
        this.linkRepository = linkRepository;
        this.views = views;
        this.availability = availability;
        this.bookingService = bookingService;
    }

    @GetMapping("/{slug}")
    public ShopResponse shop(@PathVariable String slug) {
        Tenant tenant = tenant(slug);
        UUID tenantId = tenant.getId();

        List<ServiceView> services = serviceRepository
                .findByTenantIdAndActiveTrueOrderBySortOrderAscNameAsc(tenantId).stream()
                .map(s -> new ServiceView(s.getId(), s.getName(), s.getDescription(), s.getDurationMinutes(), s.getPriceCents()))
                .toList();
        Map<UUID, List<UUID>> serviceIdsByStaff = linkRepository.findByTenantId(tenantId).stream()
                .collect(Collectors.groupingBy(
                        StaffServiceLink::getStaffId,
                        Collectors.mapping(StaffServiceLink::getServiceId, Collectors.toList())));
        List<StaffView> staff = staffRepository
                .findByTenantIdAndActiveTrueOrderBySortOrderAscDisplayNameAsc(tenantId).stream()
                .map(s -> new StaffView(
                        s.getId(), s.getDisplayName(), s.getBio(), s.getPhotoUrl(),
                        serviceIdsByStaff.getOrDefault(s.getId(), List.of())))
                .toList();

        return new ShopResponse(TenantView.from(tenant), services, staff);
    }

    @GetMapping("/{slug}/availability")
    public AvailabilityResponse availability(
            @PathVariable String slug,
            @RequestParam UUID serviceId,
            @RequestParam(required = false) UUID staffId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (to.isBefore(from)) {
            throw ApiException.badRequest("INVALID_RANGE", "'to' must not be before 'from'");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_AVAILABILITY_DAYS) {
            throw ApiException.badRequest("RANGE_TOO_LARGE", "Ask for at most " + MAX_AVAILABILITY_DAYS + " days at a time");
        }

        Tenant tenant = tenant(slug);
        ServiceOffering service = serviceRepository
                .findByIdAndTenantId(serviceId, tenant.getId())
                .filter(ServiceOffering::isActive)
                .orElseThrow(() -> ApiException.notFound("Service"));

        List<UUID> staffIds = availability.candidateStaff(tenant.getId(), service.getId(), staffId).stream()
                .map(Staff::getId)
                .toList();
        Map<UUID, Map<LocalDate, List<Instant>>> byStaff =
                availability.slotsByStaff(tenant, service.getDurationMinutes(), staffIds, from, to);

        // "Any barber" = union of everyone's free slots.
        List<DaySlots> days = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            TreeSet<Instant> merged = new TreeSet<>();
            for (Map<LocalDate, List<Instant>> perDay : byStaff.values()) {
                merged.addAll(perDay.getOrDefault(day, List.of()));
            }
            days.add(new DaySlots(day, new ArrayList<>(merged)));
        }
        return new AvailabilityResponse(tenant.getTimezone(), days);
    }

    @PostMapping("/{slug}/appointments")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingView book(@PathVariable String slug, @Valid @RequestBody BookRequest request) {
        Tenant tenant = tenant(slug);
        Appointment appointment = bookingService.book(
                tenant,
                new BookingService.BookCommand(
                        request.serviceId(),
                        request.staffId(),
                        request.startAt(),
                        request.customerName(),
                        request.customerEmail(),
                        request.customerPhone(),
                        request.notes()));
        return views.toView(tenant, appointment);
    }

    @GetMapping("/bookings/{token}")
    public BookingView booking(@PathVariable String token) {
        Appointment appointment = views.byToken(token);
        return views.toView(views.tenantOf(appointment), appointment);
    }

    @PostMapping("/bookings/{token}/cancel")
    public BookingView cancel(@PathVariable String token) {
        Appointment appointment = bookingService.cancelByToken(token);
        return views.toView(views.tenantOf(appointment), appointment);
    }

    private Tenant tenant(String slug) {
        return tenantRepository
                .findBySlug(slug)
                .filter(Tenant::isActive)
                .orElseThrow(() -> ApiException.notFound("Business"));
    }
}
