package com.swiftroute.orderservice.dto.request;

import java.util.UUID;

public record ApproveReturnRequest(
        UUID returnLocationId,
        UUID assignedDriverId,
        String notes
) {}

