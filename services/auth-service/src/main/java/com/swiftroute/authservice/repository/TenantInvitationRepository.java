package com.swiftroute.authservice.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.swiftroute.authservice.entity.TenantInvitation;
import com.swiftroute.authservice.entity.enums.TenantInvitationStatus;

@Repository
public interface TenantInvitationRepository extends JpaRepository<TenantInvitation, UUID> {

    List<TenantInvitation> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<TenantInvitation> findByTokenHash(String tokenHash);

    Optional<TenantInvitation> findByTenantIdAndEmailIgnoreCaseAndStatus(UUID tenantId, String email, TenantInvitationStatus status);

    List<TenantInvitation> findByTenantIdAndEmailIgnoreCase(UUID tenantId, String email);
}

