package com.swiftroute.orderservice.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import com.swiftroute.orderservice.repository.TenantRepository;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/tenants")
public class InternalTenantDetailsController {
    private final TenantRepository tenants;
    @Value("${tenant.internal-key:}") private String internalKey;

    @GetMapping("/{tenantId}")
    public Map<String, Object> get(@PathVariable UUID tenantId,
            @RequestHeader("X-Service-Key") String supplied) {
        if (internalKey.isBlank() || !MessageDigest.isEqual(
                internalKey.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        var tenant = tenants.findById(tenantId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return Map.of("tenantId", tenant.getId(), "businessName", tenant.getBusinessName());
    }
}
