package com.swiftroute.orderservice.dto.routing;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.swiftroute.orderservice.dto.routing.BatchOptimizationRequest.LocationPoint;
import com.swiftroute.orderservice.dto.routing.BatchOptimizationRequest.StopItem;
import com.swiftroute.orderservice.dto.routing.BatchOptimizationResponse.OrderedStop;

/**
 * Request/Response DTOs for POST /optimize/insert and POST /optimize/reoptimize (P3.1 & P3.2).
 */
public final class DynamicRoutingDtos {

    private DynamicRoutingDtos() {}

    public record RouteStopState(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("location") LocationPoint location,
            @JsonProperty("demand_kg") double demandKg,
            @JsonProperty("time_window_start") String timeWindowStart,
            @JsonProperty("time_window_end") String timeWindowEnd,
            @JsonProperty("is_completed") boolean isCompleted
    ) {}

    public record RouteInsertRequest(
            @JsonProperty("driver_location") LocationPoint driverLocation,
            @JsonProperty("current_stops") List<RouteStopState> currentStops,
            @JsonProperty("new_stop") StopItem newStop,
            @JsonProperty("max_added_duration_minutes") double maxAddedDurationMinutes,
            @JsonProperty("time_weight") Double timeWeight,
            @JsonProperty("cost_weight") Double costWeight
    ) {}

    public record RouteInsertResponse(
            @JsonProperty("job_id") String jobId,
            @JsonProperty("algorithm_used") String algorithmUsed,
            @JsonProperty("feasible") boolean feasible,
            @JsonProperty("inserted_stop_index") int insertedStopIndex,
            @JsonProperty("added_distance_km") double addedDistanceKm,
            @JsonProperty("added_duration_minutes") double addedDurationMinutes,
            @JsonProperty("total_distance_km") double totalDistanceKm,
            @JsonProperty("total_duration_minutes") double totalDurationMinutes,
            @JsonProperty("ordered_stops") List<OrderedStop> orderedStops,
            @JsonProperty("execution_time_ms") double executionTimeMs,
            @JsonProperty("timestamp") String timestamp
    ) {}

    public record RouteReoptimizeRequest(
            @JsonProperty("driver_location") LocationPoint driverLocation,
            @JsonProperty("current_stops") List<RouteStopState> currentStops,
            @JsonProperty("reason") String reason,
            @JsonProperty("min_improvement_km") double minImprovementKm,
            @JsonProperty("time_weight") Double timeWeight,
            @JsonProperty("cost_weight") Double costWeight
    ) {}

    public record RouteReoptimizeResponse(
            @JsonProperty("job_id") String jobId,
            @JsonProperty("algorithm_used") String algorithmUsed,
            @JsonProperty("stops_changed") boolean stopsChanged,
            @JsonProperty("distance_saved_km") double distanceSavedKm,
            @JsonProperty("total_distance_km") double totalDistanceKm,
            @JsonProperty("total_duration_minutes") double totalDurationMinutes,
            @JsonProperty("ordered_stops") List<OrderedStop> orderedStops,
            @JsonProperty("execution_time_ms") double executionTimeMs,
            @JsonProperty("timestamp") String timestamp
    ) {}
}

