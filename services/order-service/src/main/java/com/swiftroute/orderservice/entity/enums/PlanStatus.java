package com.swiftroute.orderservice.entity.enums;

/**
 * Lifecycle states for a dispatch plan.
 *
 * <pre>
 * DRAFT ──► APPROVED ──► DISPATCHED ──► COMPLETED
 * </pre>
 *
 * Cancelled or emptied plans are deleted directly and their orders are
 * returned to {@code READY_FOR_DISPATCH}.
 */
public enum PlanStatus {
    DRAFT,
    APPROVED,
    DISPATCHED,
    COMPLETED
}

