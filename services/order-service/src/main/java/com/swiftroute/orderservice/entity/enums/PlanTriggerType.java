package com.swiftroute.orderservice.entity.enums;

/**
 * Reason a planning cycle was triggered.
 */
public enum PlanTriggerType {
    /** Pending order count reached the configured batch_size_threshold. */
    BATCH_SIZE,
    /** Oldest pending order waited longer than max_wait_time_seconds. */
    MAX_WAIT,
    /** Scheduled dispatch window (cron-based trigger). */
    SCHEDULED,
    /** An URGENT-priority order arrived and triggered immediate planning. */
    URGENT,
    /** Operator pressed the manual "Trigger planning now" button in Web Admin. */
    MANUAL
}

