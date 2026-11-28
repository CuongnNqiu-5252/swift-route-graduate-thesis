package com.swiftroute.orderservice.dto.request;

import com.swiftroute.orderservice.entity.enums.DeliveryZoneEnforcementMode;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record UpsertDeliveryZoneRequest(
        @NotNull(message = "Vĩ độ tâm vùng giao hàng không được để trống")
        @DecimalMin(value = "-90.0", message = "Vĩ độ phải nằm trong khoảng [-90, 90]")
        @DecimalMax(value = "90.0", message = "Vĩ độ phải nằm trong khoảng [-90, 90]")
        Double centerLat,

        @NotNull(message = "Kinh độ tâm vùng giao hàng không được để trống")
        @DecimalMin(value = "-180.0", message = "Kinh độ phải nằm trong khoảng [-180, 180]")
        @DecimalMax(value = "180.0", message = "Kinh độ phải nằm trong khoảng [-180, 180]")
        Double centerLng,

        @NotNull(message = "Bán kính phục vụ không được để trống")
        @DecimalMin(value = "0.5", message = "Bán kính tối thiểu là 0.5 km")
        @DecimalMax(value = "50.0", message = "Bán kính tối đa là 50 km")
        Double radiusKm,

        @NotNull(message = "Chế độ kiểm soát không được để trống")
        DeliveryZoneEnforcementMode enforcementMode,

        @NotNull(message = "Trạng thái kích hoạt không được để trống")
        Boolean isActive
) {}
