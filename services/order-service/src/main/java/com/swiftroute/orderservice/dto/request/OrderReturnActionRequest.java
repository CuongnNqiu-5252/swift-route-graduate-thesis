package com.swiftroute.orderservice.dto.request;

import jakarta.validation.constraints.Size;

/**
 * Optional operator note when confirming warehouse return check-in or re-queueing a failed order.
 */
public record OrderReturnActionRequest(
        @Size(max = 1000) String notes) {
}
