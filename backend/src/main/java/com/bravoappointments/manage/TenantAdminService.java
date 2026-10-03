package com.bravoappointments.manage;

import com.bravoappointments.common.ApiException;
import com.bravoappointments.manage.ManageDtos.OnboardingRequest;
import com.bravoappointments.manage.ManageDtos.TenantUpdateRequest;
import com.bravoappointments.tenant.AppUser;
import com.bravoappointments.tenant.AppUserRepository;
import com.bravoappointments.tenant.Tenant;
import com.bravoappointments.tenant.TenantRepository;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantAdminService {

    /** Slugs that would collide with API or app routes. */
    private static final Set<String> RESERVED_SLUGS = Set.of(
            "api", "admin", "app", "assets", "b", "bookings", "dashboard", "health", "onboarding", "sign-in",
            "sign-up", "static", "www");

    private final TenantRepository tenantRepository;
    private final AppUserRepository userRepository;

    public TenantAdminService(TenantRepository tenantRepository, AppUserRepository userRepository) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Optional<Tenant> findForUser(String authProviderId) {
        return userRepository
                .findByAuthProviderId(authProviderId)
                .flatMap(user -> tenantRepository.findById(user.getTenantId()));
    }

    @Transactional(readOnly = true)
    public Tenant get(UUID tenantId) {
        return tenantRepository.findById(tenantId).orElseThrow(() -> ApiException.notFound("Business"));
    }

    /** First login: creates the business and links the Clerk user to it as its manager. */
    @Transactional
    public Tenant onboard(String authProviderId, String email, OnboardingRequest request) {
        if (userRepository.findByAuthProviderId(authProviderId).isPresent()) {
            throw ApiException.conflict("ALREADY_ONBOARDED", "This account already has a business");
        }
        String slug = request.slug();
        if (RESERVED_SLUGS.contains(slug)) {
            throw ApiException.badRequest("SLUG_RESERVED", "That address is reserved. Pick another.");
        }
        requireValidTimezone(request.timezone());
        if (tenantRepository.existsBySlug(slug)) {
            throw slugTaken();
        }
        try {
            Tenant tenant = tenantRepository.saveAndFlush(new Tenant(slug, request.name().trim(), request.timezone()));
            userRepository.save(new AppUser(tenant.getId(), authProviderId, email));
            return tenant;
        } catch (DataIntegrityViolationException e) {
            throw slugTaken(); // lost a race with someone claiming the same slug
        }
    }

    @Transactional
    public Tenant update(UUID tenantId, TenantUpdateRequest request) {
        Tenant tenant = get(tenantId);
        requireValidTimezone(request.timezone());
        tenant.setName(request.name().trim());
        tenant.setTimezone(request.timezone());
        tenant.setDescription(blankToNull(request.description()));
        tenant.setAddress(blankToNull(request.address()));
        tenant.setPhone(blankToNull(request.phone()));
        tenant.setPrimaryColor(request.primaryColor());
        tenant.setLogoUrl(blankToNull(request.logoUrl()));
        return tenantRepository.save(tenant);
    }

    private static void requireValidTimezone(String timezone) {
        if (!ZoneId.getAvailableZoneIds().contains(timezone)) {
            throw ApiException.badRequest("INVALID_TIMEZONE", "Unknown timezone: " + timezone);
        }
    }

    private static ApiException slugTaken() {
        return ApiException.conflict("SLUG_TAKEN", "That address is already taken. Pick another.");
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
