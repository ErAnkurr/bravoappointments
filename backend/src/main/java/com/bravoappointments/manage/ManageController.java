package com.bravoappointments.manage;

import com.bravoappointments.manage.ManageDtos.MeResponse;
import com.bravoappointments.manage.ManageDtos.OnboardingRequest;
import com.bravoappointments.manage.ManageDtos.TenantUpdateRequest;
import com.bravoappointments.tenant.TenantView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Account and business profile for the signed-in manager. */
@RestController
@RequestMapping("/api/manage")
public class ManageController {

    private final TenantAdminService tenants;

    public ManageController(TenantAdminService tenants) {
        this.tenants = tenants;
    }

    /** Safe to call before onboarding: tells the app whether to show the dashboard or the setup screen. */
    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        return tenants.findForUser(jwt.getSubject())
                .map(t -> new MeResponse(true, TenantView.from(t)))
                .orElse(new MeResponse(false, null));
    }

    @PostMapping("/onboarding")
    @ResponseStatus(HttpStatus.CREATED)
    public TenantView onboard(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody OnboardingRequest request) {
        return TenantView.from(tenants.onboard(jwt.getSubject(), jwt.getClaimAsString("email"), request));
    }

    @GetMapping("/tenant")
    public TenantView tenant(ManagerContext ctx) {
        return TenantView.from(tenants.get(ctx.tenantId()));
    }

    @PutMapping("/tenant")
    public TenantView updateTenant(ManagerContext ctx, @Valid @RequestBody TenantUpdateRequest request) {
        return TenantView.from(tenants.update(ctx.tenantId(), request));
    }
}
