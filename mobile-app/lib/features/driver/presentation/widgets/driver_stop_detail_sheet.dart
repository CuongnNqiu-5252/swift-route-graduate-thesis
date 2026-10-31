import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/theme/app_colors.dart';
import 'package:swiftroute_app/features/driver/application/driver_provider.dart';
import 'package:swiftroute_app/features/driver/presentation/screens/driver_navigation_screen.dart';
import 'package:swiftroute_app/features/driver/presentation/widgets/incident_report_dialog.dart';
import 'package:swiftroute_app/features/order/data/models/order_model.dart';

/// Modal bottom sheet displaying complete details and actions for a stop (D06)
class DriverStopDetailSheet extends ConsumerStatefulWidget {
  const DriverStopDetailSheet({
    super.key,
    required this.order,
    required this.sequenceNumber,
  });

  final OrderModel order;
  final int sequenceNumber;

  static Future<void> show(
    BuildContext context, {
    required OrderModel order,
    required int sequenceNumber,
  }) {
    return showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => DriverStopDetailSheet(
        order: order,
        sequenceNumber: sequenceNumber,
      ),
    );
  }

  @override
  ConsumerState<DriverStopDetailSheet> createState() => _DriverStopDetailSheetState();
}

class _DriverStopDetailSheetState extends ConsumerState<DriverStopDetailSheet> {
  var _isUpdating = false;

  Future<void> _updateStatus(OrderStatus nextStatus) async {
    setState(() => _isUpdating = true);
    try {
      await ref.read(driverOrdersProvider.notifier).updateOrderStatus(
            orderId: widget.order.id,
            nextStatus: nextStatus,
          );
      if (mounted) {
        Navigator.of(context).pop();
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Đơn #${widget.order.id.substring(0, 8)} đã chuyển thành: ${nextStatus.displayName}'),
            backgroundColor: nextStatus == OrderStatus.failed ? AppColors.error : AppColors.success,
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

  Future<void> _handleIncident() async {
    final result = await IncidentReportDialog.show(
      context,
      orderId: widget.order.id,
      deliveryAddress: widget.order.deliveryAddress,
    );

    if (result != null && mounted) {
      await _updateStatus(OrderStatus.failed);
    }
  }

  @override
  Widget build(BuildContext context) {
    final order = widget.order;
    final statusColor = switch (order.status) {
      OrderStatus.planned => Colors.blue.shade700,
      OrderStatus.inTransit => AppColors.accent,
      OrderStatus.delivered => AppColors.success,
      OrderStatus.failed => AppColors.error,
      _ => AppColors.textSecondary,
    };

    return Container(
      decoration: const BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      padding: const EdgeInsets.fromLTRB(20, 12, 20, 28),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Drag handle
          Center(
            child: Container(
              width: 40,
              height: 4,
              decoration: BoxDecoration(
                color: AppColors.divider,
                borderRadius: BorderRadius.circular(2),
              ),
            ),
          ),
          const SizedBox(height: 16),
          // Header: Sequence, ID, Status
          Row(
            children: [
              Container(
                width: 32,
                height: 32,
                alignment: Alignment.center,
                decoration: const BoxDecoration(
                  color: AppColors.primary,
                  shape: BoxShape.circle,
                ),
                child: Text(
                  '${widget.sequenceNumber}',
                  style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 15),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Đơn hàng #${order.id.length > 8 ? order.id.substring(0, 8) : order.id}',
                      style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                    ),
                    Text(
                      order.tenantName ?? 'Kho vận SwiftRoute Hub',
                      style: const TextStyle(fontSize: 12, color: AppColors.textSecondary),
                    ),
                  ],
                ),
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(
                  color: statusColor.withValues(alpha: 0.1),
                  borderRadius: BorderRadius.circular(6),
                ),
                child: Text(
                  order.status.displayName,
                  style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: statusColor),
                ),
              ),
            ],
          ),
          const Divider(height: 24, color: AppColors.divider),
          // Delivery details
          const Text('ĐIỂM GIAO HÀNG', style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: AppColors.textSecondary)),
          const SizedBox(height: 4),
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Icon(Icons.location_on_rounded, color: AppColors.primary, size: 20),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  order.deliveryAddress,
                  style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          // Time window and weight
          Row(
            children: [
              Expanded(
                child: _InfoTile(
                  title: 'Khung giờ giao',
                  value: '${_formatTime(order.timeWindowStart)} - ${_formatTime(order.timeWindowEnd)}',
                  icon: Icons.access_time_rounded,
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: _InfoTile(
                  title: 'Trọng lượng gói',
                  value: '${order.weightKg.toStringAsFixed(1)} kg',
                  icon: Icons.scale_rounded,
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          // Customer contact simulation
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: AppColors.surface,
              borderRadius: BorderRadius.circular(10),
            ),
            child: Row(
              children: [
                const CircleAvatar(
                  radius: 18,
                  backgroundColor: AppColors.surfaceVariant,
                  child: Icon(Icons.person, color: AppColors.textPrimary, size: 20),
                ),
                const SizedBox(width: 12),
                const Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Khách nhận hàng', style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold)),
                      Text('0909 ••• ••• (Được bảo mật)', style: TextStyle(fontSize: 11, color: AppColors.textSecondary)),
                    ],
                  ),
                ),
                IconButton(
                  onPressed: () {
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(content: Text('Đang kết nối cuộc gọi che số với khách hàng...')),
                    );
                  },
                  icon: const Icon(Icons.phone_rounded, color: AppColors.primary),
                  tooltip: 'Gọi cho khách',
                ),
              ],
            ),
          ),
          const SizedBox(height: 20),
          // Action buttons
          Row(
            children: [
              // Navigation map
              Expanded(
                flex: 4,
                child: OutlinedButton.icon(
                  onPressed: () {
                    Navigator.of(context).pop();
                    Navigator.of(context).push(
                      MaterialPageRoute(
                        builder: (_) => DriverNavigationScreen(order: order),
                      ),
                    );
                  },
                  icon: const Icon(Icons.navigation_rounded, size: 18),
                  label: const Text('Bản đồ'),
                  style: OutlinedButton.styleFrom(
                    padding: const EdgeInsets.symmetric(vertical: 12),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                  ),
                ),
              ),
              const SizedBox(width: 10),
              // State transition button
              if (order.status == OrderStatus.planned)
                Expanded(
                  flex: 6,
                  child: FilledButton.icon(
                    onPressed: _isUpdating ? null : () => _updateStatus(OrderStatus.inTransit),
                    icon: _isUpdating
                        ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                        : const Icon(Icons.two_wheeler_rounded, size: 18),
                    label: const Text('Bắt đầu giao', style: TextStyle(fontWeight: FontWeight.bold)),
                    style: FilledButton.styleFrom(
                      backgroundColor: AppColors.primary,
                      padding: const EdgeInsets.symmetric(vertical: 12),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                    ),
                  ),
                ),
              if (order.status == OrderStatus.inTransit) ...[
                Expanded(
                  flex: 6,
                  child: FilledButton.icon(
                    onPressed: _isUpdating ? null : () => _updateStatus(OrderStatus.delivered),
                    icon: _isUpdating
                        ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                        : const Icon(Icons.check_circle_rounded, size: 18),
                    label: const Text('Đã giao xong', style: TextStyle(fontWeight: FontWeight.bold)),
                    style: FilledButton.styleFrom(
                      backgroundColor: AppColors.success,
                      padding: const EdgeInsets.symmetric(vertical: 12),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                    ),
                  ),
                ),
              ],
            ],
          ),
          if (order.status == OrderStatus.inTransit) ...[
            const SizedBox(height: 8),
            SizedBox(
              width: double.infinity,
              child: TextButton.icon(
                onPressed: _isUpdating ? null : _handleIncident,
                icon: const Icon(Icons.report_problem_rounded, color: AppColors.error, size: 16),
                label: const Text('Báo sự cố / Giao thất bại', style: TextStyle(color: AppColors.error, fontWeight: FontWeight.w600)),
              ),
            ),
          ],
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

class _InfoTile extends StatelessWidget {
  const _InfoTile({required this.title, required this.value, required this.icon});
  final String title;
  final String value;
  final IconData icon;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(icon, size: 14, color: AppColors.textSecondary),
              const SizedBox(width: 4),
              Text(title, style: const TextStyle(fontSize: 11, color: AppColors.textSecondary)),
            ],
          ),
          const SizedBox(height: 4),
          Text(value, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.bold)),
        ],
      ),
    );
  }
}

