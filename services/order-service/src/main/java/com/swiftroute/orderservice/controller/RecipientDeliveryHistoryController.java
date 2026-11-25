package com.swiftroute.orderservice.controller;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

import com.swiftroute.orderservice.dto.response.ApiResponse;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import com.swiftroute.orderservice.security.TenantRequestInterceptor;
import com.swiftroute.orderservice.service.RecipientDeliveryHistory;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
class RecipientDeliveryHistoryController {
    private static final Set<String> ROLES = Set.of("SYSTEM_ADMIN", "OWNER", "TENANT_ADMIN", "OPERATOR");
    private final RecipientDeliveryHistory history;

    @GetMapping("/api/orders/{orderId}/recipient-notifications")
    public ResponseEntity<ApiResponse<List<RecipientDeliveryHistory.Entry>>> list(
            @PathVariable UUID orderId,
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId,
            @RequestAttribute("effectiveRole") String role) {
        if (tenantId == null || !ROLES.contains(role)
                || !TenantRequestInterceptor.isAuthorizedForPermission(role, "order:read", ROLES)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(history.list(tenantId, orderId)));
    }
}
