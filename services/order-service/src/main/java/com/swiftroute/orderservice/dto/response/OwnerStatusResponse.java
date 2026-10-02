package com.swiftroute.orderservice.dto.response;

import java.util.UUID;

/** Internal response returned by Auth Service when Order Service verifies a tenant owner. */
public record OwnerStatusResponse(UUID userId, boolean active) {
}
