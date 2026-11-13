// features/driver/presentation/widgets/driver_stop_detail_sheet.dart

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/theme/app_colors.dart';
import 'package:swiftroute_app/core/theme/app_theme.dart';
import 'package:swiftroute_app/core/utils/formatters.dart';
import 'package:swiftroute_app/core/widgets/field_ops_widgets.dart';
import 'package:swiftroute_app/core/widgets/slide_to_confirm_button.dart';
import 'package:swiftroute_app/features/driver/application/driver_provider.dart';
import 'package:swiftroute_app/features/driver/data/datasources/driver_remote_data_source.dart';
import 'package:swiftroute_app/features/driver/presentation/screens/driver_navigation_screen.dart';
import 'package:swiftroute_app/features/driver/presentation/widgets/driver_delivery_pod_sheet.dart';
import 'package:swiftroute_app/features/driver/presentation/widgets/driver_incident_sheet.dart';
import 'package:swiftroute_app/features/order/data/datasources/order_remote_data_source.dart';
import 'package:swiftroute_app/features/order/data/models/order_model.dart';

/// Waybill detail sheet for a stop (D06).
///
/// Exposes exactly one primary action per phase:
/// - package still at depot → swipe to confirm pickup (fast-track to OUT_FOR_DELIVERY)
/// - package on the vehicle → "Giao xong" opening the POD/COD sheet
/// - return task → swipe to start return / hand over to warehouse
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
  bool _isBusy = false;

  OrderModel get _order => widget.order;

  void _showSnack(String message, {Color? color}) {
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text(message), backgroundColor: color));
  }

  Future<void> _confirmPickup() async {
    try {
      await ref
          .read(driverOrdersProvider.notifier)
          .confirmBatchPickup(orderIds: [_order.id]);
      if (!mounted) return;
      Navigator.of(context).pop();
      _showSnack(
        'Đã lấy ${_order.displayCode}. Sẵn sàng đi giao.',
        color: AppColors.success,
      );
    } catch (e) {
      if (mounted) {
        _showSnack(
          'Không xác nhận được lấy hàng: ${AppFormatters.errorMessage(e)}',
          color: AppColors.error,
        );
      }
    }
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
    final status = _order.deliveryTaskStatus ?? DeliveryTaskStatus.assigned;
    try {
      if (status == DeliveryTaskStatus.assigned ||
          status == DeliveryTaskStatus.accepted) {
        await ref
            .read(driverOrdersProvider.notifier)
            .confirmBatchPickup(
              orderIds: [_order.id],
              notes: 'Bắt đầu hoàn kiện về kho',
            );
      } else {
        // Warehouse staff verifies receipt afterwards on Web Admin.
        await ref
            .read(driverOrdersProvider.notifier)
            .updateDeliveryTask(
              orderId: _order.id,
              taskId: taskId,
              nextStatus: DeliveryTaskStatus.completed,
              notes: 'Đã bàn giao kiện hoàn cho kho',
            );
      }
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

  /// Starts a dual-confirmation package handover to another active driver.
  Future<void> _createPackageHandover() async {
    setState(() => _isBusy = true);
    try {
      final currentUserId = ref.read(driverProvider).valueOrNull?.userId;
      final drivers = await ref
          .read(driverRemoteDataSourceProvider)
          .getTenantDrivers();
      final candidates = drivers
          .where(
            (d) =>
                d.userId != null &&
                d.userId != currentUserId &&
                d.status == 'ACTIVE',
          )
          .toList();
      if (!mounted) return;
      final selected = await showModalBottomSheet<String>(
        context: context,
        builder: (sheetContext) => SafeArea(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Padding(
                padding: EdgeInsets.fromLTRB(20, 20, 20, 8),
                child: Text(
                  'Bàn giao kiện cho tài xế',
                  style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800),
                ),
              ),
              if (candidates.isEmpty)
                const Padding(
                  padding: EdgeInsets.fromLTRB(20, 8, 20, 24),
                  child: Text(
                    'Không có tài xế khác đang hoạt động.',
                    style: TextStyle(color: AppColors.textSecondary),
                  ),
                )
              else
                for (final d in candidates)
                  ListTile(
                    minVerticalPadding: 12,
                    leading: const CircleAvatar(
                      backgroundColor: AppColors.primarySoft,
                      child: Icon(
                        Icons.person_rounded,
                        color: AppColors.primary,
                      ),
                    ),
                    title: Text(
                      d.fullName,
                      style: const TextStyle(fontWeight: FontWeight.w700),
                    ),
                    subtitle: Text('${d.licensePlate} · ${d.phone}'),
                    onTap: () => Navigator.of(sheetContext).pop(d.userId),
                  ),
              const SizedBox(height: 12),
            ],
          ),
        ),
      );
      if (selected == null) return;
      await ref
          .read(orderRemoteDataSourceProvider)
          .createPackageHandover(orderId: _order.id, toDriverId: selected);
      await ref.read(packageHandoverProvider.notifier).refresh();
      if (mounted) {
        _showSnack(
          'Đã tạo lượt bàn giao. Hai tài xế xác nhận trong tab Bàn giao.',
          color: AppColors.success,
        );
      }
    } catch (_) {
      if (mounted) {
        _showSnack(
          'Không thể tạo bàn giao. Chỉ kiện đã nhận và chưa có bàn giao khác mới được chuyển.',
        );
      }
    } finally {
      if (mounted) setState(() => _isBusy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final order = _order;
    final status = order.deliveryTaskStatus ?? DeliveryTaskStatus.assigned;
    final isClosed =
        status == DeliveryTaskStatus.completed ||
        status == DeliveryTaskStatus.failed ||
        status == DeliveryTaskStatus.cancelled;
    final atDepotPhase = order.needsPickup || order.isReturnTask;
    final bottom = MediaQuery.paddingOf(context).bottom;

    final placeTitle = order.isReturnTask
        ? 'Hoàn về ${order.pickupDisplayName}'
        : order.needsPickup
        ? 'Lấy hàng tại ${order.pickupDisplayName}'
        : (order.recipientName?.trim().isNotEmpty == true
              ? order.recipientName!.trim()
              : 'Khách nhận hàng');
    final placeAddress = atDepotPhase
        ? order.pickupAddress
        : order.deliveryAddress;
    final contactPhone = atDepotPhase
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
                    label: atDepotPhase ? 'Gọi kho' : 'Gọi khách',
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
              value:
                  '${order.packageCount} kiện · ${order.weightKg.toStringAsFixed(1)} kg',
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
            if (!order.isReturnTask) ...[
              const SizedBox(height: 4),
              CodBadge(amount: order.codAmount, large: true),
            ],
            if (!isClosed) ...[
              const SizedBox(height: 20),
              if (order.isReturnTask)
                SlideToConfirmButton(
                  label: status == DeliveryTaskStatus.outForDelivery
                      ? 'Vuốt bàn giao kiện hoàn cho kho'
                      : 'Vuốt bắt đầu mang kiện về kho',
                  icon: Icons.assignment_return_rounded,
                  onConfirmed: _advanceReturn,
                )
              else if (order.needsPickup)
                SlideToConfirmButton(
                  label: 'Vuốt đã lấy ${order.packageCount} kiện',
                  subtitle: 'Đơn chuyển thẳng sang đang giao',
                  backgroundColor: AppColors.accent,
                  icon: Icons.inventory_2_rounded,
                  onConfirmed: _confirmPickup,
                )
              else
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
              Row(
                children: [
                  if (!order.isReturnTask)
                    Expanded(
                      child: TextButton.icon(
                        onPressed: _isBusy ? null : _reportIncident,
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
                  if (order.isOutForDelivery)
                    Expanded(
                      child: TextButton.icon(
                        onPressed: _isBusy ? null : _createPackageHandover,
                        style: TextButton.styleFrom(
                          minimumSize: const Size.fromHeight(48),
                        ),
                        icon: _isBusy
                            ? const SizedBox(
                                width: 16,
                                height: 16,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                ),
                              )
                            : const Icon(Icons.swap_horiz_rounded, size: 18),
                        label: const Text('Bàn giao kiện'),
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
