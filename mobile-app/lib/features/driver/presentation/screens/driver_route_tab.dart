import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/theme/app_colors.dart';
import 'package:swiftroute_app/core/utils/formatters.dart';
import 'package:swiftroute_app/core/widgets/field_ops_widgets.dart';
import 'package:swiftroute_app/features/driver/application/driver_provider.dart';
import 'package:swiftroute_app/features/driver/presentation/screens/route_assignment_screen.dart';
import 'package:swiftroute_app/features/driver/presentation/widgets/driver_stop_detail_sheet.dart';
import 'package:swiftroute_app/features/order/data/models/order_model.dart';

class DriverRouteTab extends ConsumerWidget {
  const DriverRouteTab({
    super.key,
    required this.completed,
    required this.onRefresh,
  });

  final bool completed;
  final Future<void> Function() onRefresh;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final plans = ref.watch(driverDispatchPlansProvider).valueOrNull ?? [];
    final ranks = <String, int>{
      for (final plan in plans)
        for (final (index, id) in plan.orderedStopIds.indexed) id: index,
    };

    return RefreshIndicator(
      onRefresh: onRefresh,
      child: ref
          .watch(driverOrdersProvider)
          .when(
            loading: () => _list(const [
              ShimmerBox(height: 88),
              SizedBox(height: 12),
              ShimmerBox(height: 88),
            ]),
            error: (_, _) => _list([
              EmptyStateCard(
                icon: Icons.cloud_off_outlined,
                title: 'Không tải được đơn hàng',
                message: 'Kiểm tra kết nối rồi kéo xuống để tải lại.',
              ),
            ]),
            data: (all) {
              final visible = completed
                  ? all
                        .where(
                          (order) =>
                              order.deliveryTaskStatus ==
                              DeliveryTaskStatus.completed,
                        )
                        .toList()
                  : all
                        .where(
                          (order) =>
                              order.deliveryTaskStatus ==
                              DeliveryTaskStatus.outForDelivery,
                        )
                        .toList();
              if (!completed) {
                visible.sort((a, b) {
                  final rank = (ranks[a.id] ?? visible.length).compareTo(
                    ranks[b.id] ?? visible.length,
                  );
                  return rank == 0 ? a.id.compareTo(b.id) : rank;
                });
              }
              if (visible.isEmpty) {
                return _list([
                  EmptyStateCard(
                    icon: completed ? Icons.task_alt : Icons.route_outlined,
                    title: completed
                        ? 'Chưa có đơn hoàn thành'
                        : 'Không có đơn cần làm',
                    message: completed
                        ? 'Đơn đã giao xong hôm nay sẽ hiện tại đây.'
                        : 'Đơn mới sẽ hiện khi điều phối giao tuyến.',
                  ),
                ]);
              }
              return _list([
                if (completed) ...[
                  _heading(context, 'Đã giao hôm nay', visible.length),
                  _orderList(context, visible, ranks),
                ] else ...[
                  _heading(context, 'Điểm dừng tiếp theo'),
                  _orderList(context, [visible.first], ranks),
                  if (plans
                          .where(
                            (plan) =>
                                plan.orderedStopIds.contains(visible.first.id),
                          )
                          .firstOrNull
                      case final plan?)
                    Align(
                      alignment: Alignment.centerLeft,
                      child: TextButton.icon(
                        onPressed: () => Navigator.of(context).push<void>(
                          MaterialPageRoute(
                            builder: (_) =>
                                RouteAssignmentScreen(plan: plan, orders: all),
                          ),
                        ),
                        icon: const Icon(Icons.route_outlined),
                        label: const Text('Xem chi tiết tuyến'),
                      ),
                    ),
                  if (visible.length > 1) ...[
                    const SizedBox(height: 24),
                    _heading(context, 'Lịch trình', visible.length - 1),
                    _orderList(context, visible.skip(1), ranks),
                  ],
                ],
              ]);
            },
          ),
    );
  }

  Widget _list(List<Widget> children) => ListView(
    physics: const AlwaysScrollableScrollPhysics(),
    padding: const EdgeInsets.fromLTRB(16, 18, 16, 28),
    children: children,
  );

  Widget _heading(BuildContext context, String title, [int? count]) {
    final style = Theme.of(context).textTheme.labelLarge;
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Row(
        children: [
          Expanded(child: Text(title, style: style)),
          if (count != null)
            Text(
              completed ? '$count đơn' : '$count điểm',
              style: style?.copyWith(color: AppColors.textSecondary),
            ),
        ],
      ),
    );
  }

  Widget _orderList(
    BuildContext context,
    Iterable<OrderModel> orders,
    Map<String, int> ranks,
  ) {
    return Material(
      color: AppColors.card,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: const BorderSide(color: AppColors.divider),
      ),
      clipBehavior: Clip.antiAlias,
      child: Column(
        children: [
          for (final (index, order) in orders.indexed) ...[
            if (index > 0) const Divider(height: 1, indent: 56),
            ListTile(
              contentPadding: const EdgeInsets.symmetric(
                horizontal: 14,
                vertical: 7,
              ),
              leading: Icon(
                completed
                    ? Icons.check_circle_outline
                    : Icons.local_shipping_outlined,
                color: completed ? AppColors.success : AppColors.primaryDark,
              ),
              title: Text(
                order.recipientName?.trim().isNotEmpty == true
                    ? order.recipientName!.trim()
                    : order.displayCode,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(fontWeight: FontWeight.w700),
              ),
              subtitle: Padding(
                padding: const EdgeInsets.only(top: 4),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      order.deliveryAddress,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                    ),
                    const SizedBox(height: 3),
                    Text(
                      [
                        order.displayCode,
                        if (ranks[order.id] case final rank?)
                          'Điểm ${rank + 1}',
                        if (!completed &&
                            order.timeWindowStart != null &&
                            order.timeWindowEnd != null)
                          AppFormatters.timeWindow(
                            order.timeWindowStart,
                            order.timeWindowEnd,
                          ),
                      ].join(' · '),
                    ),
                  ],
                ),
              ),
              trailing: const Icon(Icons.chevron_right),
              onTap: () => DriverStopDetailSheet.show(
                context,
                order: order,
                sequenceNumber: ranks[order.id] == null
                    ? null
                    : ranks[order.id]! + 1,
              ),
            ),
          ],
        ],
      ),
    );
  }
}
