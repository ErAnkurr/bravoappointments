package com.bravoappointments.tenant;

/** The business profile as exposed over the API (public booking page and manager settings). */
public record TenantView(
        String slug,
        String name,
        String timezone,
        String description,
        String address,
        String phone,
        String primaryColor,
        String logoUrl) {

    public static TenantView from(Tenant t) {
        return new TenantView(
                t.getSlug(),
                t.getName(),
                t.getTimezone(),
                t.getDescription(),
                t.getAddress(),
                t.getPhone(),
                t.getPrimaryColor(),
                t.getLogoUrl());
    }
}
