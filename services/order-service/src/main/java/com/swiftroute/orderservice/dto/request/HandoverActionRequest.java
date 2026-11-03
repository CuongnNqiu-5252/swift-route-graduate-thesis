package com.swiftroute.orderservice.dto.request;

import jakarta.validation.constraints.Size;

public record HandoverActionRequest(@Size(max = 1000) String notes) {
}
