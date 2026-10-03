package com.bravoappointments.notification;

import com.bravoappointments.appointment.Appointment;
import com.bravoappointments.appointment.AppointmentEvent;
import com.bravoappointments.appointment.AppointmentRepository;
import com.bravoappointments.catalog.Staff;
import com.bravoappointments.catalog.StaffRepository;
import com.bravoappointments.config.BravoProperties;
import com.bravoappointments.tenant.Tenant;
import com.bravoappointments.tenant.TenantRepository;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.HtmlUtils;

/** Emails the guest after a booking or cancellation has been committed. Failures are logged, never thrown. */
@Component
public class AppointmentNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(AppointmentNotificationListener.class);
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' h:mm a z", Locale.ENGLISH);

    private final AppointmentRepository appointmentRepository;
    private final TenantRepository tenantRepository;
    private final StaffRepository staffRepository;
    private final EmailSender emailSender;
    private final BravoProperties properties;

    public AppointmentNotificationListener(
            AppointmentRepository appointmentRepository,
            TenantRepository tenantRepository,
            StaffRepository staffRepository,
            EmailSender emailSender,
            BravoProperties properties) {
        this.appointmentRepository = appointmentRepository;
        this.tenantRepository = tenantRepository;
        this.staffRepository = staffRepository;
        this.emailSender = emailSender;
        this.properties = properties;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentEvent(AppointmentEvent event) {
        try {
            Appointment a = appointmentRepository.findById(event.appointmentId()).orElse(null);
            if (a == null) {
                return;
            }
            Tenant tenant = tenantRepository.findById(a.getTenantId()).orElse(null);
            Staff staff = staffRepository.findById(a.getStaffId()).orElse(null);
            if (tenant == null || staff == null) {
                return;
            }

            String when = WHEN.format(a.getStartAt().atZone(ZoneId.of(tenant.getTimezone())));
            String base = properties.publicBaseUrl().replaceAll("/+$", "");
            String link = base + "/b/" + tenant.getSlug() + "/booking/" + a.getManagementToken();
            boolean booked = event.type() == AppointmentEvent.Type.BOOKED;

            String subject = (booked ? "Booking confirmed: " : "Booking cancelled: ") + tenant.getName();
            String intro = booked
                    ? "Your appointment is confirmed."
                    : "Your appointment has been cancelled.";
            String footer = booked
                    ? "<p>Need to cancel? <a href=\"" + HtmlUtils.htmlEscape(link) + "\">Manage your booking</a>.</p>"
                    : "<p>You can book again any time at <a href=\"" + HtmlUtils.htmlEscape(base + "/b/" + tenant.getSlug())
                            + "\">" + HtmlUtils.htmlEscape(tenant.getName()) + "</a>.</p>";

            String html = "<p>Hi " + HtmlUtils.htmlEscape(a.getCustomerName()) + ",</p>"
                    + "<p>" + intro + "</p>"
                    + "<p><strong>" + HtmlUtils.htmlEscape(a.getServiceName()) + "</strong> with "
                    + HtmlUtils.htmlEscape(staff.getDisplayName()) + "<br>"
                    + HtmlUtils.htmlEscape(when) + "<br>"
                    + HtmlUtils.htmlEscape(tenant.getName())
                    + (tenant.getAddress() != null ? "<br>" + HtmlUtils.htmlEscape(tenant.getAddress()) : "")
                    + "</p>"
                    + footer;

            emailSender.send(a.getCustomerEmail(), subject, html);
        } catch (RuntimeException e) {
            log.warn("Could not send email for appointment {}", event.appointmentId(), e);
        }
    }
}
