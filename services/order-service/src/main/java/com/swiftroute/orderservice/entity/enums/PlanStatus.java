package com.swiftroute.orderservice.entity.enums;

/**
 * Lifecycle states for a dispatch plan.
 *
 * <pre>
 * DRAFT ──► APPROVED ──► DISPATCHED ──► COMPLETED
 *   │           │            │
 *   └───────────┴────────────┴──► CANCELLED
 * </pre>
 *
 * Valid transitions:
 * <ul>
 *   <li>DRAFT → APPROVED   : operator manually approves, or auto-approved when require_approval=false</li>
 *   <li>DRAFT → CANCELLED  : system/operator cancels before approval</li>
 *   <li>APPROVED → DISPATCHED : plan published to driver, driver notified</li>
 *   <li>APPROVED → CANCELLED  : operator cancels after approval but before dispatch</li>
 *   <li>DISPATCHED → COMPLETED: all delivery tasks in the plan are finished</li>
 *   <li>DISPATCHED → CANCELLED: driver decline or ack timeout triggers reassignment/cancellation</li>
 * </ul>
 */
public enum PlanStatus {
    DRAFT,
    APPROVED,
    DISPATCHED,
    COMPLETED,
    CANCELLED
}

