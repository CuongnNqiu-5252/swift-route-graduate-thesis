package com.swiftroute.orderservice.dto.request;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SubmitCodHandoverRequest(
        @NotNull UUID operationId,
        List<UUID> collectionIds,
        List<UUID> orderIds,
        @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 0) BigDecimal declaredTotal,
        @Size(max = 1000) String notes) {

    public SubmitCodHandoverRequest(UUID operationId, List<UUID> orderIds, BigDecimal declaredTotal, String notes) {
        this(operationId, orderIds, orderIds, declaredTotal, notes);
    }

    public List<UUID> effectiveOrderIds() {
        if (orderIds != null && !orderIds.isEmpty()) {
            return orderIds;
        }
        return collectionIds != null ? collectionIds : List.of();
    }
}
