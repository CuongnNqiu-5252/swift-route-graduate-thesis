package com.swiftroute.orderservice.controller;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import com.swiftroute.orderservice.dto.response.ApiResponse;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import com.swiftroute.orderservice.security.TenantRequestInterceptor;
import com.swiftroute.orderservice.service.weather.RouteWeatherService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders/dispatch/weather-risks")
@RequiredArgsConstructor
public class RouteWeatherController {
    private final RouteWeatherService weather;
    private static final Set<String> ROLES = Set.of("OWNER", "TENANT_ADMIN", "OPERATOR");

    @GetMapping
    public ApiResponse<RouteWeatherService.Report> risks(
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId,
            @RequestAttribute("effectiveRole") String role,
            @RequestParam(required = false) UUID planId,
            @RequestParam(defaultValue = "next_24_hours") String period) {
        if (tenantId == null || !ROLES.contains(role.toUpperCase(Locale.ROOT))
                || !TenantRequestInterceptor.isAuthorizedForPermission(role, "order:read", ROLES)
                || !(TenantRequestInterceptor.isAuthorizedForPermission(role, "dispatch:read", ROLES)
                    || TenantRequestInterceptor.isAuthorizedForPermission(role, "dispatch:execute", ROLES))) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        if (!Set.of("today", "today_afternoon", "tomorrow", "next_24_hours").contains(period)) {
            throw new IllegalArgumentException("Unsupported forecast period");
        }
        return ApiResponse.success(weather.evaluate(tenantId, planId, period));
    }
}
