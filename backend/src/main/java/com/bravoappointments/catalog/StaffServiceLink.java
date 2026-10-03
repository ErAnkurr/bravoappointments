package com.bravoappointments.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Row in staff_service: this barber performs this service. */
@Entity
@Table(name = "staff_service")
public class StaffServiceLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false, updatable = false)
    private UUID staffId;

    @Column(nullable = false, updatable = false)
    private UUID serviceId;

    protected StaffServiceLink() {}

    public StaffServiceLink(UUID tenantId, UUID staffId, UUID serviceId) {
        this.tenantId = tenantId;
        this.staffId = staffId;
        this.serviceId = serviceId;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getStaffId() { return staffId; }
    public UUID getServiceId() { return serviceId; }
}
