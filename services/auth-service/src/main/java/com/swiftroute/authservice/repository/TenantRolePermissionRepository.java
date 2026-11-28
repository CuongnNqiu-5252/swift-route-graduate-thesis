package com.swiftroute.authservice.repository;

import com.swiftroute.authservice.entity.TenantRolePermission;
import com.swiftroute.authservice.entity.TenantRolePermissionId;
import com.swiftroute.authservice.entity.enums.TenantMembershipRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TenantRolePermissionRepository extends JpaRepository<TenantRolePermission, TenantRolePermissionId> {

    List<TenantRolePermission> findByTenantId(UUID tenantId);

    Optional<TenantRolePermission> findByTenantIdAndMembershipRole(UUID tenantId, TenantMembershipRole membershipRole);
}
