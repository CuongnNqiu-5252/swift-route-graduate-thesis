package com.swiftroute.authservice.controller;

import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import com.swiftroute.authservice.dto.response.ApiResponse;
import com.swiftroute.authservice.service.TenantIdentityService;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class TenantIdentityController {
    private final TenantIdentityService identities;
    @GetMapping("/context")
    public ApiResponse<Map<String,Object>> context(Authentication auth, @RequestParam(required=false) UUID tenantId) {
        return ApiResponse.success(identities.context(UUID.fromString(auth.getName()),tenantId));
    }
    @GetMapping("/tenants")
    public ApiResponse<List<Map<String,Object>>> list(Authentication auth) {
        return ApiResponse.success(identities.list(UUID.fromString(auth.getName())));
    }
    @PostMapping("/tenants/{tenantId}/switch")
    public ApiResponse<Map<String,Object>> switchTenant(Authentication auth, @PathVariable UUID tenantId) {
        return ApiResponse.success(identities.switchTenant(UUID.fromString(auth.getName()),tenantId));
    }
}
