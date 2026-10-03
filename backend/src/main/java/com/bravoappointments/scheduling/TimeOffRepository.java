package com.bravoappointments.scheduling;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TimeOffRepository extends JpaRepository<TimeOff, UUID> {

    List<TimeOff> findByStaffIdAndTenantIdOrderByStartAtAsc(UUID staffId, UUID tenantId);

    Optional<TimeOff> findByIdAndStaffIdAndTenantId(UUID id, UUID staffId, UUID tenantId);

    @Query("select t from TimeOff t where t.tenantId = :tenantId and t.staffId in :staffIds "
            + "and t.startAt < :to and t.endAt > :from")
    List<TimeOff> findOverlapping(
            @Param("tenantId") UUID tenantId,
            @Param("staffIds") Collection<UUID> staffIds,
            @Param("from") Instant from,
            @Param("to") Instant to);
}
