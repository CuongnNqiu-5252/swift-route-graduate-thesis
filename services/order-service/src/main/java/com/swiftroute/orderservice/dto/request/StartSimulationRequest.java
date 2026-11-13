package com.swiftroute.orderservice.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

/**
 * Payload for triggering a server-side GPS route simulation along decoded street waypoints.
 */
public record StartSimulationRequest(
        @NotEmpty(message = "Waypoints list must not be empty")
        @Valid
        List<Waypoint> waypoints,
        Integer intervalMs,
        Integer pauseAtIndex
) {
    public record Waypoint(
            double lat,
            double lng
    ) {}
}

