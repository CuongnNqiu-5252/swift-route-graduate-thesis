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
                @JsonProperty("total_distance_km") BigDecimal totalDistanceKm,
                @JsonProperty("estimated_duration_minutes") Integer estimatedDurationMinutes,
                @JsonProperty("trigger_type") String triggerType,
                @JsonProperty("order_count") int orderCount,
                @JsonProperty("ordered_stop_ids") List<UUID> orderedStopIds,
                @JsonProperty("dispatched_at") Instant dispatchedAt,
                @JsonProperty("route_waypoints") String routeWaypoints,
                @JsonProperty("created_at") Instant createdAt,
                @JsonProperty("updated_at") Instant updatedAt,
                @JsonProperty("version") Long version) {
}
