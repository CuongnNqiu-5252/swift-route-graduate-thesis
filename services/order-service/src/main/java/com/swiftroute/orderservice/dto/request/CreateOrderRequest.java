package com.swiftroute.orderservice.dto.request;

import java.time.LocalTime;
import java.util.UUID;

import com.swiftroute.orderservice.entity.enums.OrderPriority;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * DTO nhận dữ liệu từ Frontend khi tạo đơn hàng.
 *
 * tenantId được lấy từ header X-Tenant-Id (inject bởi Gateway),
 * không cần client truyền lên để tránh giả mạo.
 * customerId cũng tương tự, lấy từ X-User-Id.
 *
 * Dùng record để immutable + compact.
 */
public record CreateOrderRequest(

                @NotNull(message = "Vĩ độ điểm lấy hàng không được để trống") @DecimalMin(value = "-90.0", message = "Vĩ độ phải từ -90 đến 90") @DecimalMax(value = "90.0", message = "Vĩ độ phải từ -90 đến 90") Double pickupLat,

                @NotNull(message = "Kinh độ điểm lấy hàng không được để trống") @DecimalMin(value = "-180.0", message = "Kinh độ phải từ -180 đến 180") @DecimalMax(value = "180.0", message = "Kinh độ phải từ -180 đến 180") Double pickupLng,

                @NotNull(message = "Vĩ độ điểm giao hàng không được để trống") @DecimalMin(value = "-90.0", message = "Vĩ độ phải từ -90 đến 90") @DecimalMax(value = "90.0", message = "Vĩ độ phải từ -90 đến 90") Double deliveryLat,

                @NotNull(message = "Kinh độ điểm giao hàng không được để trống") @DecimalMin(value = "-180.0", message = "Kinh độ phải từ -180 đến 180") @DecimalMax(value = "180.0", message = "Kinh độ phải từ -180 đến 180") Double deliveryLng,

                @Size(max = 500, message = "Địa chỉ giao hàng tối đa 500 ký tự") String deliveryAddress,

                @NotNull(message = "Thời gian bắt đầu giao hàng không được để trống") LocalTime timeWindowStart,

                @NotNull(message = "Thời gian kết thúc giao hàng không được để trống") LocalTime timeWindowEnd,

                @NotNull(message = "Khối lượng hàng không được để trống") @Positive(message = "Khối lượng phải lớn hơn 0") Double weightKg,

                // Nullable — mặc định NORMAL nếu không truyền
                OrderPriority priority,

                // UUID của Tenant (do chính doanh nghiệp truyền lên khi tạo đơn hộ)
                // Nếu null → hệ thống lấy X-Tenant-Id từ header
                UUID tenantId) {
}
