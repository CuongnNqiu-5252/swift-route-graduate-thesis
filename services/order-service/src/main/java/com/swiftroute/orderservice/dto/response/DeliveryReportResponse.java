package com.swiftroute.orderservice.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record DeliveryReportResponse(
        List<Day> days,
        List<Failure> failures,
        long overdue,
        List<Warehouse> warehouses) {

    public record Day(LocalDate date, long delivered, long withDeadline, long onTime,
                      long firstAttempts, long firstSuccesses, long failures) {}

    public record Failure(String reason, long count) {}

    public record Warehouse(UUID id, String name) {}

    public record Detail(UUID orderId, String orderCode, OffsetDateTime completedAt,
                         OffsetDateTime deadline, int attemptNumber) {}
}
