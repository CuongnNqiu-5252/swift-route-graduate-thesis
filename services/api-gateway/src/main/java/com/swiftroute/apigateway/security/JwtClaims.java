package com.swiftroute.apigateway.security;

/**
 * Immutable record chứa các claims đã được parse từ JWT.
 *
 * Lý do dùng record thay vì class:
 *  - Immutable by design (thread-safe trong reactive context)
 *  - Compact syntax, tự sinh equals/hashCode/toString
 *
 * Mapping với claims mà auth-service đặt vào token:
 *  - sub       → userId  (UUID của user)
 *  - _ROLE     → role    (CUSTOMER, DRIVER, ADMIN, OPERATOR)
 *  - tenant_id → tenantId (UUID của tenant, có thể null)
 */
public record JwtClaims(
        String userId,
        String role,
        String tenantId
) {}
