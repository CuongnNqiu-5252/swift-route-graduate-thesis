import 'package:flutter/material.dart';
import 'package:swiftroute_app/core/theme/app_colors.dart';
import 'package:swiftroute_app/features/order/data/models/order_model.dart';

class ShiftStatsCard extends StatelessWidget {
  const ShiftStatsCard({super.key, required this.orders});

  final List<OrderModel> orders;

  @override
  Widget build(BuildContext context) {
    final total = orders.length;
    final inTransit = orders
        .where((o) => o.status == OrderStatus.inTransit)
        .length;
    final planned = orders.where((o) => o.status == OrderStatus.planned).length;
    final delivered = orders
        .where((o) => o.status == OrderStatus.delivered)
        .length;
    final failed = orders.where((o) => o.status == OrderStatus.failed).length;
    final completionRate = total > 0 ? delivered / total : 0.0;

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppColors.divider),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Text(
                'Tổng quan ca giao hôm nay',
                style: TextStyle(fontSize: 14, fontWeight: FontWeight.w800),
              ),
              Text(
                '${(completionRate * 100).round()}% hoàn tất',
                style: const TextStyle(
                  fontSize: 11,
                  fontWeight: FontWeight.w700,
                  color: AppColors.primary,
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          ClipRRect(
            borderRadius: BorderRadius.circular(4),
            child: LinearProgressIndicator(
              value: completionRate,
              minHeight: 6,
              backgroundColor: AppColors.surfaceVariant,
              valueColor: const AlwaysStoppedAnimation<Color>(
                AppColors.success,
              ),
            ),
          ),
          const SizedBox(height: 14),
          Row(
            children: [
              _MetricItem(
                label: 'Tổng đơn',
                value: '$total',
                icon: Icons.inventory_2_outlined,
                color: AppColors.primary,
              ),
              const SizedBox(width: 8),
              _MetricItem(
                label: 'Đang giao',
                value: '$inTransit',
                icon: Icons.delivery_dining_rounded,
                color: AppColors.accent,
              ),
              const SizedBox(width: 8),
              _MetricItem(
                label: 'Chờ giao',
                value: '$planned',
                icon: Icons.schedule_rounded,
                color: Colors.blue.shade700,
              ),
              const SizedBox(width: 8),
              _MetricItem(
                label: 'Đã giao',
                value: '$delivered',
                icon: Icons.task_alt_rounded,
                color: AppColors.success,
              ),
            ],
          ),
          if (failed > 0) ...[
            const SizedBox(height: 10),
            Row(
              children: [
                const Icon(
                  Icons.error_outline_rounded,
                  size: 15,
                  color: AppColors.error,
                ),
                const SizedBox(width: 6),
                Text(
                  '$failed đơn giao không thành công',
                  style: const TextStyle(
                    fontSize: 11,
                    fontWeight: FontWeight.w700,
                    color: AppColors.error,
                  ),
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }
}

class _MetricItem extends StatelessWidget {
  const _MetricItem({
    required this.label,
    required this.value,
    required this.icon,
    required this.color,
  });
  final String label;
  final String value;
  final IconData icon;
  final Color color;

  @override
  Widget build(BuildContext context) => Expanded(
    child: Container(
      padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 4),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Column(
        children: [
          Icon(icon, size: 16, color: color),
          const SizedBox(height: 4),
          Text(
            value,
            style: TextStyle(
              fontSize: 16,
              fontWeight: FontWeight.w800,
              color: color,
            ),
          ),
          const SizedBox(height: 2),
          Text(
            label,
            maxLines: 1,
            style: const TextStyle(
              fontSize: 10,
              color: AppColors.textSecondary,
            ),
          ),
        ],
      ),
    ),
  );
}
