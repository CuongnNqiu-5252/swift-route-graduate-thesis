package com.swiftroute.orderservice.controller;

import java.time.Instant;
import java.util.UUID;
import com.swiftroute.orderservice.dto.response.ApiResponse;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import com.swiftroute.orderservice.security.JwtTokenValidator;
import com.swiftroute.orderservice.service.platform.PlatformReadService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tenants/platform")
@RequiredArgsConstructor
public class PlatformConsoleController {
    private final JwtTokenValidator tokens;
    private final PlatformReadService reads;

    @GetMapping("/audit")
    public ApiResponse<PlatformReadService.AuditPage> history(@RequestHeader("Authorization") String auth,
            @RequestParam(defaultValue = "") String query, @RequestParam(defaultValue = "") String category,
            @RequestParam(defaultValue = "") String severity, @RequestParam(defaultValue = "7") int days,
            @RequestParam(required = false) Instant beforeTime, @RequestParam(required = false) UUID beforeId) {
        requireAdmin(auth);
        return ApiResponse.success(reads.history(new PlatformReadService.Filter(query, category, severity, days, beforeTime, beforeId), 50));
    }

    @GetMapping("/snapshot")
    public ApiResponse<PlatformReadService.Snapshot> snapshot(@RequestHeader("Authorization") String auth) {
        requireAdmin(auth);
        return ApiResponse.success(reads.snapshot());
    }

    private Claims requireAdmin(String auth) {
        var claims = tokens.parseAndValidate(auth);
        if (claims == null || claims.getExpiration() == null) throw new AppException(ErrorCode.UNAUTHENTICATED);
        if (!"SYSTEM_ADMIN".equalsIgnoreCase(claims.get("system_role", String.class))) throw new AppException(ErrorCode.ACCESS_DENIED);
        return claims;
    }
}
