package com.swiftroute.authservice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.swiftroute.authservice.dto.response.PermissionCatalogResponse;
import com.swiftroute.authservice.entity.TenantMembership;
import com.swiftroute.authservice.entity.TenantRolePermission;
import com.swiftroute.authservice.entity.enums.Role;
import com.swiftroute.authservice.entity.enums.TenantMembershipRole;
import com.swiftroute.authservice.entity.enums.TenantMembershipStatus;
import com.swiftroute.authservice.entity.enums.TenantPermission;
import com.swiftroute.authservice.exception.AppException;
import com.swiftroute.authservice.exception.ErrorCode;
import com.swiftroute.authservice.repository.TenantRolePermissionRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TenantPermissionServiceTest {

    @Mock
    private TenantRolePermissionRepository tenantRolePermissionRepository;

    private TenantPermissionService tenantPermissionService;

    @BeforeEach
    void setUp() {
        tenantPermissionService = new TenantPermissionService(tenantRolePermissionRepository);
    }

    @Test
    @DisplayName("OWNER and SYSTEM_ADMIN always resolve to all 29 canonical permissions")
    void ownerAndSystemAdmin_ShouldHaveAllPermissions() {
        UUID tenantId = UUID.randomUUID();
        TenantMembership ownerMembership = TenantMembership.builder()
                .tenantId(tenantId)
                .membershipRole(TenantMembershipRole.OWNER)
                .status(TenantMembershipStatus.ACTIVE)
                .revokedPermissions("[\"order:cancel\",\"billing:manage\"]")
                .build();

        List<String> ownerPerms = tenantPermissionService.resolveEffectivePermissions(ownerMembership);
        assertEquals(29, ownerPerms.size());
        assertTrue(ownerPerms.contains("order:cancel"));
        assertTrue(ownerPerms.contains("billing:manage"));

        List<String> sysAdminPerms = tenantPermissionService.resolveEffectivePermissions(Role.SYSTEM_ADMIN, null);
        assertEquals(29, sysAdminPerms.size());
    }

    @Test
    @DisplayName("DRIVER default permissions include dispatch:read and dispatch:execute per business requirement")
    void driverDefaults_ShouldIncludeDispatchAndSimulation() {
        List<String> driverDefaults = TenantPermission.defaultPermissionsForRole(TenantMembershipRole.DRIVER);
        assertTrue(driverDefaults.contains("dispatch:read"));
        assertTrue(driverDefaults.contains("dispatch:execute"));
        assertTrue(driverDefaults.contains("order:deliver"));
        assertTrue(driverDefaults.contains("driver:self"));
        assertFalse(driverDefaults.contains("billing:manage"));
    }

    @Test
    @DisplayName("3-Layer Effective Permissions: (RoleTemplate U granted) \\ revoked")
    void resolveEffectivePermissions_ThreeLayerFormula() {
        UUID tenantId = UUID.randomUUID();
        // Custom role template for OPERATOR in this tenant: only order:read and order:create
        TenantRolePermission customTemplate = TenantRolePermission.builder()
                .tenantId(tenantId)
                .membershipRole(TenantMembershipRole.OPERATOR)
                .permissions("[\"order:read\",\"order:create\",\"order:cancel\"]")
                .build();
        when(tenantRolePermissionRepository.findByTenantIdAndMembershipRole(tenantId, TenantMembershipRole.OPERATOR))
                .thenReturn(Optional.of(customTemplate));

        // Member is granted billing:read and has order:cancel revoked
        TenantMembership member = TenantMembership.builder()
                .tenantId(tenantId)
                .membershipRole(TenantMembershipRole.OPERATOR)
                .status(TenantMembershipStatus.ACTIVE)
                .grantedPermissions("[\"billing:read\"]")
                .revokedPermissions("[\"order:cancel\"]")
                .build();

        List<String> effective = tenantPermissionService.resolveEffectivePermissions(member);
        assertEquals(List.of("order:read", "order:create", "billing:read"), effective);
    }

    @Test
    @DisplayName("Grant ceiling prevents TENANT_ADMIN from granting permissions they do not possess")
    void validateMemberOverrides_GrantCeilingEnforced() {
        Set<String> actorEffective = Set.of("order:read", "member:manage");

        AppException ex = assertThrows(AppException.class, () ->
                tenantPermissionService.validateMemberOverrides(
                        TenantMembershipRole.OPERATOR,
                        Set.of("billing:manage"),
                        Set.of(),
                        TenantMembershipRole.TENANT_ADMIN,
                        actorEffective,
                        false));
        assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());
    }

    @Test
    @DisplayName("Driver-only permissions cannot be granted to non-DRIVER roles")
    void validateMemberOverrides_DriverOnlyGuard() {
        AppException ex = assertThrows(AppException.class, () ->
                tenantPermissionService.validateMemberOverrides(
                        TenantMembershipRole.OPERATOR,
                        Set.of("order:deliver"),
                        Set.of(),
                        TenantMembershipRole.OWNER,
                         Set.copyOf(TenantPermission.allCodes()),
                        false));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
    }

    @Test
    @DisplayName("updateRoleTemplate saves custom template and rejects OWNER modification")
    void updateRoleTemplate_SuccessAndOwnerGuard() {
        UUID tenantId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        when(tenantRolePermissionRepository.findByTenantIdAndMembershipRole(tenantId, TenantMembershipRole.VIEWER))
                .thenReturn(Optional.empty());
        when(tenantRolePermissionRepository.save(any(TenantRolePermission.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        List<String> updated = tenantPermissionService.updateRoleTemplate(
                tenantId,
                TenantMembershipRole.VIEWER,
                List.of("order:read", "analytics:read"),
                ownerId);

        assertEquals(List.of("order:read", "analytics:read"), updated);
        verify(tenantRolePermissionRepository).save(any(TenantRolePermission.class));

        assertThrows(AppException.class, () ->
                tenantPermissionService.updateRoleTemplate(
                        tenantId, TenantMembershipRole.OWNER, List.of("order:read"), ownerId));
    }

    @Test
    @DisplayName("getPermissionsCatalog returns all 29 permissions and role templates")
    void getPermissionsCatalog_ReturnsCompleteCatalog() {
        UUID tenantId = UUID.randomUUID();
        when(tenantRolePermissionRepository.findByTenantId(tenantId)).thenReturn(List.of());

        PermissionCatalogResponse catalog = tenantPermissionService.getPermissionsCatalog(tenantId);
        assertEquals(29, catalog.permissions().size());
        assertEquals(5, catalog.systemRoleDefaults().size());
        assertEquals(5, catalog.tenantRoleTemplates().size());
    }
}
