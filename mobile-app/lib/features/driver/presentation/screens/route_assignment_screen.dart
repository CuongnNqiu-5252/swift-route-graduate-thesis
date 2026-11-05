import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/theme/app_colors.dart';
import 'package:swiftroute_app/core/theme/app_theme.dart';
import 'package:swiftroute_app/features/driver/application/driver_provider.dart';
import 'package:swiftroute_app/features/driver/data/models/dispatch_plan_model.dart';
import 'package:swiftroute_app/features/order/data/models/order_model.dart';

/// Screen presenting a dispatched route assignment for driver acknowledgement
/// or decline (P1.8).
class RouteAssignmentScreen extends ConsumerStatefulWidget {
  const RouteAssignmentScreen({
    super.key,
    required this.plan,
    required this.orders,
  });

  final DispatchPlanModel plan;
  final List<OrderModel> orders;

  @override
  ConsumerState<RouteAssignmentScreen> createState() =>
      _RouteAssignmentScreenState();
}

class _RouteAssignmentScreenState extends ConsumerState<RouteAssignmentScreen> {
  var _isSubmitting = false;

  Future<void> _handleAcknowledge() async {
    setState(() => _isSubmitting = true);
    try {
      await ref
          .read(driverDispatchPlansProvider.notifier)
          .acknowledge(widget.plan.planId);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Đã nhận lộ trình. Hãy di chuyển tới điểm lấy hàng.'),
            backgroundColor: AppColors.success,
          ),
        );
        Navigator.of(context).pop(true);
      }
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Không thể xác nhận nhận tuyến. Hãy thử lại.'),
            backgroundColor: AppColors.error,
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _isSubmitting = false);
    }
  }

  Future<void> _handleDecline() async {
    final reasonController = TextEditingController();
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Từ chối lộ trình được giao'),
        content: TextField(
          controller: reasonController,
          decoration: const InputDecoration(
            labelText: 'Lý do từ chối',
            hintText: 'Xe gặp sự cố, vượt tải trọng, hết ca trực…',
          ),
          maxLines: 2,
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text('Quay lại'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: AppColors.error),
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: const Text('Xác nhận từ chối'),
          ),
        ],
      ),
    );
    final reason = reasonController.text.trim();
    reasonController.dispose();
    if (confirmed != true || !mounted) return;

    setState(() => _isSubmitting = true);
    try {
      await ref
          .read(driverDispatchPlansProvider.notifier)
          .decline(
            widget.plan.planId,
            reason.isEmpty ? 'Tài xế từ chối nhận tuyến trên ứng dụng' : reason,
          );
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
              'Đã từ chối lộ trình. Hệ thống sẽ điều phối lại cho tài xế khác.',
            ),
          ),
        );
        Navigator.of(context).pop(false);
      }
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Không thể từ chối lộ trình. Hãy thử lại.'),
            backgroundColor: AppColors.error,
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _isSubmitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final plan = widget.plan;
    final planOrders = plan.orderedStopIds
        .map((id) => widget.orders.where((o) => o.id == id).firstOrNull)
        .whereType<OrderModel>()
        .toList();
    final displayOrders = planOrders.isNotEmpty ? planOrders : widget.orders;

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: Text(
          'Lộ trình #${plan.planId.length > 8 ? plan.planId.substring(0, 8) : plan.planId}',
          style: const TextStyle(
            fontSize: 16,
            fontWeight: FontWeight.bold,
            color: AppColors.textPrimary,
          ),
        ),
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Container(
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
                      'Tóm tắt lộ trình',
                      style: TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w800,
                        color: AppColors.textPrimary,
                      ),
                    ),
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 8,
                        vertical: 3,
                      ),
                      decoration: BoxDecoration(
                        color: plan.isAcknowledged
                            ? AppColors.success.withValues(alpha: 0.12)
                            : AppColors.warningSoft,
                        borderRadius: BorderRadius.circular(6),
                      ),
                      child: Text(
                        plan.isAcknowledged ? 'Đã nhận tuyến' : 'Chờ xác nhận',
                        style: TextStyle(
                          fontSize: 11,
                          fontWeight: FontWeight.bold,
                          color: plan.isAcknowledged
                              ? AppColors.success
                              : AppColors.warning,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 14),
                Row(
                  children: [
                    Expanded(
                      child: _MetricColumn(
                        label: 'Số điểm giao',
                        value: '${plan.orderCount} điểm',
                      ),
                    ),
                    Expanded(
                      child: _MetricColumn(
                        label: 'Tổng quãng đường',
                        value: '${plan.totalDistanceKm.toStringAsFixed(1)} km',
                      ),
                    ),
                    Expanded(
                      child: _MetricColumn(
                        label: 'Thời gian dự kiến',
                        value: '${plan.estimatedDurationMinutes} phút',
                      ),
                    ),
                  ],
                ),
                if (plan.ackDeadlineAt != null && !plan.isAcknowledged) ...[
                  const Divider(height: 24, color: AppColors.divider),
                  Row(
                    children: [
                      Icon(
                        Icons.schedule_rounded,
                        size: 16,
                        color: AppColors.warning,
                      ),
                      const SizedBox(width: 6),
                      Expanded(
                        child: Text(
                          'Hạn xác nhận nhận tuyến: ${_formatTime(plan.ackDeadlineAt!)}',
                          style: TextStyle(
                            fontSize: 12,
                            fontWeight: FontWeight.w600,
                            color: AppColors.warning,
                          ),
                        ),
                      ),
                    ],
                  ),
                ],
              ],
            ),
          ),
          const SizedBox(height: 16),
          const Text(
            'Thứ tự điểm giao',
            style: TextStyle(
              fontSize: 16,
              fontWeight: FontWeight.w800,
              color: AppColors.textPrimary,
            ),
          ),
          const SizedBox(height: 8),
          if (displayOrders.isEmpty)
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: AppColors.card,
                borderRadius: BorderRadius.circular(14),
                border: Border.all(color: AppColors.divider),
              ),
              child: Text(
                'Lộ trình gồm ${plan.orderCount} đơn hàng. Danh sách chi tiết sẽ đồng bộ ngay sau khi bạn nhận tuyến.',
                style: const TextStyle(
                  fontSize: 13,
                  color: AppColors.textSecondary,
                ),
              ),
            )
          else
            ...displayOrders.asMap().entries.map((entry) {
              final idx = entry.key + 1;
              final order = entry.value;
              return Container(
                margin: const EdgeInsets.only(bottom: 10),
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: AppColors.card,
                  borderRadius: BorderRadius.circular(14),
                  border: Border.all(color: AppColors.divider),
                ),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Container(
                      width: 26,
                      height: 26,
                      alignment: Alignment.center,
                      decoration: const BoxDecoration(
                        color: AppColors.primary,
                        shape: BoxShape.circle,
                      ),
                      child: Text(
                        '$idx',
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 12,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            order.deliveryAddress,
                            style: const TextStyle(
                              fontSize: 13,
                              fontWeight: FontWeight.w600,
                              color: AppColors.textPrimary,
                            ),
                          ),
                          const SizedBox(height: 4),
                          Text(
                            'Lấy tại: ${order.pickupAddress} · ${order.weightKg.toStringAsFixed(1)} kg',
                            style: const TextStyle(
                              fontSize: 11,
                              color: AppColors.textSecondary,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              );
            }),
        ],
      ),
      bottomNavigationBar: plan.isAcknowledged
          ? null
          : SafeArea(
              child: Container(
                padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
                decoration: const BoxDecoration(
                  color: Colors.white,
                  border: Border(top: BorderSide(color: AppColors.divider)),
                ),
                child: Row(
                  children: [
                    Expanded(
                      flex: 4,
                      child: OutlinedButton(
                        onPressed: _isSubmitting ? null : _handleDecline,
                        style: OutlinedButton.styleFrom(
                          foregroundColor: AppColors.error,
                          side: const BorderSide(color: AppColors.error),
                          padding: const EdgeInsets.symmetric(vertical: 14),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(10),
                          ),
                        ),
                        child: const Text(
                          'Từ chối',
                          style: TextStyle(fontWeight: FontWeight.bold),
                        ),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      flex: 6,
                      child: FilledButton.icon(
                        onPressed: _isSubmitting ? null : _handleAcknowledge,
                        icon: _isSubmitting
                            ? const SizedBox(
                                width: 16,
                                height: 16,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                  color: Colors.white,
                                ),
                              )
                            : const Icon(Icons.check_circle_outline_rounded),
                        label: const Text(
                          'Nhận lộ trình',
                          style: TextStyle(fontWeight: FontWeight.bold),
                        ),
                        style: FilledButton.styleFrom(
                          backgroundColor: AppColors.primary,
                          padding: const EdgeInsets.symmetric(vertical: 14),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(10),
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
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

class _MetricColumn extends StatelessWidget {
  const _MetricColumn({required this.label, required this.value});
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label,
          style: const TextStyle(fontSize: 11, color: AppColors.textSecondary),
        ),
        const SizedBox(height: 4),
        Text(
          value,
          style: AppTheme.numStyle(fontSize: 15, fontWeight: FontWeight.w800),
        ),
      ],
    );
  }
}
