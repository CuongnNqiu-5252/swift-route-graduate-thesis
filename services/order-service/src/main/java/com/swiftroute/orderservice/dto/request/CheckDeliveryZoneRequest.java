package com.swiftroute.orderservice.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record CheckDeliveryZoneRequest(
        @NotNull(message = "Vĩ độ điểm kiểm tra không được để trống")
        @DecimalMin(value = "-90.0", message = "Vĩ độ phải nằm trong khoảng [-90, 90]")
        @DecimalMax(value = "90.0", message = "Vĩ độ phải nằm trong khoảng [-90, 90]")
        Double lat,

        @NotNull(message = "Kinh độ điểm kiểm tra không được để trống")
        @DecimalMin(value = "-180.0", message = "Kinh độ phải nằm trong khoảng [-180, 180]")
        @DecimalMax(value = "180.0", message = "Kinh độ phải nằm trong khoảng [-180, 180]")
        Double lng
) {}
