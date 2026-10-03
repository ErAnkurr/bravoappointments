package com.bravoappointments.scheduling;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkingHoursRepository extends JpaRepository<WorkingHours, UUID> {

    List<WorkingHours> findByStaffIdInAndTenantId(Collection<UUID> staffIds, UUID tenantId);

    List<WorkingHours> findByStaffIdAndTenantIdOrderByDayOfWeekAscStartTimeAsc(UUID staffId, UUID tenantId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from WorkingHours h where h.staffId = :staffId and h.tenantId = :tenantId")
    void deleteForStaff(@Param("staffId") UUID staffId, @Param("tenantId") UUID tenantId);
}
