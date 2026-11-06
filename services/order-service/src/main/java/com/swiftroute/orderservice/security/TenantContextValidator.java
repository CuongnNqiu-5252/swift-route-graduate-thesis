package com.swiftroute.orderservice.security;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.swiftroute.orderservice.entity.Tenant;
import com.swiftroute.orderservice.entity.enums.TenantStatus;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import com.swiftroute.orderservice.repository.TenantRepository;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Validates tenant access authoritatively at the backend service layer.
 * Does NOT merely trust client headers; validates against JWT claims (when present)
 * and database tenant lifecycle status (single source of truth in order_db.tenants).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenantContextValidator {

    private final JwtTokenValidator jwtTokenValidator;
    private final TenantRepository tenantRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private TenantIdentityClient identities;

    /**
     * Authoritatively resolves and validates tenant access for the current request.
     *
     * @param tenantIdHeader Header X-Tenant-Id (optional if present in JWT)
     * @param authHeader     Header Authorization Bearer token (optional if forwarded via Gateway)
     * @return Validated active Tenant entity
     */
    public Tenant validateWorkspaceAccess(String tenantIdHeader, String authHeader) {
        var claims=jwtTokenValidator.parseAndValidate(authHeader);
        if (claims==null) throw new AppException(ErrorCode.UNAUTHENTICATED);
        String id=tenantIdHeader!=null && !tenantIdHeader.isBlank() ? tenantIdHeader : claims.get("tenant_id",String.class);
        if (id==null) throw new AppException(ErrorCode.ACCESS_DENIED);
        UUID tenantId=UUID.fromString(id);
        identities.context(authHeader,tenantId);
        return tenantRepository.findById(tenantId).orElseThrow(() -> new AppException(ErrorCode.TENANT_NOT_FOUND));
    }
    public Tenant validateWorkspaceOwner(String tenantIdHeader,String authHeader) {
        Tenant tenant=validateWorkspaceAccess(tenantIdHeader,authHeader);
        identities.requireOwner(identities.context(authHeader,tenant.getId())); return tenant;
    }

    public Tenant validateTenantAccess(String tenantIdHeader, String authHeader) {
        if (identities != null) {
            Tenant workspace=validateWorkspaceAccess(tenantIdHeader,authHeader);
            com.swiftroute.orderservice.service.OperationalReadinessService.requireApproved(workspace);
            return workspace;
        }
        String effectiveTenantStr = null;

        // 1. If Authorization header is present, enforce JWT membership claims
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            Claims claims = jwtTokenValidator.parseAndValidate(authHeader);
            if (claims == null) {
                log.warn("Invalid or expired JWT bearer token provided to order-service");
                throw new AppException(ErrorCode.UNAUTHENTICATED);
            }

            String tokenTenantId = claims.get("tenant_id", String.class);
            String tokenRole = claims.get("_ROLE", String.class);

            if (!"ADMIN".equalsIgnoreCase(tokenRole)) {
                // Non-admin MUST have tenant membership in JWT
                if (tokenTenantId == null || tokenTenantId.isBlank()) {
                    log.warn("User {} with role {} has no tenant membership claim in JWT",
                            claims.getSubject(), tokenRole);
                    throw new AppException(ErrorCode.ACCESS_DENIED);
                }

                // If client also supplied an X-Tenant-Id header, it MUST match token's tenant claim
                if (tenantIdHeader != null && !tenantIdHeader.isBlank()
                        && !tenantIdHeader.trim().equalsIgnoreCase(tokenTenantId.trim())) {
                    log.warn("Cross-tenant attempt blocked: token.tenantId={}, requested={}",
                            tokenTenantId, tenantIdHeader);
                    throw new AppException(ErrorCode.ACCESS_DENIED);
                }

                effectiveTenantStr = tokenTenantId;
            } else {
                // ADMIN can specify target tenant via header, or operate in their own tenant
                if (tenantIdHeader != null && !tenantIdHeader.isBlank()) {
                    effectiveTenantStr = tenantIdHeader;
                } else {
                    effectiveTenantStr = tokenTenantId;
                }
            }
        } else {
            // 2. No JWT token provided: rely on gateway-injected X-Tenant-Id header
            effectiveTenantStr = tenantIdHeader;
        }

        if (effectiveTenantStr == null || effectiveTenantStr.isBlank()) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        UUID tenantId;
        try {
            tenantId = UUID.fromString(effectiveTenantStr.trim());
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }

        // 3. Single source of truth verification in order_db.tenants
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.TENANT_NOT_FOUND));

        if (tenant.getStatus() == TenantStatus.SUSPENDED) {
            throw new AppException(ErrorCode.TENANT_SUSPENDED);
        }
        if (tenant.getStatus() == TenantStatus.PENDING) {
            throw new AppException(ErrorCode.TENANT_PENDING);
        }
        if (tenant.getStatus() == TenantStatus.REJECTED) {
            throw new AppException(ErrorCode.TENANT_REJECTED);
        }

        return tenant;
    }
}

