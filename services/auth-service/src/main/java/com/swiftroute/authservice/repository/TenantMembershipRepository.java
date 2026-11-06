package com.swiftroute.authservice.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.swiftroute.authservice.entity.TenantMembership;
import com.swiftroute.authservice.entity.enums.TenantMembershipRole;
import com.swiftroute.authservice.entity.enums.TenantMembershipStatus;

@Repository
public interface TenantMembershipRepository extends JpaRepository<TenantMembership, UUID> {

    List<TenantMembership> findByUser_IdAndStatus(UUID userId, TenantMembershipStatus status);

    List<TenantMembership> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<TenantMembership> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, TenantMembershipStatus status);

    Optional<TenantMembership> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<TenantMembership> findByTenantIdAndUser_Id(UUID tenantId, UUID userId);

    Optional<TenantMembership> findByTenantIdAndUser_EmailIgnoreCase(UUID tenantId, String email);

    long countByTenantIdAndMembershipRoleAndStatus(UUID tenantId, TenantMembershipRole role, TenantMembershipStatus status);
}
