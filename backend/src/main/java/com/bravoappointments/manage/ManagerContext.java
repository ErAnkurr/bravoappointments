package com.bravoappointments.manage;

import java.util.UUID;

/**
 * Who is calling and which business they manage, resolved server-side from the verified Clerk token.
 * Controllers take this as a parameter and pass tenantId down; a tenant id from the client is never trusted.
 */
public record ManagerContext(UUID userId, UUID tenantId, String role) {}
