package com.swiftroute.orderservice.dto.request;

import jakarta.validation.constraints.Size;

public record ConfirmReturnReceiptRequest(@Size(max = 1000) String notes) {
}
