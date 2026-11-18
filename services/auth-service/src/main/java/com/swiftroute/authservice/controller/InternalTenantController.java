package com.swiftroute.authservice.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import com.swiftroute.authservice.service.TenantIdentityService;
import com.swiftroute.authservice.exception.*;
import com.swiftroute.authservice.entity.enums.TenantMembershipStatus;
import com.swiftroute.authservice.repository.TenantMembershipRepository;
import lombok.RequiredArgsConstructor;

/** Not routed by the public gateway. Requires a private service credential. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/tenant-memberships")
public class InternalTenantController {
    private final TenantIdentityService identities;
    private final TenantMembershipRepository memberships;
    @Value("${tenant.internal-key:}") private String internalKey;
    public record Provision(UUID tenantId, UUID ownerUserId) {}
    @PostMapping("/provision")
    public void provision(@RequestHeader("X-Service-Key") String key, @RequestBody Provision request) {
        requireServiceKey(key);
        if (request.tenantId() == null || request.ownerUserId() == null) throw new AppException(ErrorCode.VALIDATION_ERROR);
        identities.provision(request.tenantId(),request.ownerUserId());
    }

    public record ChatMember(UUID id, String name, String role) {}

    @GetMapping("/{tenantId}/chat-members")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<ChatMember> chatMembers(@RequestHeader("X-Service-Key") String key, @PathVariable UUID tenantId) {
        requireServiceKey(key);
        return memberships.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, TenantMembershipStatus.ACTIVE)
                .stream()
                .filter(member -> "ACTIVE".equals(member.getUser().getStatus()))
                .map(member -> {
                    var user = member.getUser();
                    String name = user.getFullName();
                    return new ChatMember(user.getId(), name == null || name.isBlank() ? "Thành viên" : name,
                            member.getMembershipRole().name());
                })
                .toList();
    }

    private void requireServiceKey(String key) {
        if (internalKey.isBlank() || !MessageDigest.isEqual(
                internalKey.getBytes(StandardCharsets.UTF_8), key.getBytes(StandardCharsets.UTF_8))) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
    }
}
