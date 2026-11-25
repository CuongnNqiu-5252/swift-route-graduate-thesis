package com.swiftroute.orderservice.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.swiftroute.orderservice.dto.response.ApiResponse;
import com.swiftroute.orderservice.dto.response.OrderTimelineResponse;
import com.swiftroute.orderservice.service.OrderTimelineService;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

/**
 * Provides unified chronological activity timelines for individual orders.
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderTimelineController {

    private final OrderTimelineService timelineService;

    @Operation(summary = "Lấy dòng thời gian lịch sử tổng hợp của đơn hàng")
    @GetMapping("/{orderId}/timeline")
    public ResponseEntity<ApiResponse<OrderTimelineResponse>> getOrderTimeline(
            @PathVariable UUID orderId,
            @RequestAttribute("authorizedUserId") UUID userId,
            @RequestAttribute("effectiveRole") String userRole,
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId) {
        return ResponseEntity.ok(ApiResponse.success(
                timelineService.getOrderTimeline(orderId, userId, userRole, tenantId)));
    }
}
