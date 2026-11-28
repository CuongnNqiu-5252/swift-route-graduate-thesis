package com.swiftroute.orderservice.dto.response;

import java.util.Map;

import com.swiftroute.orderservice.entity.enums.OrderStatus;

public record OrderListPageResponse(
        PageResponse<OrderResponse> orders,
        Map<OrderStatus, Long> statusCounts) {
}
