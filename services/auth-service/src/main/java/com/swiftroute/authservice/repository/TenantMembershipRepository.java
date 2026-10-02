package com.swiftroute.authservice.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.swiftroute.authservice.entity.TenantMembership;

public interface TenantMembershipRepository extends JpaRepository<TenantMembership, UUID> {
    Optional<TenantMembership> findByTenantIdAndUser_Id(UUID tenantId, UUID userId);
}
