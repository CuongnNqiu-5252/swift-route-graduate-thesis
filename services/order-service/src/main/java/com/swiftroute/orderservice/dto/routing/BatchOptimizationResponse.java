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
        @JsonProperty("execution_time_ms") double executionTimeMs,
        @JsonProperty("timestamp") String timestamp
) {

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

