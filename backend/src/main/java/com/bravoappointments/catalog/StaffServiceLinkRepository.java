package com.bravoappointments.catalog;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StaffServiceLinkRepository extends JpaRepository<StaffServiceLink, UUID> {

    List<StaffServiceLink> findByTenantId(UUID tenantId);

    List<StaffServiceLink> findByTenantIdAndServiceId(UUID tenantId, UUID serviceId);

    boolean existsByStaffIdAndServiceIdAndTenantId(UUID staffId, UUID serviceId, UUID tenantId);

    // Bulk delete runs immediately (not at flush), so re-inserting the same pairs afterwards can't hit the unique key.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from StaffServiceLink l where l.staffId = :staffId and l.tenantId = :tenantId")
    void deleteForStaff(@Param("staffId") UUID staffId, @Param("tenantId") UUID tenantId);
}
