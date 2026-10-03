package com.bravoappointments.appointment;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    Optional<Appointment> findByManagementToken(String managementToken);

    Optional<Appointment> findByIdAndTenantId(UUID id, UUID tenantId);

    /** Non-cancelled appointments that overlap [from, to) for any of the given staff. */
    @Query("select a from Appointment a where a.tenantId = :tenantId and a.staffId in :staffIds "
            + "and a.status <> com.bravoappointments.appointment.AppointmentStatus.CANCELLED "
            + "and a.startAt < :to and a.endAt > :from")
    List<Appointment> findActiveOverlapping(
            @Param("tenantId") UUID tenantId,
            @Param("staffIds") Collection<UUID> staffIds,
            @Param("from") Instant from,
            @Param("to") Instant to);

    /** How busy a barber is on a day; used to spread "any available" bookings. */
    @Query("select count(a) from Appointment a where a.tenantId = :tenantId and a.staffId = :staffId "
            + "and a.status <> com.bravoappointments.appointment.AppointmentStatus.CANCELLED "
            + "and a.startAt >= :from and a.startAt < :to")
    long countActiveStartingBetween(
            @Param("tenantId") UUID tenantId,
            @Param("staffId") UUID staffId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    List<Appointment> findByTenantIdAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(
            UUID tenantId, Instant from, Instant to);

    List<Appointment> findByTenantIdAndStaffIdAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(
            UUID tenantId, UUID staffId, Instant from, Instant to);
}
