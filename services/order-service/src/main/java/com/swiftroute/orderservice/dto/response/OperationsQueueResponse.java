package com.swiftroute.orderservice.dto.response;

import java.util.List;

/**
 * Tenant-scoped work queues used by dispatchers to operate the last-mile flow.
 */
public record OperationsQueueResponse(
        List<OrderResponse> readyForDispatch,
        List<OrderResponse> active,
        List<OrderResponse> intervention) {}
