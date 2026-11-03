import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/theme/app_colors.dart';
import 'package:swiftroute_app/features/driver/presentation/screens/driver_navigation_screen.dart';
import 'package:swiftroute_app/features/driver/presentation/widgets/driver_incident_sheet.dart';
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
  Future<void> _advance() => DriverStopDetailSheet.show(context, order: widget.stop, sequenceNumber: widget.sequence);

  @override
  Widget build(BuildContext context) {
    final stop = widget.stop;
    final isInTransit = stop.deliveryTaskStatus == DeliveryTaskStatus.outForDelivery;
    final isDelivered = stop.deliveryTaskStatus == DeliveryTaskStatus.completed;
    final isFailed = stop.deliveryTaskStatus == DeliveryTaskStatus.failed;
    final isReturn = stop.deliveryTaskType == 'RETURN';

    final statusColor = isInTransit ? AppColors.accent : Colors.blue.shade700;

    const actionLabel = 'Mở thao tác';
    const actionIcon = Icons.arrow_forward_rounded;

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
                    '${isReturn ? 'Hoàn' : 'Đơn'} #${stop.id.length > 8 ? stop.id.substring(0, 8) : stop.id}',
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
                    (stop.deliveryTaskStatus ?? DeliveryTaskStatus.assigned).displayName,
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
                    isReturn ? stop.pickupAddress : stop.deliveryAddress,
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
                  '${stop.packageCount} kiện · ${stop.weightKg.toStringAsFixed(1)} kg',
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
                  OutlinedButton(
                    onPressed: () => DriverIncidentSheet.show(context, stop),
                    style: OutlinedButton.styleFrom(
                      padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 10),
                      side: const BorderSide(color: AppColors.error, width: 1.2),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                    ),
                    child: const Tooltip(
                      message: 'Báo sự cố đơn hàng',
                      child: Icon(Icons.report_problem_outlined, color: AppColors.error, size: 16),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    flex: 6,
                    child: FilledButton.icon(
                      onPressed: _advance,
                      icon: const Icon(actionIcon, size: 16),
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
