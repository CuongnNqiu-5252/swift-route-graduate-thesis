package com.swiftroute.orderservice.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Deterministic business rules and preferences for automated dispatch.
 * Stored as JSONB in the tenants table.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TenantDispatchConfig {

    /**
     * Whether automated dispatch evaluation is active for this tenant.
     * When false, orders remain in READY_FOR_DISPATCH state until manually
     * optimized via Web
     * Admin.
     */
    @JsonProperty("auto_dispatch_enabled")
    @Builder.Default
    private Boolean autoDispatchEnabled = true;

    /**
     * Order count threshold to trigger immediate batch optimization.
     */
    @JsonProperty("batch_size_threshold")
    @NotNull
    @Min(1)
    @Max(50)
    @Builder.Default
    private Integer batchSizeThreshold = 5;

    /**
     * Maximum wait time (in seconds) for the oldest pending order before
     * triggering batch optimization, even if batch_size_threshold is not yet
     * reached.
     */
    @JsonProperty("max_wait_time_seconds")
    @NotNull
    @Min(30)
    @Max(3600)
    @Builder.Default
    private Integer maxWaitTimeSeconds = 300;

    /**
     * Maximum number of order stops assigned to a single driver in one batch.
     */
    @JsonProperty("max_orders_per_driver")
    @NotNull
    @Min(1)
    @Max(20)
    @Builder.Default
    private Integer maxOrdersPerDriver = 5;

    /**
     * Whether daily scheduled wave dispatch is enabled for this tenant.
     */
    @JsonProperty("scheduled_dispatch_enabled")
    @Builder.Default
    private Boolean scheduledDispatchEnabled = true;

    /**
     * Daily scheduled dispatch trigger time in HH:mm (24-hour Asia/Ho_Chi_Minh).
     */
    @JsonProperty("scheduled_dispatch_time")
    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "Format must be HH:mm (00:00 - 23:59)")
    @Builder.Default
    private String scheduledDispatchTime = "06:00";

}
