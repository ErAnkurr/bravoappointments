package com.bravoappointments.scheduling;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A blocked range for one staff member (holiday, break, appointment taken outside the system). */
@Entity
@Table(name = "time_off")
public class TimeOff {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false, updatable = false)
    private UUID staffId;

    @Column(nullable = false)
    private Instant startAt;

    @Column(nullable = false)
    private Instant endAt;

    private String reason;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected TimeOff() {}

    public TimeOff(UUID tenantId, UUID staffId, Instant startAt, Instant endAt, String reason) {
        this.tenantId = tenantId;
        this.staffId = staffId;
        this.startAt = startAt;
        this.endAt = endAt;
        this.reason = reason;
    }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getStaffId() { return staffId; }
    public Instant getStartAt() { return startAt; }
    public Instant getEndAt() { return endAt; }
    public String getReason() { return reason; }
}
