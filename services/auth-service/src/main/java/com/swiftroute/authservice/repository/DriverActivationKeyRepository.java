package com.swiftroute.authservice.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.swiftroute.authservice.entity.DriverActivationKey;

import jakarta.persistence.LockModeType;

@Repository
public interface DriverActivationKeyRepository extends JpaRepository<DriverActivationKey, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT k FROM DriverActivationKey k JOIN FETCH k.user WHERE k.keyHash = :keyHash")
    Optional<DriverActivationKey> findLockedByKeyHash(@Param("keyHash") String keyHash);

    List<DriverActivationKey> findByTenantIdAndDriverIdOrderByCreatedAtDesc(UUID tenantId, UUID driverId);

    List<DriverActivationKey> findByTenantIdAndDriverIdInOrderByCreatedAtDesc(UUID tenantId, Collection<UUID> driverIds);

    boolean existsByKeyHash(String keyHash);

    @Modifying
    @Query("UPDATE DriverActivationKey k SET k.revokedAt = :now "
            + "WHERE k.tenantId = :tenantId AND k.driverId = :driverId AND k.usedAt IS NULL AND k.revokedAt IS NULL")
    int revokeUnusedByTenantAndDriver(
            @Param("tenantId") UUID tenantId,
            @Param("driverId") UUID driverId,
            @Param("now") Instant now);
}
