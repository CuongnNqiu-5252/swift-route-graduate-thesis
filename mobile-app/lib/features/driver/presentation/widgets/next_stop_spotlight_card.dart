import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/theme/app_colors.dart';
import 'package:swiftroute_app/features/driver/application/driver_provider.dart';
import 'package:swiftroute_app/features/driver/presentation/screens/driver_navigation_screen.dart';
import 'package:swiftroute_app/features/order/data/models/order_model.dart';

/// Spotlight card featuring the driver's current or immediate next stop (D02 / D06)
class NextStopSpotlightCard extends ConsumerStatefulWidget {
  const NextStopSpotlightCard({super.key, required this.orders});

  final List<OrderModel> orders;

  @override
  ConsumerState<NextStopSpotlightCard> createState() => _NextStopSpotlightCardState();
}

class _NextStopSpotlightCardState extends ConsumerState<NextStopSpotlightCard> {
  var _isUpdating = false;

  Future<void> _advanceOrder(OrderModel order) async {
    final nextStatus = order.status == OrderStatus.planned
        ? OrderStatus.inTransit
        : OrderStatus.delivered;

    setState(() => _isUpdating = true);
    try {
      await ref.read(driverOrdersProvider.notifier).updateOrderStatus(
            orderId: order.id,
            nextStatus: nextStatus,
          );
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              nextStatus == OrderStatus.inTransit
                  ? 'Bắt đầu di chuyển tới điểm giao #${order.id.substring(0, 8)}'
                  : 'Đã hoàn thành giao đơn #${order.id.substring(0, 8)}',
            ),
            backgroundColor: AppColors.success,
          ),
        );
      }
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Không thể cập nhật trạng thái đơn. Vui lòng thử lại.'),
            backgroundColor: AppColors.error,
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _isUpdating = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    // 1. Prioritize active IN_TRANSIT order
    final inTransit = widget.orders.where((o) => o.status == OrderStatus.inTransit).firstOrNull;
    // 2. Otherwise next PLANNED order
    final nextPlanned = widget.orders.where((o) => o.status == OrderStatus.planned).firstOrNull;

    final targetOrder = inTransit ?? nextPlanned;

    if (targetOrder == null) {
      final totalCompleted = widget.orders.where((o) => o.status == OrderStatus.delivered).length;
      return Container(
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: AppColors.divider),
        ),
        child: Column(
          children: [
            const Icon(Icons.check_circle_outline_rounded, size: 48, color: AppColors.success),
            const SizedBox(height: 12),
            Text(
              totalCompleted > 0
                  ? 'Chúc mừng! Bạn đã hoàn thành toàn bộ tuyến giao hôm nay ($totalCompleted đơn).'
                  : 'Hiện tại chưa có điểm giao nào được phân công.',
              textAlign: TextAlign.center,
              style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: AppColors.textPrimary),
            ),
            const SizedBox(height: 4),
            const Text(
              'Hệ thống điều phối sẽ tự động cập nhật khi có đơn mới.',
              textAlign: TextAlign.center,
              style: TextStyle(fontSize: 12, color: AppColors.textSecondary),
            ),
          ],
        ),
      );
    }

    final isInTransit = targetOrder.status == OrderStatus.inTransit;
    final badgeColor = isInTransit ? AppColors.accent : AppColors.primary;
    final badgeText = isInTransit ? 'ĐANG GIAO ĐƠN NÀY' : 'ĐIỂM GIAO KẾ TIẾP';
    final actionLabel = isInTransit ? 'Xác nhận Đã giao' : 'Bắt đầu giao đơn';
    final actionIcon = isInTransit ? Icons.check_circle_rounded : Icons.two_wheeler_rounded;

    return Container(
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: badgeColor.withValues(alpha: 0.3), width: 1.5),
        boxShadow: [
          BoxShadow(
            color: badgeColor.withValues(alpha: 0.08),
            blurRadius: 12,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Spotlight Top Banner
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
            decoration: BoxDecoration(
              color: badgeColor.withValues(alpha: 0.08),
              borderRadius: const BorderRadius.vertical(top: Radius.circular(15)),
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Row(
                  children: [
                    Icon(
                      isInTransit ? Icons.directions_bike_rounded : Icons.next_plan_rounded,
                      size: 16,
                      color: badgeColor,
                    ),
                    const SizedBox(width: 6),
                    Text(
                      badgeText,
                      style: TextStyle(
                        fontSize: 12,
                        fontWeight: FontWeight.bold,
                        color: badgeColor,
                        letterSpacing: 0.5,
                      ),
                    ),
                  ],
                ),
                Text(
                  '#${targetOrder.id.length > 8 ? targetOrder.id.substring(0, 8) : targetOrder.id}',
                  style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: AppColors.textSecondary),
                ),
              ],
            ),
          ),
          // Stop details
          Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Container(
                      padding: const EdgeInsets.all(8),
                      decoration: BoxDecoration(
                        color: AppColors.surface,
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: const Icon(Icons.location_on_rounded, color: AppColors.primary, size: 20),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Text('Địa chỉ giao hàng', style: TextStyle(fontSize: 11, color: AppColors.textSecondary)),
                          const SizedBox(height: 2),
                          Text(
                            targetOrder.deliveryAddress,
                            style: const TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: AppColors.textPrimary),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                // Metadata chips: time window and weight
                Row(
                  children: [
                    _InfoChip(
                      icon: Icons.access_time_rounded,
                      text: '${_formatTime(targetOrder.timeWindowStart)} - ${_formatTime(targetOrder.timeWindowEnd)}',
                    ),
                    const SizedBox(width: 8),
                    _InfoChip(
                      icon: Icons.scale_rounded,
                      text: '${targetOrder.weightKg.toStringAsFixed(1)} kg',
                    ),
                    const SizedBox(width: 8),
                    _InfoChip(
                      icon: Icons.flag_rounded,
                      text: targetOrder.priority,
                    ),
                  ],
                ),
                const SizedBox(height: 16),
                // Actions
                Row(
                  children: [
                    // Navigation Map button
                    Expanded(
                      flex: 4,
                      child: OutlinedButton.icon(
                        onPressed: () => Navigator.of(context).push(
                          MaterialPageRoute(
                            builder: (_) => DriverNavigationScreen(order: targetOrder),
                          ),
                        ),
                        icon: const Icon(Icons.map_rounded, size: 16),
                        label: const Text('Bản đồ'),
                        style: OutlinedButton.styleFrom(
                          padding: const EdgeInsets.symmetric(vertical: 12),
                          side: const BorderSide(color: AppColors.divider),
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                        ),
                      ),
                    ),
                    const SizedBox(width: 10),
                    // Advance status button
                    Expanded(
                      flex: 6,
                      child: FilledButton.icon(
                        onPressed: _isUpdating ? null : () => _advanceOrder(targetOrder),
                        icon: _isUpdating
                            ? const SizedBox(
                                width: 14,
                                height: 14,
                                child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                              )
                            : Icon(actionIcon, size: 16),
                        label: Text(actionLabel, style: const TextStyle(fontWeight: FontWeight.bold)),
                        style: FilledButton.styleFrom(
                          backgroundColor: badgeColor,
                          padding: const EdgeInsets.symmetric(vertical: 12),
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                        ),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  String _formatTime(DateTime dt) {
    final local = dt.toLocal();
    final h = local.hour.toString().padLeft(2, '0');
    final m = local.minute.toString().padLeft(2, '0');
    return '$h:$m';
  }
}

class _InfoChip extends StatelessWidget {
  const _InfoChip({required this.icon, required this.text});
  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(6),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 12, color: AppColors.textSecondary),
          const SizedBox(width: 4),
          Text(text, style: const TextStyle(fontSize: 11, color: AppColors.textSecondary, fontWeight: FontWeight.w500)),
        ],
      ),
    );
  }
}

