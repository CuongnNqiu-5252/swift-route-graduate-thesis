import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/theme/app_colors.dart';
import 'package:swiftroute_app/features/driver/application/driver_provider.dart';
import 'package:swiftroute_app/features/driver/presentation/screens/driver_navigation_screen.dart';
import 'package:swiftroute_app/features/driver/presentation/widgets/driver_stop_detail_sheet.dart';
import 'package:swiftroute_app/features/order/data/models/order_model.dart';

/// Card representing a delivery stop along the driver's daily route (D05)
class DriverStopCard extends ConsumerStatefulWidget {
  const DriverStopCard({
    super.key,
    required this.stop,
    required this.sequence,
  });

  final OrderModel stop;
  final int sequence;

  @override
  ConsumerState<DriverStopCard> createState() => _DriverStopCardState();
}

class _DriverStopCardState extends ConsumerState<DriverStopCard> {
  var _isUpdating = false;

  Future<void> _advance() async {
    final nextStatus = widget.stop.status == OrderStatus.planned
        ? OrderStatus.inTransit
        : OrderStatus.delivered;

    setState(() => _isUpdating = true);
    try {
      await ref.read(driverOrdersProvider.notifier).updateOrderStatus(
            orderId: widget.stop.id,
            nextStatus: nextStatus,
          );
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              nextStatus == OrderStatus.inTransit
                  ? 'Bắt đầu giao đơn #${widget.stop.id.substring(0, 8)}'
                  : 'Đã hoàn thành điểm giao #${widget.stop.id.substring(0, 8)}',
            ),
            backgroundColor: AppColors.success,
          ),
        );
      }
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Không thể cập nhật đơn. Hãy thử lại.')),
        );
      }
    } finally {
      if (mounted) setState(() => _isUpdating = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final stop = widget.stop;
    final isInTransit = stop.status == OrderStatus.inTransit;
    final isDelivered = stop.status == OrderStatus.delivered;
    final isFailed = stop.status == OrderStatus.failed;

    final statusColor = switch (stop.status) {
      OrderStatus.planned => Colors.blue.shade700,
      OrderStatus.inTransit => AppColors.accent,
      OrderStatus.delivered => AppColors.success,
      OrderStatus.failed => AppColors.error,
      _ => AppColors.textSecondary,
    };

    final actionLabel = isInTransit ? 'Đã giao xong' : 'Bắt đầu giao';
    final actionIcon = isInTransit ? Icons.check_circle_rounded : Icons.two_wheeler_rounded;

    return InkWell(
      onTap: () => DriverStopDetailSheet.show(
        context,
        order: stop,
        sequenceNumber: widget.sequence,
      ),
      borderRadius: BorderRadius.circular(12),
      child: Container(
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
            color: isInTransit ? AppColors.accent.withValues(alpha: 0.5) : AppColors.divider,
            width: isInTransit ? 1.5 : 1.0,
          ),
          boxShadow: isInTransit
              ? [
                  BoxShadow(
                    color: AppColors.accent.withValues(alpha: 0.08),
                    blurRadius: 8,
                    offset: const Offset(0, 2),
                  ),
                ]
              : null,
        ),
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Row 1: Index + Order ID + Status
            Row(
              children: [
                Container(
                  width: 26,
                  height: 26,
                  alignment: Alignment.center,
                  decoration: BoxDecoration(
                    color: isDelivered
                        ? AppColors.success
                        : (isInTransit ? AppColors.accent : AppColors.primary),
                    shape: BoxShape.circle,
                  ),
                  child: isDelivered
                      ? const Icon(Icons.check, size: 14, color: Colors.white)
                      : Text(
                          '${widget.sequence}',
                          style: const TextStyle(
                            color: Colors.white,
                            fontWeight: FontWeight.bold,
                            fontSize: 12,
                          ),
                        ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    'Đơn #${stop.id.length > 8 ? stop.id.substring(0, 8) : stop.id}',
                    style: TextStyle(
                      fontWeight: FontWeight.bold,
                      fontSize: 14,
                      decoration: isDelivered ? TextDecoration.lineThrough : null,
                      color: isDelivered ? AppColors.textSecondary : AppColors.textPrimary,
                    ),
                  ),
                ),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(
                    color: statusColor.withValues(alpha: 0.1),
                    borderRadius: BorderRadius.circular(6),
                  ),
                  child: Text(
                    stop.status.displayName,
                    style: TextStyle(
                      fontSize: 11,
                      fontWeight: FontWeight.bold,
                      color: statusColor,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 10),
            // Address
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Icon(Icons.location_on_outlined, size: 16, color: AppColors.textSecondary),
                const SizedBox(width: 6),
                Expanded(
                  child: Text(
                    stop.deliveryAddress,
                    style: TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.w500,
                      color: isDelivered ? AppColors.textSecondary : AppColors.textPrimary,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            // Window & weight
            Row(
              children: [
                const Icon(Icons.access_time_rounded, size: 13, color: AppColors.textSecondary),
                const SizedBox(width: 4),
                Text(
                  '${_formatTime(stop.timeWindowStart)} - ${_formatTime(stop.timeWindowEnd)}',
                  style: const TextStyle(fontSize: 11, color: AppColors.textSecondary),
                ),
                const SizedBox(width: 12),
                const Icon(Icons.scale_rounded, size: 13, color: AppColors.textSecondary),
                const SizedBox(width: 4),
                Text(
                  '${stop.weightKg.toStringAsFixed(1)} kg',
                  style: const TextStyle(fontSize: 11, color: AppColors.textSecondary),
                ),
              ],
            ),
            // Actions (only if not completed/failed)
            if (!isDelivered && !isFailed) ...[
              const Divider(height: 20, color: AppColors.divider),
              Row(
                children: [
                  Expanded(
                    flex: 4,
                    child: OutlinedButton.icon(
                      onPressed: () => Navigator.of(context).push(
                        MaterialPageRoute(
                          builder: (_) => DriverNavigationScreen(order: stop),
                        ),
                      ),
                      icon: const Icon(Icons.directions_outlined, size: 16),
                      label: const Text('Bản đồ'),
                      style: OutlinedButton.styleFrom(
                        padding: const EdgeInsets.symmetric(vertical: 8),
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    flex: 6,
                    child: FilledButton.icon(
                      onPressed: _isUpdating ? null : _advance,
                      icon: _isUpdating
                          ? const SizedBox(
                              width: 14,
                              height: 14,
                              child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                            )
                          : Icon(actionIcon, size: 16),
                      label: Text(actionLabel, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13)),
                      style: FilledButton.styleFrom(
                        backgroundColor: isInTransit ? AppColors.success : AppColors.primary,
                        padding: const EdgeInsets.symmetric(vertical: 8),
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                      ),
                    ),
                  ),
                ],
              ),
            ],
          ],
        ),
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

