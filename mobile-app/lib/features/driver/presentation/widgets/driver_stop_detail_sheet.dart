import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/theme/app_colors.dart';
import 'package:swiftroute_app/features/driver/application/driver_provider.dart';
import 'package:swiftroute_app/features/driver/data/datasources/driver_remote_data_source.dart';
import 'package:swiftroute_app/features/driver/presentation/screens/driver_navigation_screen.dart';
import 'package:swiftroute_app/features/driver/presentation/widgets/incident_report_dialog.dart';
import 'package:swiftroute_app/features/order/data/models/order_model.dart';
import 'package:swiftroute_app/features/order/data/datasources/order_remote_data_source.dart';

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
      builder: (_) =>
          DriverStopDetailSheet(order: order, sequenceNumber: sequenceNumber),
    );
  }

  @override
  ConsumerState<DriverStopDetailSheet> createState() =>
      _DriverStopDetailSheetState();
}

class _DriverStopDetailSheetState extends ConsumerState<DriverStopDetailSheet> {
  var _isUpdating = false;

  Future<void> _updateStatus(
    DeliveryTaskStatus nextStatus, {
    String? failureReason,
    String? notes,
    String? proofUrl,
  }) async {
    final taskId = widget.order.deliveryTaskId;
    if (taskId == null || taskId.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Nhiệm vụ chưa có mã thực thi. Hãy tải lại lộ trình.'),
        ),
      );
      return;
    }
    setState(() => _isUpdating = true);
    try {
      await ref
          .read(driverOrdersProvider.notifier)
          .updateDeliveryTask(
            orderId: widget.order.id,
            taskId: taskId,
            nextStatus: nextStatus,
            failureReason: failureReason,
            notes: notes,
            proofUrl: proofUrl,
          );
      if (mounted) {
        Navigator.of(context).pop();
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              'Đơn #${widget.order.id.substring(0, 8)}: ${nextStatus.displayName}',
            ),
            backgroundColor: nextStatus == DeliveryTaskStatus.failed
                ? AppColors.error
                : AppColors.success,
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
      setState(() => _isUpdating = true);
      try {
        final taskId = widget.order.deliveryTaskId;
        if (taskId == null) throw StateError('Nhiệm vụ chưa có mã thực thi');
        await ref
            .read(driverOrdersProvider.notifier)
            .reportIncident(
              orderId: widget.order.id,
              taskId: taskId,
              incidentType: result.type.name,
              notes: result.note.isEmpty ? null : result.note,
            );
        if (mounted) Navigator.of(context).pop();
      } finally {
        if (mounted) setState(() => _isUpdating = false);
      }
    }
  }

  Future<void> _advanceTask() async {
    final current =
        widget.order.deliveryTaskStatus ?? DeliveryTaskStatus.assigned;
    final isReturn = widget.order.deliveryTaskType == 'RETURN';
    final next = isReturn
        ? switch (current) {
            DeliveryTaskStatus.assigned => DeliveryTaskStatus.accepted,
            DeliveryTaskStatus.accepted => DeliveryTaskStatus.outForDelivery,
            _ => null,
          }
        : switch (current) {
            DeliveryTaskStatus.assigned => DeliveryTaskStatus.accepted,
            DeliveryTaskStatus.accepted => DeliveryTaskStatus.arrived,
            DeliveryTaskStatus.arrived => DeliveryTaskStatus.pickedUp,
            DeliveryTaskStatus.pickedUp => DeliveryTaskStatus.outForDelivery,
            _ => null,
          };
    if (next == null) return;
    String? notes;
    if (next == DeliveryTaskStatus.pickedUp) {
      final controller = TextEditingController();
      final confirmed = await showDialog<bool>(
        context: context,
        builder: (dialogContext) => AlertDialog(
          title: const Text('Xác nhận nhận kiện hàng'),
          content: TextField(
            controller: controller,
            decoration: const InputDecoration(
              labelText: 'Ghi chú bàn giao (tuỳ chọn)',
              hintText: 'Tình trạng kiện, người bàn giao…',
            ),
            maxLines: 2,
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(dialogContext).pop(false),
              child: const Text('Quay lại'),
            ),
            FilledButton(
              onPressed: () => Navigator.of(dialogContext).pop(true),
              child: const Text('Đã nhận kiện'),
            ),
          ],
        ),
      );
      notes = controller.text.trim();
      controller.dispose();
      if (confirmed != true) return;
    }
    await _updateStatus(next, notes: notes?.isEmpty == true ? null : notes);
  }

  Future<void> _confirmDelivery() async {
    if (widget.order.deliveryTaskType == 'RETURN') {
      await _confirmReturnDelivery();
      return;
    }
    final proofUrlController = TextEditingController();
    final codAmountController = TextEditingController(
      text: widget.order.codAmount > 0
          ? widget.order.codAmount.toStringAsFixed(0)
          : '',
    );
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Xác nhận đã giao hàng'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: proofUrlController,
              keyboardType: TextInputType.url,
              decoration: const InputDecoration(
                labelText: 'Link chứng từ giao hàng (tuỳ chọn)',
                hintText: 'https://…',
              ),
            ),
            if (widget.order.codAmount > 0) ...[
              const SizedBox(height: 12),
              TextField(
                controller: codAmountController,
                keyboardType: const TextInputType.numberWithOptions(
                  decimal: true,
                ),
                decoration: InputDecoration(
                  labelText: 'COD đã thu',
                  helperText:
                      'Phải đúng ${widget.order.codAmount.toStringAsFixed(0)} đ',
                ),
              ),
            ],
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text('Quay lại'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: const Text('Xác nhận giao'),
          ),
        ],
      ),
    );
    final proofUrl = proofUrlController.text.trim();
    final codAmount = double.tryParse(codAmountController.text.trim());
    proofUrlController.dispose();
    codAmountController.dispose();
    if (confirmed == true && mounted) {
      if (widget.order.codAmount > 0 && codAmount != widget.order.codAmount) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Số COD phải khớp với số tiền trên đơn.'),
          ),
        );
        return;
      }
      setState(() => _isUpdating = true);
      try {
        await ref
            .read(driverOrdersProvider.notifier)
            .completeDelivery(
              orderId: widget.order.id,
              taskId: widget.order.deliveryTaskId!,
              proofUrl: proofUrl.isEmpty ? null : proofUrl,
              codAmount: widget.order.codAmount > 0 ? codAmount : null,
            );
        if (mounted) {
          Navigator.of(context).pop();
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Đã xác nhận giao hàng và ghi nhận COD.'),
              backgroundColor: AppColors.success,
            ),
          );
        }
      } catch (_) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text(
                'Đơn đã giao nhưng không thể ghi nhận COD. Hãy báo điều phối viên.',
              ),
            ),
          );
        }
      } finally {
        if (mounted) setState(() => _isUpdating = false);
      }
    }
  }

  Future<void> _confirmReturnDelivery() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Xác nhận đã đưa hàng về'),
        content: const Text(
          'Sau bước này, đơn vị vận hành sẽ kiểm nhận kiện hàng và xác nhận hoàn tất trên Web Admin.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text('Quay lại'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: const Text('Đã bàn giao hàng hoàn'),
          ),
        ],
      ),
    );
    if (confirmed == true) await _updateStatus(DeliveryTaskStatus.completed);
  }

  Future<void> _createPackageHandover() async {
    setState(() => _isUpdating = true);
    try {
      final currentUserId = ref.read(driverProvider).valueOrNull?.userId;
      final drivers = await ref
          .read(driverRemoteDataSourceProvider)
          .getTenantDrivers();
      final candidates = drivers
          .where(
            (driver) =>
                driver.userId != null &&
                driver.userId != currentUserId &&
                driver.status == 'ACTIVE',
          )
          .toList();
      if (!mounted) return;
      final selected = await showDialog<String>(
        context: context,
        builder: (dialogContext) => SimpleDialog(
          title: const Text('Bàn giao toàn bộ kiện cho'),
          children: candidates.isEmpty
              ? [
                  const Padding(
                    padding: EdgeInsets.all(20),
                    child: Text('Không có tài xế khác đang hoạt động.'),
                  ),
                ]
              : candidates
                    .map(
                      (driver) => SimpleDialogOption(
                        onPressed: () =>
                            Navigator.of(dialogContext).pop(driver.userId),
                        child: ListTile(
                          contentPadding: EdgeInsets.zero,
                          leading: const CircleAvatar(
                            child: Icon(Icons.person_rounded),
                          ),
                          title: Text(driver.fullName),
                          subtitle: Text(
                            '${driver.licensePlate} · ${driver.phone}',
                          ),
                        ),
                      ),
                    )
                    .toList(),
        ),
      );
      if (selected == null) return;
      await ref
          .read(orderRemoteDataSourceProvider)
          .createPackageHandover(
            orderId: widget.order.id,
            toDriverId: selected,
          );
      await ref.read(packageHandoverProvider.notifier).refresh();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
              'Đã tạo lượt bàn giao. Hai tài xế cần xác nhận trong mục Bàn giao.',
            ),
            backgroundColor: AppColors.success,
          ),
        );
      }
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
              'Không thể tạo bàn giao kiện. Chỉ kiện đã nhận và chưa có bàn giao khác mới được chuyển.',
            ),
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _isUpdating = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final order = widget.order;
    final isReturn = order.deliveryTaskType == 'RETURN';
    final taskStatus = order.deliveryTaskStatus ?? DeliveryTaskStatus.assigned;
    final statusColor = switch (taskStatus) {
      DeliveryTaskStatus.outForDelivery => AppColors.accent,
      DeliveryTaskStatus.completed => AppColors.success,
      DeliveryTaskStatus.failed => AppColors.error,
      _ => Colors.blue.shade700,
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
                  style: const TextStyle(
                    color: Colors.white,
                    fontWeight: FontWeight.bold,
                    fontSize: 15,
                  ),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '${isReturn ? 'Hoàn hàng' : 'Đơn hàng'} #${order.id.length > 8 ? order.id.substring(0, 8) : order.id}',
                      style: const TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    Text(
                      order.tenantName ?? 'Kho vận SwiftRoute Hub',
                      style: const TextStyle(
                        fontSize: 12,
                        color: AppColors.textSecondary,
                      ),
                    ),
                  ],
                ),
              ),
              Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 10,
                  vertical: 4,
                ),
                decoration: BoxDecoration(
                  color: statusColor.withValues(alpha: 0.1),
                  borderRadius: BorderRadius.circular(6),
                ),
                child: Text(
                  taskStatus.displayName,
                  style: TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.bold,
                    color: statusColor,
                  ),
                ),
              ),
            ],
          ),
          const Divider(height: 24, color: AppColors.divider),
          // Delivery details
          Text(
            isReturn
                ? 'ĐIỂM HOÀN HÀNG'
                : (taskStatus.index < DeliveryTaskStatus.pickedUp.index
                      ? 'ĐIỂM LẤY HÀNG'
                      : 'ĐIỂM GIAO HÀNG'),
            style: const TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.bold,
              color: AppColors.textSecondary,
            ),
          ),
          const SizedBox(height: 4),
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Icon(
                Icons.location_on_rounded,
                color: AppColors.primary,
                size: 20,
              ),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  isReturn
                      ? order.pickupAddress
                      : (taskStatus.index < DeliveryTaskStatus.pickedUp.index
                            ? order.pickupAddress
                            : order.deliveryAddress),
                  style: const TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w600,
                  ),
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
                  value:
                      '${_formatTime(order.timeWindowStart)} - ${_formatTime(order.timeWindowEnd)}',
                  icon: Icons.access_time_rounded,
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: _InfoTile(
                  title: 'Trọng lượng gói',
                  value:
                      '${order.packageCount} kiện · ${order.weightKg.toStringAsFixed(1)} kg',
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
                  child: Icon(
                    Icons.person,
                    color: AppColors.textPrimary,
                    size: 20,
                  ),
                ),
                const SizedBox(width: 12),
                const Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Khách nhận hàng',
                        style: TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      Text(
                        '0909 ••• ••• (Được bảo mật)',
                        style: TextStyle(
                          fontSize: 11,
                          color: AppColors.textSecondary,
                        ),
                      ),
                    ],
                  ),
                ),
                IconButton(
                  onPressed: () {
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(
                        content: Text(
                          'Đang kết nối cuộc gọi che số với khách hàng...',
                        ),
                      ),
                    );
                  },
                  icon: const Icon(
                    Icons.phone_rounded,
                    color: AppColors.primary,
                  ),
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
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(10),
                    ),
                  ),
                ),
              ),
              const SizedBox(width: 10),
              // State transition button
              if (taskStatus != DeliveryTaskStatus.outForDelivery)
                Expanded(
                  flex: 6,
                  child: FilledButton.icon(
                    onPressed: _isUpdating ? null : _advanceTask,
                    icon: _isUpdating
                        ? const SizedBox(
                            width: 16,
                            height: 16,
                            child: CircularProgressIndicator(
                              strokeWidth: 2,
                              color: Colors.white,
                            ),
                          )
                        : Icon(_taskActionIcon(taskStatus), size: 18),
                    label: Text(
                      _taskActionLabel(taskStatus),
                      style: const TextStyle(fontWeight: FontWeight.bold),
                    ),
                    style: FilledButton.styleFrom(
                      backgroundColor: AppColors.primary,
                      padding: const EdgeInsets.symmetric(vertical: 12),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(10),
                      ),
                    ),
                  ),
                ),
              if (taskStatus == DeliveryTaskStatus.outForDelivery) ...[
                Expanded(
                  flex: 6,
                  child: FilledButton.icon(
                    onPressed: _isUpdating ? null : _confirmDelivery,
                    icon: _isUpdating
                        ? const SizedBox(
                            width: 16,
                            height: 16,
                            child: CircularProgressIndicator(
                              strokeWidth: 2,
                              color: Colors.white,
                            ),
                          )
                        : const Icon(Icons.check_circle_rounded, size: 18),
                    label: Text(
                      isReturn ? 'Đã đưa về kho' : 'Đã giao xong',
                      style: const TextStyle(fontWeight: FontWeight.bold),
                    ),
                    style: FilledButton.styleFrom(
                      backgroundColor: AppColors.success,
                      padding: const EdgeInsets.symmetric(vertical: 12),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(10),
                      ),
                    ),
                  ),
                ),
              ],
            ],
          ),
          if (!isReturn && taskStatus != DeliveryTaskStatus.assigned) ...[
            const SizedBox(height: 8),
            SizedBox(
              width: double.infinity,
              child: TextButton.icon(
                onPressed: _isUpdating ? null : _handleIncident,
                icon: const Icon(
                  Icons.report_problem_rounded,
                  color: AppColors.error,
                  size: 16,
                ),
                label: const Text(
                  'Báo sự cố / Giao thất bại',
                  style: TextStyle(
                    color: AppColors.error,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
            ),
          ],
          if (taskStatus == DeliveryTaskStatus.pickedUp ||
              taskStatus == DeliveryTaskStatus.outForDelivery) ...[
            const SizedBox(height: 4),
            SizedBox(
              width: double.infinity,
              child: TextButton.icon(
                onPressed: _isUpdating ? null : _createPackageHandover,
                icon: const Icon(Icons.swap_horiz_rounded, size: 18),
                label: const Text('Bàn giao kiện cho tài xế khác'),
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

  String _taskActionLabel(DeliveryTaskStatus status) {
    if (widget.order.deliveryTaskType == 'RETURN') {
      return switch (status) {
        DeliveryTaskStatus.assigned => 'Nhận nhiệm vụ hoàn',
        DeliveryTaskStatus.accepted => 'Bắt đầu về kho',
        _ => 'Tiếp tục',
      };
    }
    return switch (status) {
      DeliveryTaskStatus.assigned => 'Nhận nhiệm vụ',
      DeliveryTaskStatus.accepted => 'Đã đến điểm lấy',
      DeliveryTaskStatus.arrived => 'Xác nhận nhận kiện',
      DeliveryTaskStatus.pickedUp => 'Bắt đầu giao',
      _ => 'Tiếp tục',
    };
  }

  IconData _taskActionIcon(DeliveryTaskStatus status) => switch (status) {
    DeliveryTaskStatus.assigned => Icons.assignment_turned_in_rounded,
    DeliveryTaskStatus.accepted => Icons.store_mall_directory_rounded,
    DeliveryTaskStatus.arrived => Icons.inventory_2_rounded,
    DeliveryTaskStatus.pickedUp => Icons.two_wheeler_rounded,
    _ => Icons.arrow_forward_rounded,
  };
}

class _InfoTile extends StatelessWidget {
  const _InfoTile({
    required this.title,
    required this.value,
    required this.icon,
  });
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
              Text(
                title,
                style: const TextStyle(
                  fontSize: 11,
                  color: AppColors.textSecondary,
                ),
              ),
            ],
          ),
          const SizedBox(height: 4),
          Text(
            value,
            style: const TextStyle(fontSize: 13, fontWeight: FontWeight.bold),
          ),
        ],
      ),
    );
  }
}
