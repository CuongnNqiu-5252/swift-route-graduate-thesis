package com.swiftroute.orderservice.entity.enums;

public enum OrderStatus {
    PENDING_PAYMENT,
    PAYMENT_FAILED,
    PAID,
    PLANNED,
    IN_TRANSIT,
    DELIVERED,
    FAILED,
    CANCELLED
}
