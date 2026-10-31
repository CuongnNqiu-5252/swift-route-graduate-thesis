package com.swiftroute.orderservice.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Domain category for delivery packages.
 * Defines business attributes such as weight limits and optional surcharges.
 *
 * Presentation details (icons, theme colors) are managed at the client
 * presentation layer,
 * keeping the domain model decoupled from UI styling concerns.
 */
@Getter
@RequiredArgsConstructor
public enum PackageCategory {

    FOOD(
            "Đồ ăn / Đồ uống",
            10.0,
            0,
            "Thức ăn nóng/lạnh, đồ uống, bánh kem (cần giao nhanh)"),

    DOCUMENT(
            "Tài liệu / Hợp đồng",
            3.0,
            0,
            "Hồ sơ, hóa đơn, bưu phẩm giấy (cần chống nước)"),

    PARCEL(
            "Hàng tiêu dùng",
            30.0,
            0,
            "Quần áo, mỹ phẩm, đồ gia dụng nhỏ"),

    MEDICAL(
            "Thuốc / Y tế",
            5.0,
            0,
            "Dược phẩm, dụng cụ y tế khẩn cấp"),

    FRAGILE(
            "Hàng dễ vỡ",
            15.0,
            5000,
            "Đồ gốm sứ, kính, thiết bị điện tử nhạy cảm");

    private final String displayName;
    private final double maxWeightKg;
    private final int surchargeVnd;
    private final String description;

    @JsonValue
    public String toValue() {
        return this.name();
    }

    @JsonCreator
    public static PackageCategory fromValue(String value) {
        if (value == null || value.isBlank()) {
            return PARCEL;
        }
        for (PackageCategory category : values()) {
            if (category.name().equalsIgnoreCase(value.trim())) {
                return category;
            }
        }
        throw new IllegalArgumentException("Unknown package category: " + value);
    }
}
