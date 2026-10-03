package com.bravoappointments.appointment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "appointment")
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID staffId;

    @Column(nullable = false, updatable = false)
    private UUID serviceId;

    @Column(nullable = false, updatable = false)
    private String serviceName;

    @Column(nullable = false, updatable = false)
    private int priceCents;

    @Column(nullable = false, updatable = false)
    private String customerName;

    @Column(nullable = false, updatable = false)
    private String customerEmail;

    @Column(nullable = false, updatable = false)
    private String customerPhone;

    private String notes;

    @Column(nullable = false, updatable = false)
    private Instant startAt;

    @Column(nullable = false, updatable = false)
    private Instant endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentStatus status = AppointmentStatus.CONFIRMED;

    @Column(nullable = false, updatable = false)
    private String managementToken;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Appointment() {}

    public Appointment(
            UUID tenantId,
            UUID staffId,
            UUID serviceId,
            String serviceName,
            int priceCents,
            String customerName,
            String customerEmail,
            String customerPhone,
            String notes,
            Instant startAt,
            Instant endAt,
            String managementToken) {
        this.tenantId = tenantId;
        this.staffId = staffId;
        this.serviceId = serviceId;
        this.serviceName = serviceName;
        this.priceCents = priceCents;
        this.customerName = customerName;
        this.customerEmail = customerEmail;
        this.customerPhone = customerPhone;
        this.notes = notes;
        this.startAt = startAt;
        this.endAt = endAt;
        this.managementToken = managementToken;
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
    public UUID getStaffId() { return staffId; }
    public void setStaffId(UUID staffId) { this.staffId = staffId; }
    public UUID getServiceId() { return serviceId; }
    public String getServiceName() { return serviceName; }
    public int getPriceCents() { return priceCents; }
    public String getCustomerName() { return customerName; }
    public String getCustomerEmail() { return customerEmail; }
    public String getCustomerPhone() { return customerPhone; }
    public String getNotes() { return notes; }
    public Instant getStartAt() { return startAt; }
    public Instant getEndAt() { return endAt; }
    public AppointmentStatus getStatus() { return status; }
    public void setStatus(AppointmentStatus status) { this.status = status; }
    public String getManagementToken() { return managementToken; }
}
