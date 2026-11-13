package com.swiftroute.authservice.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import com.swiftroute.authservice.service.TenantIdentityService;
import com.swiftroute.authservice.exception.*;
import lombok.RequiredArgsConstructor;

/** Not routed by the public gateway. Requires a private service credential. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/tenant-memberships")
public class InternalTenantController {
    private final TenantIdentityService identities;
    @Value("${tenant.internal-key:}") private String internalKey;
    public record Provision(UUID tenantId, UUID ownerUserId) {}
    @PostMapping("/provision")
    public void provision(@RequestHeader("X-Service-Key") String key, @RequestBody Provision request) {
        if (internalKey.isBlank() || !MessageDigest.isEqual(internalKey.getBytes(StandardCharsets.UTF_8), key.getBytes(StandardCharsets.UTF_8)))
            throw new AppException(ErrorCode.ACCESS_DENIED);
        if (request.tenantId() == null || request.ownerUserId() == null) throw new AppException(ErrorCode.VALIDATION_ERROR);
        identities.provision(request.tenantId(),request.ownerUserId());
    }
}
