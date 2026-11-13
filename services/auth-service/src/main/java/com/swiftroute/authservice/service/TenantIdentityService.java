package com.swiftroute.authservice.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftroute.authservice.entity.TenantMembership;
import com.swiftroute.authservice.entity.User;
import com.swiftroute.authservice.entity.enums.Role;
import com.swiftroute.authservice.entity.enums.TenantMembershipRole;
import com.swiftroute.authservice.entity.enums.TenantMembershipStatus;
import com.swiftroute.authservice.exception.AppException;
import com.swiftroute.authservice.exception.ErrorCode;
import com.swiftroute.authservice.repository.TenantMembershipRepository;
import com.swiftroute.authservice.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TenantIdentityService {
    private final UserRepository users;
    private final TenantMembershipRepository memberships;
    private final JwtService jwt;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public Map<String, Object> context(UUID userId, UUID tenantId) {
        User user = users.findById(userId).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        if (!"ACTIVE".equals(user.getStatus()))
            throw new AppException(ErrorCode.ACCESS_DENIED);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user_id", userId);
        result.put("email", user.getEmail());
        result.put("email_verified", user.isEmailVerified());
        result.put("system_role", user.getRole().name());
        if (tenantId != null) {
            TenantMembership m = memberships.findByTenantIdAndUser_Id(tenantId, userId).orElse(null);
            boolean admin = Role.SYSTEM_ADMIN == user.getRole();
            if (!admin && (m == null || m.getStatus() != TenantMembershipStatus.ACTIVE))
                throw new AppException(ErrorCode.ACCESS_DENIED);
            result.put("membership_role", m != null ? m.getMembershipRole().name() : "SYSTEM_ADMIN");
            result.put("tenant_id", tenantId);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(UUID userId) {
        return memberships.findByUser_IdAndStatus(userId, TenantMembershipStatus.ACTIVE).stream()
                .map(m -> Map.<String, Object>of("tenant_id", m.getTenantId(), "role", m.getMembershipRole().name(),
                        "status", m.getStatus().name()))
                .toList();
    }

    @Transactional
    public Map<String, Object> switchTenant(UUID userId, UUID tenantId) {
        context(userId, tenantId);
        User user = users.findById(userId).orElseThrow();
        TenantMembership m = memberships.findByTenantIdAndUser_Id(tenantId, userId).orElse(null);
        String role = m != null ? m.getMembershipRole().name() : null;
        return Map.of("access_token", jwt.generateTenantAccessToken(user, tenantId, role, 15), "tenant_id", tenantId);
    }

    @Transactional
    public void provision(UUID tenantId, UUID userId) {
        jdbc.execute("SELECT pg_advisory_xact_lock(" + (long) tenantId.hashCode() + ")");
        User user = users.findById(userId).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        if (!user.isEmailVerified())
            throw new AppException(ErrorCode.EMAIL_NOT_VERIFIED);
        jdbc.update(
                "INSERT INTO tenant_memberships (membership_id, tenant_id, user_id, membership_role, status, last_event_version, created_at, updated_at) VALUES (?, ?, ?, 'OWNER', 'ACTIVE', 0, now(), now()) ON CONFLICT (tenant_id, user_id) DO NOTHING",
                UUID.randomUUID(), tenantId, userId);
        TenantMembership m = memberships.findByTenantIdAndUser_Id(tenantId, userId).orElseThrow();
        if (m.getMembershipRole() != TenantMembershipRole.OWNER || m.getStatus() != TenantMembershipStatus.ACTIVE)
            throw new AppException(ErrorCode.ACCESS_DENIED);
    }
}
