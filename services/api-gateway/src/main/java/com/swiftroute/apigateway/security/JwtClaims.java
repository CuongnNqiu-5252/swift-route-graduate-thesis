package com.swiftroute.apigateway.security;

/**
 * Immutable record holding parsed claims from a verified JWT.
 *
 * Mapping from auth-service JWT claims:
 * - sub         -> userId     (user UUID)
 * - system_role -> systemRole (SYSTEM_ADMIN, USER)
 * - tenant_id   -> tenantId   (tenant UUID, nullable)
 * - tenant_role -> tenantRole (OWNER, TENANT_ADMIN, OPERATOR, DRIVER, VIEWER; nullable)
 */
public record JwtClaims(
        String userId,
        String systemRole,
        String tenantId,
        String tenantRole
) {
    /**
     * Returns the effective role for downstream compatibility and logging:
     * tenantRole when present and non-blank, otherwise systemRole.
     */
    public String effectiveRole() {
        if (tenantRole != null && !tenantRole.isBlank()) {
            return tenantRole;
        }
        return systemRole;
    }
}
