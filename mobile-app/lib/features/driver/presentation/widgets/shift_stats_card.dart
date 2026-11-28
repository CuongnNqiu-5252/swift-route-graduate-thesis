// features/driver/presentation/widgets/shift_stats_card.dart

import 'package:flutter/material.dart';
import 'package:swiftroute_app/core/theme/app_colors.dart';
import 'package:swiftroute_app/core/theme/app_theme.dart';
import 'package:swiftroute_app/core/utils/formatters.dart';
import 'package:swiftroute_app/features/order/data/models/order_model.dart';

/// Operational shift progress strip with tabular numerals and remaining COD visibility.
class ShiftStatsCard extends StatelessWidget {
  const ShiftStatsCard({
    super.key,
    required this.orders,
    this.pendingCodTotal = 0,
  });

  final List<OrderModel> orders;
  final double pendingCodTotal;

  @override
  Widget build(BuildContext context) {
    final total = orders.length;
    final completed = orders
        .where(
          (o) =>
              o.status == OrderStatus.delivered ||
              o.deliveryTaskStatus == DeliveryTaskStatus.completed,
        )
        .length;
    final remaining = orders
        .where(
          (o) =>
              o.deliveryTaskStatus != DeliveryTaskStatus.completed &&
              o.deliveryTaskStatus != DeliveryTaskStatus.cancelled &&
              o.deliveryTaskStatus != DeliveryTaskStatus.failed,
        )
        .length;
    final progress = total > 0 ? completed / total : 0.0;

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.card,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: AppColors.divider),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Text(
                'Tiến độ ca hôm nay',
                style: TextStyle(
                  fontSize: 15,
                  fontWeight: FontWeight.w800,
                  color: AppColors.textPrimary,
                ),
              ),
              Text(
                '$completed/$total điểm hoàn tất',
                style: AppTheme.numStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w700,
                  color: AppColors.primary,
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          ClipRRect(
            borderRadius: BorderRadius.circular(6),
            child: LinearProgressIndicator(
              value: progress,
              minHeight: 7,
              backgroundColor: AppColors.surface,
              valueColor: const AlwaysStoppedAnimation<Color>(
                AppColors.success,
              ),
            ),
          ),
          const SizedBox(height: 14),
          Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      'Còn lại trên xe',
                      style: TextStyle(
                        fontSize: 12,
                        color: AppColors.textSecondary,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      '$remaining điểm dừng',
                      style: AppTheme.numStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  ],
                ),
              ),
              Container(width: 1, height: 32, color: AppColors.divider),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      'Tiền mặt COD đang giữ',
                      style: TextStyle(
                        fontSize: 12,
                        color: AppColors.textSecondary,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      AppFormatters.currency(pendingCodTotal),
                      style: AppTheme.numStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w800,
                        color: pendingCodTotal > 0
                            ? AppColors.warning
                            : AppColors.textPrimary,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
