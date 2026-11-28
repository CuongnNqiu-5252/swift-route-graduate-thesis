package com.swiftroute.orderservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

public record OperationFlowSchedule(
        @NotNull @Pattern(regexp = "daily|interval") String mode,
        @NotNull @Pattern(regexp = "([01][0-9]|2[0-3]):[0-5][0-9]") String time,
        @Min(1) @Max(168) int hours) {

    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    public Instant nextRunAfter(Instant now, Instant dueSlot) {
        if ("daily".equals(mode)) {
            var localNow = now.atZone(VIETNAM);
            var today = localNow.toLocalDate().atTime(LocalTime.parse(time)).atZone(VIETNAM);
            return (today.isAfter(localNow) ? today : today.plusDays(1)).toInstant();
        }

        Instant anchor = dueSlot == null ? now.truncatedTo(ChronoUnit.MINUTES) : dueSlot;
        long period = Duration.ofHours(hours).toSeconds();
        long elapsed = Duration.between(anchor, now).toSeconds();
        return anchor.plusSeconds((elapsed / period + 1) * period);
    }
}
