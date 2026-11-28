package com.swiftroute.orderservice.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.swiftroute.orderservice.dto.response.ApiResponse;
import com.swiftroute.orderservice.dto.response.OrderTrackingLinkResponse;
import com.swiftroute.orderservice.entity.OrderTrackingLink;
import com.swiftroute.orderservice.service.OrderTrackingLinkService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/orders/{orderId}/tracking-link")
public class OrderTrackingLinkController {

    private final OrderTrackingLinkService service;

    @PostMapping
    public ResponseEntity<ApiResponse<OrderTrackingLinkResponse>> createTrackingLink(
            @RequestAttribute("authorizedTenantId") UUID tenantId,
            @PathVariable UUID orderId) {
        OrderTrackingLink link = service.getOrCreateLink(tenantId, orderId);

        OrderTrackingLinkResponse response = new OrderTrackingLinkResponse(
                link.getOrderId(),
                service.generateTrackingUrl(link),
                link.getExpiresAt());

        return ResponseEntity.ok(
                ApiResponse.success(response));
    }

    @DeleteMapping
    public ResponseEntity<Void> revokeTrackingLink(
            @RequestAttribute("authorizedTenantId") UUID tenantId,
            @PathVariable UUID orderId) {
        service.revokeLink(tenantId, orderId);

        return ResponseEntity.noContent().build();
    }
}
