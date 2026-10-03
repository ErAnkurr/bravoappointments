package com.bravoappointments.manage;

import com.bravoappointments.manage.ManageDtos.HoursRequest;
import com.bravoappointments.manage.ManageDtos.ServiceRequest;
import com.bravoappointments.manage.ManageDtos.ServiceResponse;
import com.bravoappointments.manage.ManageDtos.StaffRequest;
import com.bravoappointments.manage.ManageDtos.StaffResponse;
import com.bravoappointments.manage.ManageDtos.TimeOffRequest;
import com.bravoappointments.manage.ManageDtos.TimeOffResponse;
import com.bravoappointments.manage.ManageDtos.WindowDto;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/manage")
public class CatalogManageController {

    private final CatalogAdminService catalog;

    public CatalogManageController(CatalogAdminService catalog) {
        this.catalog = catalog;
    }

    // ---- services ----

    @GetMapping("/services")
    public List<ServiceResponse> services(ManagerContext ctx) {
        return catalog.listServices(ctx.tenantId());
    }

    @PostMapping("/services")
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceResponse createService(ManagerContext ctx, @Valid @RequestBody ServiceRequest request) {
        return catalog.createService(ctx.tenantId(), request);
    }

    @PutMapping("/services/{id}")
    public ServiceResponse updateService(
            ManagerContext ctx, @PathVariable UUID id, @Valid @RequestBody ServiceRequest request) {
        return catalog.updateService(ctx.tenantId(), id, request);
    }

    // ---- staff ----

    @GetMapping("/staff")
    public List<StaffResponse> staff(ManagerContext ctx) {
        return catalog.listStaff(ctx.tenantId());
    }

    @PostMapping("/staff")
    @ResponseStatus(HttpStatus.CREATED)
    public StaffResponse createStaff(ManagerContext ctx, @Valid @RequestBody StaffRequest request) {
        return catalog.createStaff(ctx.tenantId(), request);
    }

    @PutMapping("/staff/{id}")
    public StaffResponse updateStaff(
            ManagerContext ctx, @PathVariable UUID id, @Valid @RequestBody StaffRequest request) {
        return catalog.updateStaff(ctx.tenantId(), id, request);
    }

    // ---- weekly hours ----

    @GetMapping("/staff/{id}/hours")
    public List<WindowDto> hours(ManagerContext ctx, @PathVariable UUID id) {
        return catalog.getHours(ctx.tenantId(), id);
    }

    @PutMapping("/staff/{id}/hours")
    public List<WindowDto> replaceHours(
            ManagerContext ctx, @PathVariable UUID id, @Valid @RequestBody HoursRequest request) {
        return catalog.replaceHours(ctx.tenantId(), id, request.windows());
    }

    // ---- time off ----

    @GetMapping("/staff/{id}/time-off")
    public List<TimeOffResponse> timeOff(ManagerContext ctx, @PathVariable UUID id) {
        return catalog.listTimeOff(ctx.tenantId(), id);
    }

    @PostMapping("/staff/{id}/time-off")
    @ResponseStatus(HttpStatus.CREATED)
    public TimeOffResponse addTimeOff(
            ManagerContext ctx, @PathVariable UUID id, @Valid @RequestBody TimeOffRequest request) {
        return catalog.addTimeOff(ctx.tenantId(), id, request);
    }

    @DeleteMapping("/staff/{id}/time-off/{timeOffId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTimeOff(ManagerContext ctx, @PathVariable UUID id, @PathVariable UUID timeOffId) {
        catalog.deleteTimeOff(ctx.tenantId(), id, timeOffId);
    }
}
