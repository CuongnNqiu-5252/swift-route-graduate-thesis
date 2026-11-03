package com.swiftroute.orderservice.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DriverCodCollectionResponse(UUID collectionId, UUID orderId, String orderCode, BigDecimal amount, String notes, Instant collectedAt) {}
