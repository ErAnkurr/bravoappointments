package com.bravoappointments.publicapi;

import com.bravoappointments.appointment.Appointment;
import com.bravoappointments.appointment.AppointmentRepository;
import com.bravoappointments.catalog.StaffRepository;
import com.bravoappointments.common.ApiException;
import com.bravoappointments.tenant.Tenant;
import com.bravoappointments.tenant.TenantRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Looks up a guest's booking by its secret token and shapes it for the API. */
@Component
public class BookingViewAssembler {

    private final AppointmentRepository appointmentRepository;
    private final TenantRepository tenantRepository;
    private final StaffRepository staffRepository;

    public BookingViewAssembler(
            AppointmentRepository appointmentRepository,
            TenantRepository tenantRepository,
            StaffRepository staffRepository) {
        this.appointmentRepository = appointmentRepository;
        this.tenantRepository = tenantRepository;
        this.staffRepository = staffRepository;
    }

    @Transactional(readOnly = true)
    public Appointment byToken(String token) {
        return appointmentRepository.findByManagementToken(token).orElseThrow(() -> ApiException.notFound("Booking"));
    }

    @Transactional(readOnly = true)
    public Tenant tenantOf(Appointment appointment) {
        return tenantRepository.findById(appointment.getTenantId()).orElseThrow(() -> ApiException.notFound("Business"));
    }

    @Transactional(readOnly = true)
    public BookingView toView(Tenant tenant, Appointment a) {
        String staffName = staffRepository.findById(a.getStaffId()).map(s -> s.getDisplayName()).orElse("");
        return new BookingView(
                a.getManagementToken(),
                a.getStatus().name(),
                tenant.getName(),
                tenant.getSlug(),
                tenant.getAddress(),
                tenant.getPhone(),
                tenant.getTimezone(),
                a.getServiceName(),
                a.getPriceCents(),
                staffName,
                a.getStartAt(),
                a.getEndAt(),
                a.getCustomerName(),
                a.getCustomerEmail());
    }
}
