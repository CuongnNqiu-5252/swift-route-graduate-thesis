package com.swiftroute.orderservice.dto.routing;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response body received from the routing engine POST /optimize/batch endpoint.
 * Fields must match the Python BatchOptimizationResponse schema exactly.
 */
public record BatchOptimizationResponse(
        @JsonProperty("job_id") String jobId,
        @JsonProperty("algorithm_used") String algorithmUsed,
        @JsonProperty("total_distance_km") double totalDistanceKm,
        @JsonProperty("total_duration_minutes") double totalDurationMinutes,
        @JsonProperty("ordered_stops") List<OrderedStop> orderedStops,
        @JsonProperty("vehicle_routes") List<VehicleRoute> vehicleRoutes,
        @JsonProperty("execution_time_ms") double executionTimeMs,
        @JsonProperty("timestamp") String timestamp
) {

    public BatchOptimizationResponse(
            String jobId,
            String algorithmUsed,
            double totalDistanceKm,
            double totalDurationMinutes,
            List<OrderedStop> orderedStops,
            double executionTimeMs,
            String timestamp
    ) {
        this(jobId, algorithmUsed, totalDistanceKm, totalDurationMinutes, orderedStops, List.of(), executionTimeMs, timestamp);
    }

    public record VehicleRoute(
            @JsonProperty("vehicle_index") int vehicleIndex,
            @JsonProperty("total_distance_km") double totalDistanceKm,
            @JsonProperty("total_duration_minutes") double totalDurationMinutes,
            @JsonProperty("total_load_kg") double totalLoadKg,
            @JsonProperty("ordered_stops") List<OrderedStop> orderedStops
    ) {}

    public record OrderedStop(
            @JsonProperty("stop_index") int stopIndex,
            @JsonProperty("stop_id") String stopId,
            @JsonProperty("location") LocationPoint location,
            @JsonProperty("cumulative_distance_km") double cumulativeDistanceKm,
            @JsonProperty("estimated_arrival_minutes") double estimatedArrivalMinutes
    ) {}

    public record LocationPoint(
            @JsonProperty("lat") double lat,
            @JsonProperty("lng") double lng,
            @JsonProperty("address") String address
    ) {}
}

