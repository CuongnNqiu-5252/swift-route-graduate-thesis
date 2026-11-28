// features/driver/presentation/widgets/next_stop_spotlight_card.dart

import 'package:flutter/material.dart';
import 'package:swiftroute_app/core/theme/app_colors.dart';
import 'package:swiftroute_app/core/utils/formatters.dart';
import 'package:swiftroute_app/core/widgets/field_ops_widgets.dart';
import 'package:swiftroute_app/features/driver/presentation/screens/driver_navigation_screen.dart';
import 'package:swiftroute_app/features/driver/presentation/widgets/driver_delivery_pod_sheet.dart';
import 'package:swiftroute_app/features/driver/presentation/widgets/driver_incident_sheet.dart';
import 'package:swiftroute_app/features/order/data/models/order_model.dart';

/// Dominant "Next Action First" operational card on the Today screen.
/// Shows the immediate next customer delivery or return-to-warehouse stop.
class NextStopSpotlightCard extends StatelessWidget {
  const NextStopSpotlightCard({super.key, required this.orders});

  final List<OrderModel> orders;

  @override
  Widget build(BuildContext context) {
    final active = orders
        .where(
          (o) =>
              o.deliveryTaskStatus != DeliveryTaskStatus.completed &&
              o.deliveryTaskStatus != DeliveryTaskStatus.cancelled &&
              o.deliveryTaskStatus != DeliveryTaskStatus.failed,
        )
        .toList();

    if (active.isEmpty) {
      final completedCount = orders
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

    final nextDelivery = active.where((o) => o.isOutForDelivery).firstOrNull;
    if (nextDelivery != null) {
      return _ActiveDeliverySpotlight(order: nextDelivery);
    }

    // Return-to-Sender task
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
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Wrap(
                  crossAxisAlignment: WrapCrossAlignment.center,
                  spacing: 6,
                  runSpacing: 6,
                  children: [
                    StatusPill(
                      label: isReturn
                          ? 'Hoàn hàng về kho'
                          : 'Đang giao · ${order.displayCode}',
                      foreground: isReturn
                          ? AppColors.error
                          : AppColors.primary,
                      background: isReturn
                          ? AppColors.errorSoft
                          : AppColors.primarySoft,
                      icon: isReturn
                          ? Icons.assignment_return_rounded
                          : Icons.two_wheeler_rounded,
                    ),
                    if (!isReturn) CodBadge(amount: order.codAmount),
                  ],
                ),
              ),
              const SizedBox(width: 8),
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
              Text.rich(
                TextSpan(
                  children: [
                    const WidgetSpan(
                      alignment: PlaceholderAlignment.middle,
                      child: Padding(
                        padding: EdgeInsets.only(right: 5),
                        child: Icon(
                          Icons.schedule_rounded,
                          size: 15,
                          color: AppColors.textSecondary,
                        ),
                      ),
                    ),
                    TextSpan(
                      text:
                          'Khung giờ: ${AppFormatters.timeWindow(order.timeWindowStart, order.timeWindowEnd)}',
                    ),
                  ],
                ),
                style: const TextStyle(
                  fontSize: 12.5,
                  fontWeight: FontWeight.w600,
                  color: AppColors.textSecondary,
                ),
              ),
              Text(
                '1 kiện · ${order.weightKg.toStringAsFixed(1)} kg',
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
