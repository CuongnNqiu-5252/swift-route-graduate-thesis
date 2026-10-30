// features/order/domain/enums/package_category.dart

import 'package:flutter/material.dart';

/// Presentation model and enum for delivery package categories in Flutter.
/// Encapsulates UI icons, colors, display labels, and client-side surcharges.
enum PackageCategory {
  food(
    code: 'FOOD',
    label: 'Đồ ăn',
    fullLabel: 'Đồ ăn / Đồ uống',
    icon: Icons.restaurant,
    color: Color(0xFFF59E0B),
    maxWeightKg: 10.0,
    surchargeVnd: 0,
    hintText: 'Ví dụ: 2 cốc trà sữa, cơm trưa...',
  ),
  document(
    code: 'DOCUMENT',
    label: 'Tài liệu',
    fullLabel: 'Tài liệu / Hợp đồng',
    icon: Icons.description_outlined,
    color: Color(0xFF3B82F6),
    maxWeightKg: 3.0,
    surchargeVnd: 0,
    hintText: 'Ví dụ: Hồ sơ xin việc, hợp đồng A4...',
  ),
  parcel(
    code: 'PARCEL',
    label: 'Tiêu dùng',
    fullLabel: 'Hàng tiêu dùng',
    icon: Icons.inventory_2_outlined,
    color: Color(0xFF10B981),
    maxWeightKg: 30.0,
    surchargeVnd: 0,
    hintText: 'Ví dụ: Quần áo, mỹ phẩm, đồ gia dụng...',
  ),
  medical(
    code: 'MEDICAL',
    label: 'Y tế',
    fullLabel: 'Thuốc / Y tế',
    icon: Icons.medication_outlined,
    color: Color(0xFFEF4444),
    maxWeightKg: 5.0,
    surchargeVnd: 0,
    hintText: 'Ví dụ: Thuốc cảm, vật tư y tế...',
  ),
  fragile(
    code: 'FRAGILE',
    label: 'Dễ vỡ',
    fullLabel: 'Hàng dễ vỡ',
    icon: Icons.warning_amber_rounded,
    color: Color(0xFF8B5CF6),
    maxWeightKg: 15.0,
    surchargeVnd: 5000,
    hintText: 'Ví dụ: Đồ gốm sứ, kính, đồ điện tử...',
  );

  final String code;
  final String label;
  final String fullLabel;
  final IconData icon;
  final Color color;
  final double maxWeightKg;
  final int surchargeVnd;
  final String hintText;

  const PackageCategory({
    required this.code,
    required this.label,
    required this.fullLabel,
    required this.icon,
    required this.color,
    required this.maxWeightKg,
    required this.surchargeVnd,
    required this.hintText,
  });

  static PackageCategory fromCode(String? code) {
    if (code == null) return PackageCategory.parcel;
    return PackageCategory.values.firstWhere(
      (cat) => cat.code.toUpperCase() == code.toUpperCase(),
      orElse: () => PackageCategory.parcel,
    );
  }
}
