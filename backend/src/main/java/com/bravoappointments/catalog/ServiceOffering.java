package com.bravoappointments.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A bookable service (haircut, beard trim...). Named ServiceOffering to avoid clashing with Spring's @Service. */
@Entity
@Table(name = "service")
public class ServiceOffering {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private int durationMinutes;

    @Column(nullable = false)
    private int priceCents;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private int sortOrder;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected ServiceOffering() {}

    public ServiceOffering(UUID tenantId, String name, String description, int durationMinutes, int priceCents, int sortOrder) {
        this.tenantId = tenantId;
        this.name = name;
        this.description = description;
        this.durationMinutes = durationMinutes;
        this.priceCents = priceCents;
        this.sortOrder = sortOrder;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
    public int getPriceCents() { return priceCents; }
    public void setPriceCents(int priceCents) { this.priceCents = priceCents; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public int getSortOrder() { return sortOrder; }
}
