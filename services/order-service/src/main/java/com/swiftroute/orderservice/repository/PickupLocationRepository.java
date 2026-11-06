package com.swiftroute.orderservice.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.swiftroute.orderservice.entity.PickupLocation;
import com.swiftroute.orderservice.entity.enums.PickupLocationStatus;

@Repository
public interface PickupLocationRepository extends JpaRepository<PickupLocation, UUID> {

    List<PickupLocation> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<PickupLocation> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, PickupLocationStatus status);

    Optional<PickupLocation> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<PickupLocation> findByTenantIdAndIsDefaultTrueAndStatus(UUID tenantId, PickupLocationStatus status);

    Optional<PickupLocation> findByTenantIdAndIsDefaultTrue(UUID tenantId);

    long countByTenantId(UUID tenantId);

    long countByTenantIdAndStatus(UUID tenantId, PickupLocationStatus status);

    @Modifying
    @Query("UPDATE PickupLocation p SET p.isDefault = false WHERE p.tenant.id = :tenantId AND p.id <> :excludeId")
    void clearOtherDefaults(@Param("tenantId") UUID tenantId, @Param("excludeId") UUID excludeId);

    @Modifying
    @Query("UPDATE PickupLocation p SET p.isDefault = false WHERE p.tenant.id = :tenantId")
    void clearAllDefaults(@Param("tenantId") UUID tenantId);
}

