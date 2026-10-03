package com.bravoappointments.catalog;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceOfferingRepository extends JpaRepository<ServiceOffering, UUID> {

    List<ServiceOffering> findByTenantIdOrderBySortOrderAscNameAsc(UUID tenantId);

    List<ServiceOffering> findByTenantIdAndActiveTrueOrderBySortOrderAscNameAsc(UUID tenantId);

    Optional<ServiceOffering> findByIdAndTenantId(UUID id, UUID tenantId);

    List<ServiceOffering> findByIdInAndTenantId(Collection<UUID> ids, UUID tenantId);

    long countByTenantId(UUID tenantId);
}
