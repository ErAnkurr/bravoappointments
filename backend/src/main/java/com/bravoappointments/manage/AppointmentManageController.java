package com.bravoappointments.manage;

import com.bravoappointments.manage.ManageDtos.AppointmentRow;
import com.bravoappointments.manage.ManageDtos.AppointmentUpdateRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/manage/appointments")
public class AppointmentManageController {

    private final AppointmentAdminService appointments;

    public AppointmentManageController(AppointmentAdminService appointments) {
        this.appointments = appointments;
    }

    @GetMapping
    public List<AppointmentRow> list(
            ManagerContext ctx,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID staffId) {
        return appointments.list(ctx.tenantId(), from, to, staffId);
    }

    @PatchMapping("/{id}")
    public AppointmentRow update(
            ManagerContext ctx, @PathVariable UUID id, @Valid @RequestBody AppointmentUpdateRequest request) {
        return appointments.update(ctx.tenantId(), id, request);
    }
}
