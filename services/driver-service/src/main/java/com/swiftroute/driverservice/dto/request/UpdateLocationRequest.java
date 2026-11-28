package com.swiftroute.driverservice.dto.request;

import java.time.Instant;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateLocationRequest(
        @NotNull(message = "lat is required")
        @DecimalMin(value = "-90.0", message = "lat must be >= -90.0")
        @DecimalMax(value = "90.0", message = "lat must be <= 90.0")
        Double lat,

        @NotNull(message = "lng is required")
        @DecimalMin(value = "-180.0", message = "lng must be >= -180.0")
        @DecimalMax(value = "180.0", message = "lng must be <= 180.0")
        Double lng,

        @DecimalMin("0.0") @DecimalMax("1000.0") Double accuracyM,
        @DecimalMin("0.0") @DecimalMax("100.0") Double speedMps,
        @DecimalMin("0.0") @DecimalMax("360.0") Double bearing,
        Instant recordedAt,
        @PositiveOrZero Long sequence
) {
    public UpdateLocationRequest {
        if (recordedAt == null) recordedAt = Instant.now();
        if (sequence == null) sequence = recordedAt.toEpochMilli();
    }

    public UpdateLocationRequest(Double lat, Double lng) {
        this(lat, lng, null, null, null, null, null);
    }

    public Instant effectiveRecordedAt() {
        return recordedAt;
    }

    public long effectiveSequence() {
        return sequence;
    }
}
