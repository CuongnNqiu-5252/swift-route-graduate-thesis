package com.swiftroute.orderservice.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO for a dispatch plan.
 */
public record DispatchPlanResponse(
        @JsonProperty("plan_id") UUID planId,
        @JsonProperty("tenant_id") UUID tenantId,
        @JsonProperty("plan_status") String planStatus,
        @JsonProperty("driver_id") UUID driverId,
        @JsonProperty("algorithm_used") String algorithmUsed,
        @JsonProperty("total_distance_km") BigDecimal totalDistanceKm,
        @JsonProperty("estimated_duration_minutes") Integer estimatedDurationMinutes,
        @JsonProperty("trigger_type") String triggerType,
        @JsonProperty("requires_approval") Boolean requiresApproval,
        @JsonProperty("order_count") int orderCount,
        @JsonProperty("ordered_stop_ids") List<UUID> orderedStopIds,
        @JsonProperty("planned_at") Instant plannedAt,
        @JsonProperty("approved_at") Instant approvedAt,
        @JsonProperty("dispatched_at") Instant dispatchedAt,
        @JsonProperty("cancelled_at") Instant cancelledAt,
        @JsonProperty("cancel_reason") String cancelReason,
        @JsonProperty("ack_deadline_at") Instant ackDeadlineAt,
        @JsonProperty("acknowledged_at") Instant acknowledgedAt,
        @JsonProperty("route_waypoints") String routeWaypoints,
        @JsonProperty("created_at") Instant createdAt
) {}

