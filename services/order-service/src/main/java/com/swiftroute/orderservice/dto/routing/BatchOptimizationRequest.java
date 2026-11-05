package com.swiftroute.orderservice.dto.routing;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body sent to the routing engine POST /optimize/batch endpoint.
 * Fields must match the Python BatchOptimizationRequest schema exactly.
 */
public record BatchOptimizationRequest(
        @JsonProperty("depot") LocationPoint depot,
        @JsonProperty("stops") List<StopItem> stops,
        @JsonProperty("algorithm") String algorithm,
        @JsonProperty("time_weight") Double timeWeight,
        @JsonProperty("cost_weight") Double costWeight
) {

    public record LocationPoint(
            @JsonProperty("lat") double lat,
            @JsonProperty("lng") double lng,
            @JsonProperty("address") String address
    ) {}

    public record StopItem(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("location") LocationPoint location,
            @JsonProperty("demand_kg") double demandKg,
            @JsonProperty("time_window_start") String timeWindowStart,
            @JsonProperty("time_window_end") String timeWindowEnd
    ) {}
}

