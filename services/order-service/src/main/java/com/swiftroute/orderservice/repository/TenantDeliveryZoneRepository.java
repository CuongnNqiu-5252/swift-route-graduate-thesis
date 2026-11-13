package com.swiftroute.orderservice.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.swiftroute.orderservice.entity.TenantDeliveryZone;

@Repository
public interface TenantDeliveryZoneRepository extends JpaRepository<TenantDeliveryZone, UUID> {

    Optional<TenantDeliveryZone> findByTenantId(UUID tenantId);

    Optional<TenantDeliveryZone> findByTenantIdAndIsActiveTrue(UUID tenantId);
}
