// features/driver/presentation/widgets/driver_stop_detail_sheet.dart

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/theme/app_colors.dart';
import 'package:swiftroute_app/core/theme/app_theme.dart';
import 'package:swiftroute_app/core/utils/formatters.dart';
import 'package:swiftroute_app/core/widgets/field_ops_widgets.dart';
import 'package:swiftroute_app/core/widgets/slide_to_confirm_button.dart';
import 'package:swiftroute_app/features/driver/application/driver_provider.dart';
import 'package:swiftroute_app/features/driver/presentation/screens/driver_navigation_screen.dart';
import 'package:swiftroute_app/features/driver/presentation/widgets/driver_delivery_pod_sheet.dart';
import 'package:swiftroute_app/features/driver/presentation/widgets/driver_incident_sheet.dart';
import 'package:swiftroute_app/features/order/data/models/order_model.dart';

/// Waybill detail sheet for a stop (D06).
///
/// Exposes one clear primary action per phase:
/// - customer delivery stop → "Giao xong" opening the POD/COD sheet
/// - return task → confirm handover back at the warehouse
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
      builder: (_) =>
          DriverStopDetailSheet(order: order, sequenceNumber: sequenceNumber),
    );
  }

  @override
  ConsumerState<DriverStopDetailSheet> createState() =>
      _DriverStopDetailSheetState();
}

class _DriverStopDetailSheetState extends ConsumerState<DriverStopDetailSheet> {
  OrderModel get _order => widget.order;

  void _showSnack(String message, {Color? color}) {
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text(message), backgroundColor: color));
  }

  Future<void> _completeDelivery() async {
    final done = await DriverDeliveryPodSheet.show(context, _order);
    if (!done || !mounted) return;
    Navigator.of(context).pop();
    _showSnack('Đã giao xong ${_order.displayCode}.', color: AppColors.success);
  }

  Future<void> _advanceReturn() async {
    final taskId = _order.deliveryTaskId;
    if (taskId == null || taskId.isEmpty) {
      _showSnack('Nhiệm vụ chưa có mã thực thi. Hãy tải lại lộ trình.');
      return;
    }
    try {
      await ref
          .read(driverOrdersProvider.notifier)
          .updateDeliveryTask(
            orderId: _order.id,
            taskId: taskId,
            nextStatus: DeliveryTaskStatus.completed,
            notes: 'Đã bàn giao kiện hoàn cho kho',
          );
      if (mounted) Navigator.of(context).pop();
    } catch (e) {
      if (mounted) {
        _showSnack(
          'Không cập nhật được đơn hoàn: ${AppFormatters.errorMessage(e)}',
          color: AppColors.error,
        );
      }
    }
  }

  Future<void> _reportIncident() async {
    await DriverIncidentSheet.show(context, _order);
    if (mounted) Navigator.of(context).pop();
  }

  void _openMap() {
    Navigator.of(context).pop();
    Navigator.of(context).push(
      MaterialPageRoute(builder: (_) => DriverNavigationScreen(order: _order)),
    );
  }

  @override
  Widget build(BuildContext context) {
    final order = _order;
    final status = order.deliveryTaskStatus ?? DeliveryTaskStatus.outForDelivery;
    final isClosed =
        status == DeliveryTaskStatus.completed ||
        status == DeliveryTaskStatus.failed ||
        status == DeliveryTaskStatus.cancelled;
    final isReturn = order.isReturnTask;
    final bottom = MediaQuery.paddingOf(context).bottom;

    final placeTitle = isReturn
        ? 'Hoàn về ${order.pickupDisplayName}'
        : (order.recipientName?.trim().isNotEmpty == true
              ? order.recipientName!.trim()
              : 'Khách nhận hàng');
    final placeAddress = isReturn
        ? order.pickupAddress
        : order.deliveryAddress;
    final contactPhone = isReturn
        ? order.senderPhone
        : order.recipientPhone;

    return ConstrainedBox(
      constraints: BoxConstraints(
        maxHeight: MediaQuery.sizeOf(context).height * 0.9,
      ),
      child: SingleChildScrollView(
        padding: EdgeInsets.fromLTRB(20, 12, 20, 20 + bottom),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Center(
              child: Container(
                width: 40,
                height: 4,
                decoration: BoxDecoration(
                  color: AppColors.surfaceVariant,
                  borderRadius: BorderRadius.circular(99),
                ),
              ),
            ),
            const SizedBox(height: 16),
            Row(
              children: [
                Container(
                  width: 34,
                  height: 34,
                  alignment: Alignment.center,
                  decoration: BoxDecoration(
                    color: AppColors.primary,
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Text(
                    '${widget.sequenceNumber}',
                    style: AppTheme.numStyle(
                      fontSize: 15,
                      fontWeight: FontWeight.w800,
                      color: Colors.white,
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    order.displayCode,
                    style: AppTheme.numStyle(
                      fontSize: 17,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ),
                StatusPill(
                  label: status.displayName,
                  foreground: AppColors.primaryDark,
                  background: AppColors.primarySoft,
                  borderColor: AppColors.primaryBorder,
                ),
              ],
            ),
            const SizedBox(height: 18),
            Text(
              placeTitle,
              style: const TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.w800,
                height: 1.25,
              ),
            ),
            const SizedBox(height: 4),
            Text(
              placeAddress,
              style: const TextStyle(
                fontSize: 14.5,
                height: 1.4,
                color: AppColors.textSecondary,
              ),
            ),
            const SizedBox(height: 14),
            Row(
              children: [
                Expanded(
                  child: PhoneCallButton(
                    phone: contactPhone,
                    label: isReturn ? 'Gọi kho' : 'Gọi khách',
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: OutlinedButton.icon(
                    onPressed: _openMap,
                    icon: const Icon(Icons.navigation_rounded, size: 18),
                    label: const Text('Dẫn đường'),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            const Divider(),
            const SizedBox(height: 12),
            _InfoRow(
              icon: Icons.schedule_rounded,
              label: 'Khung giờ giao',
              value: AppFormatters.timeWindow(
                order.timeWindowStart,
                order.timeWindowEnd,
              ),
            ),
            _InfoRow(
              icon: Icons.inventory_2_outlined,
              label: 'Kiện hàng',
              value: '1 kiện · ${order.weightKg.toStringAsFixed(1)} kg',
            ),
            if (order.packageDescription?.trim().isNotEmpty == true)
              _InfoRow(
                icon: Icons.description_outlined,
                label: 'Mô tả',
                value: order.packageDescription!.trim(),
              ),
            if (order.notes?.trim().isNotEmpty == true)
              _InfoRow(
                icon: Icons.sticky_note_2_outlined,
                label: 'Ghi chú',
                value: order.notes!.trim(),
                emphasize: true,
              ),
            if (!isReturn) ...[
              const SizedBox(height: 4),
              CodBadge(amount: order.codAmount, large: true),
            ],
            if (!isClosed) ...[
              const SizedBox(height: 20),
              if (isReturn)
                SlideToConfirmButton(
                  label: 'Vuốt xác nhận đã bàn giao tại kho',
                  subtitle: 'Kho sẽ kiểm nhận để hoàn tất yêu cầu',
                  icon: Icons.assignment_return_rounded,
                  onConfirmed: _advanceReturn,
                )
              else ...[
                SizedBox(
                  width: double.infinity,
                  height: 56,
                  child: FilledButton.icon(
                    onPressed: _completeDelivery,
                    style: FilledButton.styleFrom(
                      backgroundColor: AppColors.success,
                    ),
                    icon: const Icon(Icons.check_circle_rounded, size: 22),
                    label: const Text(
                      'Giao xong',
                      style: TextStyle(fontSize: 16),
                    ),
                  ),
                ),
                const SizedBox(height: 8),
                SizedBox(
                  width: double.infinity,
                  child: TextButton.icon(
                    onPressed: _reportIncident,
                    style: TextButton.styleFrom(
                      foregroundColor: AppColors.error,
                      minimumSize: const Size.fromHeight(48),
                    ),
                    icon: const Icon(
                      Icons.report_problem_outlined,
                      size: 18,
                    ),
                    label: const Text('Báo sự cố'),
                  ),
                ),
              ],
            ],
          ],
        ),
      ),
    );
  }
}

class _InfoRow extends StatelessWidget {
  const _InfoRow({
    required this.icon,
    required this.label,
    required this.value,
    this.emphasize = false,
  });

  final IconData icon;
  final String label;
  final String value;
  final bool emphasize;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, size: 18, color: AppColors.textSecondary),
          const SizedBox(width: 10),
          SizedBox(
            width: 104,
            child: Text(
              label,
              style: const TextStyle(
                fontSize: 13.5,
                color: AppColors.textSecondary,
              ),
            ),
          ),
          Expanded(
            child: Text(
              value,
              style: AppTheme.numStyle(
                fontSize: 14,
                fontWeight: FontWeight.w700,
                color: emphasize ? AppColors.warning : AppColors.textPrimary,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
