package com.swiftroute.authservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftroute.authservice.dto.response.PermissionCatalogResponse;
import com.swiftroute.authservice.dto.response.PermissionCatalogResponse.PermissionItemDTO;
import com.swiftroute.authservice.entity.TenantMembership;
import com.swiftroute.authservice.entity.TenantRolePermission;
import com.swiftroute.authservice.entity.enums.Role;
import com.swiftroute.authservice.entity.enums.TenantMembershipRole;
import com.swiftroute.authservice.entity.enums.TenantPermission;
import com.swiftroute.authservice.exception.AppException;
import com.swiftroute.authservice.exception.ErrorCode;
import com.swiftroute.authservice.repository.TenantRolePermissionRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves 3-layer effective permissions for tenant members:
 * Effective(m) = (TenantRoleTemplate(m.tenantId, m.membershipRole) U m.grantedPermissions) \ m.revokedPermissions
 * Invariant: OWNER and SYSTEM_ADMIN always hold all 29 permissions.
 */
@Service
@RequiredArgsConstructor
public class TenantPermissionService {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};
    private static final ObjectMapper DEFAULT_MAPPER = new ObjectMapper();

    private final TenantRolePermissionRepository tenantRolePermissionRepository;

    /**
     * Parses a JSON array of permission codes stored in a TEXT column.
     * Filters out any unrecognized codes safely.
     */
    public List<String> parsePermissionJson(String json) {
        if (json == null || json.isBlank() || "[]".equals(json.trim())) {
            return List.of();
        }
        try {
            List<String> raw = DEFAULT_MAPPER.readValue(json, STRING_LIST_TYPE);
            if (raw == null || raw.isEmpty()) {
                return List.of();
            }
            Set<String> valid = new LinkedHashSet<>();
            for (String code : raw) {
                TenantPermission.fromCode(code).ifPresent(p -> valid.add(p.getCode()));
            }
            return List.copyOf(valid);
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Validates and serializes a list of permission codes into canonical JSON array string.
     */
    public String toPermissionJson(List<String> codes) {
        Set<String> normalized = validatePermissionCodes(codes);
        try {
            return DEFAULT_MAPPER.writeValueAsString(new ArrayList<>(normalized));
        } catch (Exception e) {
            return "[]";
        }
    }

    /**
     * Validates that all supplied permission codes exist in the canonical catalog.
     */
    public Set<String> validatePermissionCodes(List<String> codes) {
        try {
            return TenantPermission.normalizeAndValidateCodes(codes);
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, ex.getMessage());
        }
    }

    /**
     * Resolves the Layer-B role template for a given tenant and role, falling back to Layer-A system defaults.
     */
    @Transactional(readOnly = true)
    public List<String> resolveRoleTemplate(UUID tenantId, TenantMembershipRole role) {
        if (role == null) {
            return List.of();
        }
        if (role == TenantMembershipRole.OWNER) {
            return TenantPermission.allCodes();
        }
        if (tenantId == null || tenantRolePermissionRepository == null) {
            return TenantPermission.defaultPermissionsForRole(role);
        }
        return tenantRolePermissionRepository.findByTenantIdAndMembershipRole(tenantId, role)
                .map(entity -> parsePermissionJson(entity.getPermissions()))
                .orElseGet(() -> TenantPermission.defaultPermissionsForRole(role));
    }

    /**
     * Resolves the 3-layer effective permissions for a TenantMembership.
     */
    @Transactional(readOnly = true)
    public List<String> resolveEffectivePermissions(TenantMembership membership) {
        if (membership == null) {
            return List.of();
        }
        TenantMembershipRole role = membership.getMembershipRole();
        if (role == TenantMembershipRole.OWNER) {
            return TenantPermission.allCodes();
        }
        List<String> roleTemplate = resolveRoleTemplate(membership.getTenantId(), role);
        List<String> granted = parsePermissionJson(membership.getGrantedPermissions());
        List<String> revoked = parsePermissionJson(membership.getRevokedPermissions());

        return computeEffective(role, roleTemplate, granted, revoked);
    }

    /**
     * Resolves effective permissions for a user in a tenant context (handles SYSTEM_ADMIN and null membership).
     */
    @Transactional(readOnly = true)
    public List<String> resolveEffectivePermissions(Role systemRole, TenantMembership membership) {
        if (systemRole == Role.SYSTEM_ADMIN) {
            return TenantPermission.allCodes();
        }
        return resolveEffectivePermissions(membership);
    }

    /**
     * Pure calculation of effective permissions from role template + granted - revoked.
     */
    public List<String> computeEffective(
            TenantMembershipRole role,
            List<String> roleTemplate,
            List<String> granted,
            List<String> revoked) {
        if (role == TenantMembershipRole.OWNER) {
            return TenantPermission.allCodes();
        }
        Set<String> effective = new LinkedHashSet<>();
        if (roleTemplate != null) {
            effective.addAll(roleTemplate);
        }
        if (granted != null) {
            for (String code : granted) {
                TenantPermission.fromCode(code).ifPresent(p -> {
                    if (!p.isDriverOnly() || role == TenantMembershipRole.DRIVER) {
                        effective.add(p.getCode());
                    }
                });
            }
        }
        if (revoked != null && !revoked.isEmpty()) {
            for (String code : revoked) {
                TenantPermission.fromCode(code).ifPresent(p -> effective.remove(p.getCode()));
            }
        }
        return List.copyOf(effective);
    }

    /**
     * Validates per-member granted/revoked overrides against driver-only constraints and actor's grant ceiling.
     */
    public void validateMemberOverrides(
            TenantMembershipRole targetRole,
            Set<String> granted,
            Set<String> revoked,
            TenantMembershipRole actorRole,
            Set<String> actorEffectivePermissions,
            boolean isSystemAdmin) {

        if (targetRole == TenantMembershipRole.OWNER) {
            if ((granted != null && !granted.isEmpty()) || (revoked != null && !revoked.isEmpty())) {
                throw new AppException(ErrorCode.VALIDATION_ERROR,
                        "Cannot customize or revoke permissions of OWNER role");
            }
            return;
        }

        // Disallow overlapping codes in granted and revoked simultaneously
        if (granted != null && revoked != null) {
            for (String code : granted) {
                if (revoked.contains(code)) {
                    throw new AppException(ErrorCode.VALIDATION_ERROR,
                            "Permission cannot be both granted and revoked simultaneously: " + code);
                }
            }
        }

        // Driver-only permissions can only be granted to DRIVER role
        if (granted != null && targetRole != TenantMembershipRole.DRIVER) {
            for (String code : granted) {
                TenantPermission perm = TenantPermission.fromCode(code)
                        .orElseThrow(() -> new AppException(ErrorCode.VALIDATION_ERROR, "Unknown permission: " + code));
                if (perm.isDriverOnly()) {
                    throw new AppException(ErrorCode.VALIDATION_ERROR,
                            "Permission '" + code + "' is restricted to DRIVER role only");
                }
            }
        }

        // Grant ceiling: non-OWNER, non-SYSTEM_ADMIN actors cannot grant permissions they do not possess themselves,
        // and cannot revoke permissions from a member that the actor does not possess.
        if (!isSystemAdmin && actorRole != TenantMembershipRole.OWNER) {
            Set<String> ceiling = actorEffectivePermissions != null ? actorEffectivePermissions : Collections.emptySet();
            if (granted != null) {
                for (String code : granted) {
                    if (!ceiling.contains(code)) {
                        throw new AppException(ErrorCode.ACCESS_DENIED,
                                "Cannot grant permission '" + code + "' that you do not hold yourself");
                    }
                }
            }
            if (revoked != null) {
                for (String code : revoked) {
                    if (!ceiling.contains(code)) {
                        throw new AppException(ErrorCode.ACCESS_DENIED,
                                "Cannot revoke permission '" + code + "' that is outside your own permission scope");
                    }
                }
            }
        }
    }

    /**
     * Returns all role templates for a tenant (merged with system defaults for uncustomized roles).
     */
    @Transactional(readOnly = true)
    public Map<String, List<String>> getTenantRoleTemplates(UUID tenantId) {
        Map<TenantMembershipRole, List<String>> customMap = new LinkedHashMap<>();
        if (tenantId != null && tenantRolePermissionRepository != null) {
            for (TenantRolePermission trp : tenantRolePermissionRepository.findByTenantId(tenantId)) {
                customMap.put(trp.getMembershipRole(), parsePermissionJson(trp.getPermissions()));
            }
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (TenantMembershipRole role : TenantMembershipRole.values()) {
            if (role == TenantMembershipRole.OWNER) {
                result.put(role.name(), TenantPermission.allCodes());
            } else {
                result.put(role.name(),
                        customMap.getOrDefault(role, TenantPermission.defaultPermissionsForRole(role)));
            }
        }
        return result;
    }

    /**
     * Updates the enterprise-level default permission template for a specific role in a tenant.
     * Only OWNER (or SYSTEM_ADMIN) is permitted to modify enterprise role templates.
     */
    @Transactional
    public List<String> updateRoleTemplate(
            UUID tenantId,
            TenantMembershipRole targetRole,
            List<String> rawPermissions,
            UUID actorUserId) {
        if (targetRole == null || targetRole == TenantMembershipRole.OWNER) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "OWNER role template is immutable and always retains full permissions");
        }
        Set<String> validated = validatePermissionCodes(rawPermissions);
        for (String code : validated) {
            TenantPermission perm = TenantPermission.fromCode(code).orElseThrow();
            if (perm.isDriverOnly() && targetRole != TenantMembershipRole.DRIVER) {
                throw new AppException(ErrorCode.VALIDATION_ERROR,
                        "Permission '" + code + "' is restricted to DRIVER role only");
            }
        }

        String json = toPermissionJson(new ArrayList<>(validated));
        TenantRolePermission entity = tenantRolePermissionRepository
                .findByTenantIdAndMembershipRole(tenantId, targetRole)
                .orElseGet(() -> TenantRolePermission.builder()
                        .tenantId(tenantId)
                        .membershipRole(targetRole)
                        .build());
        entity.setPermissions(json);
        entity.setUpdatedBy(actorUserId);
        entity.setUpdatedAt(Instant.now());
        tenantRolePermissionRepository.save(entity);

        return List.copyOf(validated);
    }

    /**
     * Builds the full permissions catalog response for the tenant UI (catalog items + system defaults + tenant templates).
     */
    @Transactional(readOnly = true)
    public PermissionCatalogResponse getPermissionsCatalog(UUID tenantId) {
        List<PermissionItemDTO> items = Arrays.stream(TenantPermission.values())
                .map(p -> new PermissionItemDTO(
                        p.getCode(),
                        p.getModule(),
                        p.getDisplayName(),
                        p.getDescription(),
                        p.isDriverOnly()))
                .toList();

        Map<String, List<String>> systemDefaults = new LinkedHashMap<>();
        for (TenantMembershipRole role : TenantMembershipRole.values()) {
            systemDefaults.put(role.name(), TenantPermission.defaultPermissionsForRole(role));
        }

        Map<String, List<String>> tenantTemplates = getTenantRoleTemplates(tenantId);
        return new PermissionCatalogResponse(items, systemDefaults, tenantTemplates);
    }
}
