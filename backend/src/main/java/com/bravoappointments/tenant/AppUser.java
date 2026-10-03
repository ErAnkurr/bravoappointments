package com.bravoappointments.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A manager account: links a Clerk user to exactly one tenant. */
@Entity
@Table(name = "app_user")
public class AppUser {

    public static final String STORE_MANAGER = "STORE_MANAGER";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false, updatable = false)
    private String authProviderId;

    private String email;

    @Column(nullable = false)
    private String role = STORE_MANAGER;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected AppUser() {}

    public AppUser(UUID tenantId, String authProviderId, String email) {
        this.tenantId = tenantId;
        this.authProviderId = authProviderId;
        this.email = email;
    }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getAuthProviderId() { return authProviderId; }
    public String getEmail() { return email; }
    public String getRole() { return role; }
}
