import 'package:flutter/material.dart';
import 'package:swiftroute_app/core/theme/app_colors.dart';
import 'package:swiftroute_app/features/order/data/models/order_model.dart';

/// Shift summary stats card showing counts and completion rate (D09)
class ShiftStatsCard extends StatelessWidget {
  const ShiftStatsCard({super.key, required this.orders});

  final List<OrderModel> orders;

  @override
  Widget build(BuildContext context) {
    final total = orders.length;
    final inTransit = orders.where((o) => o.status == OrderStatus.inTransit).length;
    final planned = orders.where((o) => o.status == OrderStatus.planned).length;
    final delivered = orders.where((o) => o.status == OrderStatus.delivered).length;
    final failed = orders.where((o) => o.status == OrderStatus.failed).length;

    final completionRate = total > 0 ? (delivered / total) : 0.0;
    // Estimated earnings: 15,000 VND base + 16,000 VND estimated 3.2km distance = 31,000 VND / order
    final estimatedEarnings = delivered * 31000;

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppColors.divider),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.03),
            blurRadius: 8,
            offset: const Offset(0, 3),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Text(
                'Tổng quan ca giao hôm nay',
                style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: AppColors.textPrimary),
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: AppColors.primary.withValues(alpha: 0.08),
                  borderRadius: BorderRadius.circular(6),
                ),
                child: Text(
                  '${(completionRate * 100).toInt()}% hoàn tất',
                  style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: AppColors.primary),
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          // Progress bar
          ClipRRect(
            borderRadius: BorderRadius.circular(4),
            child: LinearProgressIndicator(
              value: completionRate,
              minHeight: 6,
              backgroundColor: AppColors.surfaceVariant,
              valueColor: const AlwaysStoppedAnimation<Color>(AppColors.success),
            ),
          ),
          const SizedBox(height: 14),
          // 4 metric chips
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
          // Estimated shift earnings (Feature 2)
          const SizedBox(height: 12),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
            decoration: BoxDecoration(
              gradient: LinearGradient(
                colors: [
                  AppColors.primary.withValues(alpha: 0.05),
                  AppColors.success.withValues(alpha: 0.08),
                ],
              ),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: AppColors.success.withValues(alpha: 0.2)),
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Row(
                  children: [
                    Container(
                      padding: const EdgeInsets.all(6),
                      decoration: const BoxDecoration(
                        color: AppColors.success,
                        shape: BoxShape.circle,
                      ),
                      child: const Icon(Icons.payments_outlined, color: Colors.white, size: 16),
                    ),
                    const SizedBox(width: 10),
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text(
                          'Ước tính thù lao ca',
                          style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: AppColors.textPrimary),
                        ),
                        Text(
                          '$delivered đơn hoàn tất (15k/đơn + 5k/km)',
                          style: const TextStyle(fontSize: 10, color: AppColors.textSecondary),
                        ),
                      ],
                    ),
                  ],
                ),
                Text(
                  _formatCurrency(estimatedEarnings),
                  style: const TextStyle(
                    fontSize: 15,
                    fontWeight: FontWeight.bold,
                    color: AppColors.success,
                  ),
                ),
              ],
            ),
          ),
          if (failed > 0) ...[
            const SizedBox(height: 10),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
              decoration: BoxDecoration(
                color: AppColors.error.withValues(alpha: 0.08),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Row(
                children: [
                  const Icon(Icons.error_outline_rounded, size: 14, color: AppColors.error),
                  const SizedBox(width: 6),
                  Text(
                    'Có $failed đơn giao không thành công trong ca này',
                    style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w600, color: AppColors.error),
                  ),
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }

  String _formatCurrency(int amount) {
    if (amount <= 0) return '0 đ';
    final s = amount.toString();
    final buffer = StringBuffer();
    var count = 0;
    for (var i = s.length - 1; i >= 0; i--) {
      buffer.write(s[i]);
      count++;
      if (count % 3 == 0 && i != 0) {
        buffer.write('.');
      }
    }
    return '${buffer.toString().split('').reversed.join()} đ';
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
  Widget build(BuildContext context) {
    return Expanded(
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
              style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: color),
            ),
            const SizedBox(height: 2),
            Text(
              label,
              style: const TextStyle(fontSize: 10, color: AppColors.textSecondary),
              textAlign: TextAlign.center,
              maxLines: 1,
            ),
          ],
        ),
      ),
    );
  }
}

