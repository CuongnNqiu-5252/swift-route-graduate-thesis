// features/driver/presentation/widgets/next_stop_spotlight_card.dart

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/theme/app_colors.dart';
import 'package:swiftroute_app/core/utils/formatters.dart';
import 'package:swiftroute_app/core/widgets/field_ops_widgets.dart';
import 'package:swiftroute_app/core/widgets/slide_to_confirm_button.dart';
import 'package:swiftroute_app/features/driver/application/driver_provider.dart';
import 'package:swiftroute_app/features/driver/presentation/screens/driver_navigation_screen.dart';
import 'package:swiftroute_app/features/driver/presentation/widgets/driver_delivery_pod_sheet.dart';
import 'package:swiftroute_app/features/driver/presentation/widgets/driver_incident_sheet.dart';
import 'package:swiftroute_app/features/order/data/models/order_model.dart';

/// Dominant "Next Action First" operational card on the Today screen.
/// Consolidates batch route acceptance, 1-swipe grouped depot pickup, and direct map cockpit launch.
class NextStopSpotlightCard extends ConsumerStatefulWidget {
  const NextStopSpotlightCard({super.key, required this.orders});

  final List<OrderModel> orders;

  @override
  ConsumerState<NextStopSpotlightCard> createState() =>
      _NextStopSpotlightCardState();
}

class _NextStopSpotlightCardState extends ConsumerState<NextStopSpotlightCard> {
  bool _isAcceptingBatch = false;

  Future<void> _acceptAllAssigned(List<OrderModel> assigned) async {
    setState(() => _isAcceptingBatch = true);
    try {
      await ref
          .read(driverOrdersProvider.notifier)
          .acceptAllAssigned(orderIds: assigned.map((o) => o.id).toList());
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            backgroundColor: AppColors.error,
            content: Text('Không nhận được chuyến: $e'),
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _isAcceptingBatch = false);
    }
  }

  Future<void> _swipeBatchPickup(List<OrderModel> depotGroup) async {
    try {
      await ref
          .read(driverOrdersProvider.notifier)
          .confirmBatchPickup(orderIds: depotGroup.map((o) => o.id).toList());
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          backgroundColor: AppColors.success,
          content: Text('Đã lấy ${depotGroup.length} đơn. Sẵn sàng đi giao!'),
        ),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          backgroundColor: AppColors.error,
          content: Text('Không xác nhận được lấy hàng: ${AppFormatters.errorMessage(e)}'),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final active = widget.orders
        .where(
          (o) =>
              o.deliveryTaskStatus != DeliveryTaskStatus.completed &&
              o.deliveryTaskStatus != DeliveryTaskStatus.cancelled &&
              o.deliveryTaskStatus != DeliveryTaskStatus.failed,
        )
        .toList();

    if (active.isEmpty) {
      final completedCount = widget.orders
          .where(
            (o) =>
                o.status == OrderStatus.delivered ||
                o.deliveryTaskStatus == DeliveryTaskStatus.completed,
          )
          .length;
      return EmptyStateCard(
        icon: completedCount > 0
            ? Icons.task_alt_rounded
            : Icons.route_outlined,
        title: completedCount > 0
            ? 'Đã hoàn thành $completedCount điểm giao hôm nay'
            : 'Chưa có điểm dừng nào đang mở',
        message:
            'Giữ trạng thái Trực tuyến để nhận lộ trình mới từ trung tâm điều phối.',
      );
    }

    // Case 1: Unaccepted ASSIGNED tasks -> 1-Tap Batch Start Route!
    final unaccepted = active
        .where((o) => o.deliveryTaskStatus == DeliveryTaskStatus.assigned)
        .toList();
    if (unaccepted.isNotEmpty &&
        active.every((o) => o.needsPickup || o.isReturnTask)) {
      final totalPackages = unaccepted.fold<int>(
        0,
        (sum, item) => sum + item.packageCount,
      );
      return Container(
        padding: const EdgeInsets.all(18),
        decoration: BoxDecoration(
          color: AppColors.card,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: AppColors.primaryBorder, width: 1.4),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                StatusPill(
                  label: '${unaccepted.length} đơn mới · $totalPackages kiện',
                  foreground: AppColors.primary,
                  background: AppColors.primarySoft,
                  icon: Icons.bolt_rounded,
                ),
              ],
            ),
            const SizedBox(height: 12),
            Text(
              'Chuyến giao tại ${unaccepted.first.pickupDisplayName}',
              style: const TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.w800,
                color: AppColors.textPrimary,
              ),
            ),
            const SizedBox(height: 4),
            Text(
              unaccepted.first.pickupAddress,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                fontSize: 13.5,
                color: AppColors.textSecondary,
              ),
            ),
            const SizedBox(height: 16),
            SizedBox(
              width: double.infinity,
              height: 54,
              child: FilledButton.icon(
                onPressed: _isAcceptingBatch
                    ? null
                    : () => _acceptAllAssigned(unaccepted),
                icon: _isAcceptingBatch
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(
                          strokeWidth: 2.2,
                          color: Colors.white,
                        ),
                      )
                    : const Icon(Icons.play_arrow_rounded, size: 22),
                label: Text(
                  _isAcceptingBatch
                      ? 'Đang nhận chuyến…'
                      : 'Bắt đầu chuyến (${unaccepted.length} đơn)',
                ),
              ),
            ),
          ],
        ),
      );
    }

    // Case 2: Prioritize active customer delivery first, else grouped depot pickup
    final nextDelivery = active.where((o) => o.isOutForDelivery).firstOrNull;
    if (nextDelivery != null) {
      return _ActiveDeliverySpotlight(order: nextDelivery);
    }

    final firstPickup = active.where((o) => o.needsPickup).firstOrNull;
    if (firstPickup != null) {
      final depotGroup = active
          .where(
            (o) =>
                o.needsPickup && o.pickupGroupKey == firstPickup.pickupGroupKey,
          )
          .toList();
      final totalPackages = depotGroup.fold<int>(
        0,
        (sum, item) => sum + item.packageCount,
      );

      return Container(
        padding: const EdgeInsets.all(18),
        decoration: BoxDecoration(
          color: AppColors.card,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: AppColors.warningBorder, width: 1.4),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: StatusPill(
                    label:
                        'Trạm lấy hàng · ${depotGroup.length} đơn ($totalPackages kiện)',
                    foreground: AppColors.warning,
                    background: AppColors.warningSoft,
                    icon: Icons.warehouse_rounded,
                  ),
                ),
                const SizedBox(width: 8),
                PhoneCallButton(phone: firstPickup.senderPhone, compact: true),
              ],
            ),
            const SizedBox(height: 10),
            Text(
              firstPickup.pickupDisplayName,
              style: const TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.w800,
                color: AppColors.textPrimary,
              ),
            ),
            const SizedBox(height: 4),
            Text(
              firstPickup.pickupAddress,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                fontSize: 13.5,
                color: AppColors.textSecondary,
              ),
            ),
            const SizedBox(height: 16),
            SizedBox(
              width: double.infinity,
              child: OutlinedButton.icon(
                onPressed: () => Navigator.of(context).push(
                  MaterialPageRoute(
                    builder: (_) => DriverNavigationScreen(order: firstPickup),
                  ),
                ),
                style: OutlinedButton.styleFrom(
                  backgroundColor: AppColors.primarySoft,
                  foregroundColor: AppColors.primary,
                  side: const BorderSide(color: AppColors.primaryBorder),
                  minimumSize: const Size.fromHeight(48),
                ),
                icon: const Icon(Icons.navigation_rounded, size: 18),
                label: const Text('Mở Bản đồ Dẫn đường tới Kho'),
              ),
            ),
            const SizedBox(height: 10),
            SlideToConfirmButton(
              label: depotGroup.length > 1
                  ? 'Vuốt đã lấy $totalPackages kiện (${depotGroup.length} đơn)'
                  : 'Vuốt xác nhận Đã lấy hàng',
              backgroundColor: AppColors.accent,
              icon: Icons.inventory_2_rounded,
              onConfirmed: () => _swipeBatchPickup(depotGroup),
            ),
          ],
        ),
      );
    }

    // Case 3: Return-to-Sender task
    final returnOrder = active.first;
    return _ActiveDeliverySpotlight(order: returnOrder);
  }
}

class _ActiveDeliverySpotlight extends StatelessWidget {
  const _ActiveDeliverySpotlight({required this.order});

  final OrderModel order;

  @override
  Widget build(BuildContext context) {
    final isReturn = order.isReturnTask;

    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: AppColors.card,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppColors.primaryBorder, width: 1.4),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Flexible(
                child: StatusPill(
                  label: isReturn
                      ? 'Hoàn hàng về kho'
                      : 'Đang giao · ${order.displayCode}',
                  foreground: isReturn ? AppColors.error : AppColors.primary,
                  background: isReturn
                      ? AppColors.errorSoft
                      : AppColors.primarySoft,
                  icon: isReturn
                      ? Icons.assignment_return_rounded
                      : Icons.two_wheeler_rounded,
                ),
              ),
              const SizedBox(width: 8),
              if (!isReturn) ...[
                CodBadge(amount: order.codAmount),
                const SizedBox(width: 8),
              ],
              const Spacer(),
              PhoneCallButton(
                phone: isReturn ? order.senderPhone : order.recipientPhone,
                compact: true,
              ),
            ],
          ),
          const SizedBox(height: 12),
          Text(
            isReturn
                ? order.pickupDisplayName
                : (order.recipientName?.isNotEmpty == true
                      ? order.recipientName!
                      : 'Điểm giao tiếp theo'),
            style: const TextStyle(
              fontSize: 18,
              fontWeight: FontWeight.w800,
              color: AppColors.textPrimary,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            isReturn ? order.pickupAddress : order.deliveryAddress,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(
              fontSize: 14,
              height: 1.35,
              color: AppColors.textSecondary,
            ),
          ),
          const SizedBox(height: 10),
          Wrap(
            crossAxisAlignment: WrapCrossAlignment.center,
            spacing: 12,
            runSpacing: 4,
            children: [
              Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Icon(
                    Icons.schedule_rounded,
                    size: 15,
                    color: AppColors.textSecondary,
                  ),
                  const SizedBox(width: 5),
                  Text(
                    'Khung giờ: ${AppFormatters.timeWindow(order.timeWindowStart, order.timeWindowEnd)}',
                    style: const TextStyle(
                      fontSize: 12.5,
                      fontWeight: FontWeight.w600,
                      color: AppColors.textSecondary,
                    ),
                  ),
                ],
              ),
              Text(
                '${order.packageCount} kiện · ${order.weightKg.toStringAsFixed(1)} kg',
                style: const TextStyle(
                  fontSize: 12.5,
                  fontWeight: FontWeight.w600,
                  color: AppColors.textSecondary,
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),

          // Primary Map Cockpit CTA + Direct Delivery Completion
          SizedBox(
            width: double.infinity,
            height: 52,
            child: FilledButton.icon(
              onPressed: () => Navigator.of(context).push(
                MaterialPageRoute(
                  builder: (_) => DriverNavigationScreen(order: order),
                ),
              ),
              icon: const Icon(Icons.navigation_rounded, size: 20),
              label: const Text('Mở Bản đồ Dẫn đường & Giao hàng'),
            ),
          ),
          if (!isReturn) ...[
            const SizedBox(height: 10),
            Row(
              children: [
                Expanded(
                  flex: 4,
                  child: OutlinedButton.icon(
                    onPressed: () => DriverIncidentSheet.show(context, order),
                    style: OutlinedButton.styleFrom(
                      foregroundColor: AppColors.error,
                      side: const BorderSide(color: AppColors.errorBorder),
                      backgroundColor: AppColors.errorSoft,
                    ),
                    icon: const Icon(Icons.report_problem_outlined, size: 18),
                    label: const Text('Sự cố'),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  flex: 6,
                  child: OutlinedButton.icon(
                    onPressed: () =>
                        DriverDeliveryPodSheet.show(context, order),
                    style: OutlinedButton.styleFrom(
                      foregroundColor: AppColors.success,
                      side: const BorderSide(color: AppColors.successBorder),
                      backgroundColor: AppColors.successSoft,
                    ),
                    icon: const Icon(
                      Icons.check_circle_outline_rounded,
                      size: 18,
                    ),
                    label: const Text('Giao xong ngay'),
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
