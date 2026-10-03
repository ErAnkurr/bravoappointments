package com.bravoappointments.catalog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffRepository extends JpaRepository<Staff, UUID> {

    List<Staff> findByTenantIdOrderBySortOrderAscDisplayNameAsc(UUID tenantId);

    List<Staff> findByTenantIdAndActiveTrueOrderBySortOrderAscDisplayNameAsc(UUID tenantId);

    Optional<Staff> findByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);
}
