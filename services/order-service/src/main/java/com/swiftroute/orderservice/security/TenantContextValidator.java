package com.swiftroute.orderservice.security;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.swiftroute.orderservice.entity.Tenant;
import com.swiftroute.orderservice.entity.enums.TenantStatus;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import com.swiftroute.orderservice.repository.TenantRepository;
import com.swiftroute.orderservice.service.OperationalReadinessService;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Validates tenant access authoritatively at the backend service layer.
 * Enforces stateless JWT claims (system_role, tenant_id, tenant_role) and
 * database tenant lifecycle status (single source of truth in order_db.tenants).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenantContextValidator {

    private final JwtTokenValidator jwtTokenValidator;
    private final TenantRepository tenantRepository;

    /**
     * Authoritatively resolves and validates tenant access for the current workspace request.
     *
     * @param tenantIdHeader Header X-Tenant-Id (optional if present in JWT)
     * @param authHeader     Header Authorization Bearer token
     * @return Validated Tenant entity
     */
    public Tenant validateWorkspaceAccess(String tenantIdHeader, String authHeader) {
        Claims claims = parseRequiredClaims(authHeader);
        String effectiveTenantStr = resolveAuthorizedTenantString(tenantIdHeader, claims);
        UUID tenantId = parseTenantUuid(effectiveTenantStr);
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.TENANT_NOT_FOUND));
    }

    public Tenant validateWorkspaceOwner(String tenantIdHeader, String authHeader) {
        Claims claims = parseRequiredClaims(authHeader);
        String systemRole = claims.get("system_role", String.class);
        String tenantRole = claims.get("tenant_role", String.class);
        if (!"SYSTEM_ADMIN".equalsIgnoreCase(systemRole) && !"OWNER".equalsIgnoreCase(tenantRole)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        String effectiveTenantStr = resolveAuthorizedTenantString(tenantIdHeader, claims);
        UUID tenantId = parseTenantUuid(effectiveTenantStr);
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.TENANT_NOT_FOUND));
    }

    public Tenant validateTenantAccess(String tenantIdHeader, String authHeader) {
        String effectiveTenantStr;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            Claims claims = parseRequiredClaims(authHeader);
            effectiveTenantStr = resolveAuthorizedTenantString(tenantIdHeader, claims);
        } else {
            effectiveTenantStr = tenantIdHeader;
        }

        if (effectiveTenantStr == null || effectiveTenantStr.isBlank()) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        UUID tenantId = parseTenantUuid(effectiveTenantStr);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.TENANT_NOT_FOUND));

        OperationalReadinessService.requireApproved(tenant);
        return tenant;
    }

    /**
     * Extracts the authenticated user ID (JWT subject) without performing additional lookups.
     */
    public Optional<UUID> extractUserId(String authHeader) {
        Claims claims = jwtTokenValidator.parseAndValidate(authHeader);
        if (claims == null || claims.getSubject() == null || claims.getSubject().isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(claims.getSubject().trim()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private Claims parseRequiredClaims(String authHeader) {
        Claims claims = jwtTokenValidator.parseAndValidate(authHeader);
        if (claims == null) {
            log.warn("Invalid or expired JWT bearer token provided to order-service");
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return claims;
    }

    private String resolveAuthorizedTenantString(String tenantIdHeader, Claims claims) {
        String systemRole = claims.get("system_role", String.class);
        String tokenTenantId = claims.get("tenant_id", String.class);
        String tokenTenantRole = claims.get("tenant_role", String.class);

        if ("SYSTEM_ADMIN".equalsIgnoreCase(systemRole)) {
            String target = (tenantIdHeader != null && !tenantIdHeader.isBlank()) ? tenantIdHeader : tokenTenantId;
            if (target == null || target.isBlank()) {
                throw new AppException(ErrorCode.ACCESS_DENIED);
            }
            return target;
        }

        if (tokenTenantId == null || tokenTenantId.isBlank()
                || tokenTenantRole == null || tokenTenantRole.isBlank()) {
            log.warn("User {} with systemRole {} has no tenant membership claim in JWT",
                    claims.getSubject(), systemRole);
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        if (tenantIdHeader != null && !tenantIdHeader.isBlank()
                && !tenantIdHeader.trim().equalsIgnoreCase(tokenTenantId.trim())) {
            log.warn("Cross-tenant attempt blocked: token.tenantId={}, requested={}",
                    tokenTenantId, tenantIdHeader);
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        return tokenTenantId;
    }

    private UUID parseTenantUuid(String rawTenantId) {
        try {
            return UUID.fromString(rawTenantId.trim());
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }
    }
}

