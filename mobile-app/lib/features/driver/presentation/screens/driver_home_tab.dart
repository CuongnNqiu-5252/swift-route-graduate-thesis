// features/driver/presentation/screens/driver_home_tab.dart

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/theme/app_colors.dart';
import 'package:swiftroute_app/core/theme/app_theme.dart';
import 'package:swiftroute_app/core/utils/formatters.dart';
import 'package:swiftroute_app/core/widgets/field_ops_widgets.dart';
import 'package:swiftroute_app/features/driver/presentation/screens/driver_chat_screen.dart';
import 'package:swiftroute_app/features/driver/application/driver_provider.dart';
import 'package:swiftroute_app/features/driver/data/models/dispatch_plan_model.dart';
import 'package:swiftroute_app/features/driver/data/models/driver_notification_model.dart';
import 'package:swiftroute_app/features/driver/presentation/screens/driver_cod_screen.dart';
import 'package:swiftroute_app/features/driver/presentation/screens/route_assignment_screen.dart';
import 'package:swiftroute_app/features/driver/presentation/widgets/driver_notifications_sheet.dart';
import 'package:swiftroute_app/features/driver/presentation/widgets/driver_status_card.dart';
import 'package:swiftroute_app/features/driver/presentation/widgets/next_stop_spotlight_card.dart';
import 'package:swiftroute_app/features/driver/presentation/widgets/shift_stats_card.dart';
import 'package:swiftroute_app/features/order/data/models/order_model.dart';
import 'package:swiftroute_app/presentation/providers/auth_provider.dart';

/// Driver Today Overview Tab ("Hôm nay").
/// Shows operational readiness and the immediate next stop.
class DriverHomeTab extends ConsumerStatefulWidget {
  const DriverHomeTab({
    super.key,
    required this.onNavigateToRoute,
    this.onNavigateToEarnings,
    this.onNavigateToProfile,
  });

  final VoidCallback onNavigateToRoute;
  final VoidCallback? onNavigateToEarnings;
  final VoidCallback? onNavigateToProfile;

  @override
  ConsumerState<DriverHomeTab> createState() => _DriverHomeTabState();
}

class _DriverHomeTabState extends ConsumerState<DriverHomeTab> {
  void _openCod() {
    Navigator.of(
      context,
    ).push(MaterialPageRoute<void>(builder: (_) => const DriverCodScreen()));
  }

  void _openNotificationsSheet(
    BuildContext context,
    List<DriverNotificationModel> notifications,
  ) {
    final ids = notifications.map((n) => n.id).toSet();
    if (ids.isNotEmpty) {
      ref.read(driverReadNotificationIdsProvider.notifier).markAllRead(ids);
    }

    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => DriverNotificationsSheet(
        onNavigateToRoute: widget.onNavigateToRoute,
        onOpenCod: _openCod,
        onNavigateToEarnings: widget.onNavigateToEarnings,
        onNavigateToProfile: widget.onNavigateToProfile,
      ),
    );
  }

  bool _planHasActiveStops(DispatchPlanModel plan, List<OrderModel> orders) {
    final stopIds = plan.orderedStopIds.toSet();
    final planOrders = stopIds.isEmpty
        ? orders
        : orders.where((o) => stopIds.contains(o.id)).toList();
    return planOrders.any((o) => o.isOutForDelivery);
  }

  @override
  Widget build(BuildContext context) {
    final driverState = ref.watch(driverProvider);
    final ordersState = ref.watch(driverOrdersProvider);
    final plansState = ref.watch(driverDispatchPlansProvider);
    final codState = ref.watch(driverCodHandoverProvider);
    final notificationsState = ref.watch(driverNotificationsProvider);
    final readNotificationIds = ref.watch(driverReadNotificationIdsProvider);
    final user = ref.watch(currentUserProvider);
    final driver = driverState.valueOrNull;

    final cod = codState.valueOrNull;
    final pendingCodTotal = cod?.availableTotal ?? 0;
    final hasCodToSubmit = cod?.eligibleCollections.isNotEmpty ?? false;
    final hasCodReceipts = cod?.recentHandovers.isNotEmpty ?? false;
    final notifications = notificationsState.valueOrNull ?? const [];
    final unreadNotificationsCount = notifications
        .where((n) => !readNotificationIds.contains(n.id))
        .length;

    final subtitleParts = <String>[
      if (driver?.vehicleType != null && driver!.vehicleType.isNotEmpty)
        driver.vehicleType,
      if (driver?.licensePlate != null && driver!.licensePlate.isNotEmpty)
        driver.licensePlate,
    ];

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        titleSpacing: 16,
        title: Row(
          children: [
            CircleAvatar(
              radius: 19,
              backgroundColor: AppColors.primary,
              child: Text(
                (user?.name.isNotEmpty == true ? user!.name[0] : 'T')
                    .toUpperCase(),
                style: const TextStyle(
                  color: Colors.white,
                  fontWeight: FontWeight.w800,
                  fontSize: 16,
                ),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    driver?.fullName.isNotEmpty == true
                        ? driver!.fullName
                        : (user?.name ?? 'Tài xế SwiftRoute'),
                    style: const TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w800,
                      color: AppColors.textPrimary,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  Text(
                    subtitleParts.isNotEmpty
                        ? subtitleParts.join(' · ')
                        : 'Ca vận hành giao nhận',
                    style: const TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.w500,
                      color: AppColors.textSecondary,
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
        actions: [
          IconButton(
            tooltip: 'Tin nhắn',
            onPressed: () => Navigator.of(context).push<void>(
              MaterialPageRoute(builder: (_) => const DriverChatScreen()),
            ),
            icon: const Icon(Icons.chat_bubble_outline_rounded),
          ),
          IconButton(
            tooltip: 'Hòm thư thông báo',
            onPressed: () => _openNotificationsSheet(context, notifications),
            icon: Badge(
              isLabelVisible: unreadNotificationsCount > 0,
              backgroundColor: AppColors.error,
              label: Text(
                unreadNotificationsCount > 9
                    ? '9+'
                    : '$unreadNotificationsCount',
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 10,
                  fontWeight: FontWeight.w700,
                ),
              ),
              child: const Icon(Icons.notifications_none_rounded),
            ),
          ),
          IconButton(
            tooltip: 'Làm mới dữ liệu',
            onPressed: () {
              ref.read(driverProvider.notifier).refreshProfile();
              ref.read(driverOrdersProvider.notifier).refreshOrders();
              ref.read(driverDispatchPlansProvider.notifier).refresh();
              ref.read(driverCodHandoverProvider.notifier).refresh();
              ref.read(driverNotificationsProvider.notifier).refresh();
            },
            icon: const Icon(Icons.refresh_rounded),
          ),
          const SizedBox(width: 4),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () async {
          await Future.wait([
            ref.read(driverProvider.notifier).refreshProfile(),
            ref.read(driverOrdersProvider.notifier).refreshOrders(),
            ref.read(driverDispatchPlansProvider.notifier).refresh(),
            ref.read(driverCodHandoverProvider.notifier).refresh(),
            ref.read(driverNotificationsProvider.notifier).refresh(),
          ]);
        },
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 28),
          children: [
            // 1. Shift Availability & GPS Readiness
            driverState.when(
              data: (d) => DriverStatusCard(driver: d),
              loading: () => const ShimmerBox(height: 92),
              error: (_, _) => const DriverStatusCard(driver: null),
            ),
            const SizedBox(height: 14),

            // 2. Assigned route summary
            plansState.maybeWhen(
              data: (plans) {
                final currentOrders = ordersState.valueOrNull ?? [];
                final activePlan = plans
                    .where((p) => _planHasActiveStops(p, currentOrders))
                    .firstOrNull;
                if (activePlan == null) return const SizedBox.shrink();
                return Padding(
                  padding: const EdgeInsets.only(bottom: 14),
                  child: Container(
                    padding: const EdgeInsets.all(16),
                    decoration: BoxDecoration(
                      color: AppColors.primarySoft,
                      borderRadius: BorderRadius.circular(14),
                      border: Border.all(color: AppColors.primaryBorder),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            const Icon(
                              Icons.route_rounded,
                              color: AppColors.primary,
                              size: 20,
                            ),
                            const SizedBox(width: 8),
                            Expanded(
                              child: Text(
                                'Lộ trình đang chạy gồm ${activePlan.orderCount} điểm giao',
                                style: const TextStyle(
                                  fontSize: 15,
                                  fontWeight: FontWeight.w800,
                                  color: AppColors.textPrimary,
                                ),
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 4),
                        Text(
                          'Tổng quãng đường ${activePlan.totalDistanceKm.toStringAsFixed(1)} km · Dự kiến ${activePlan.estimatedDurationMinutes} phút',
                          style: AppTheme.numStyle(
                            fontSize: 13,
                            fontWeight: FontWeight.w600,
                            color: AppColors.primaryDark,
                          ),
                        ),
                        const SizedBox(height: 12),
                        OutlinedButton(
                          onPressed: () => Navigator.of(context).push(
                            MaterialPageRoute<void>(
                              builder: (_) => RouteAssignmentScreen(
                                plan: activePlan,
                                orders: currentOrders,
                              ),
                            ),
                          ),
                          child: const Text('Xem chi tiết'),
                        ),
                      ],
                    ),
                  ),
                );
              },
              orElse: () => const SizedBox.shrink(),
            ),

            // 3. Dominant Next-Action Spotlight
            ordersState.when(
              data: (orders) => NextStopSpotlightCard(orders: orders),
              loading: () => const ShimmerBox(height: 210),
              error: (_, _) => EmptyStateCard(
                icon: Icons.cloud_off_rounded,
                title: 'Chưa tải được danh sách chuyến',
                message: 'Kiểm tra kết nối 4G/Wi-Fi rồi tải lại dữ liệu.',
                actionLabel: 'Thử lại',
                onAction: () =>
                    ref.read(driverOrdersProvider.notifier).refreshOrders(),
              ),
            ),
            const SizedBox(height: 14),

            // 4. Shift Progress & Cash-in-Hand Summary
            ordersState.maybeWhen(
              data: (orders) => ShiftStatsCard(
                orders: orders,
                pendingCodTotal: pendingCodTotal,
              ),
              orElse: () => const SizedBox.shrink(),
            ),

            if (hasCodToSubmit || hasCodReceipts || codState.hasError)
              Card(
                child: ListTile(
                  leading: const Icon(Icons.payments_outlined),
                  title: Text(hasCodToSubmit ? 'Nộp COD' : 'Xem phiếu nộp COD'),
                  subtitle: hasCodToSubmit
                      ? Text(AppFormatters.currency(pendingCodTotal))
                      : null,
                  trailing: const Icon(Icons.chevron_right),
                  onTap: _openCod,
                ),
              ),

            // 5. Direct Navigation to Full Route Timeline
            ordersState.maybeWhen(
              data: (orders) {
                if (orders.isEmpty) return const SizedBox.shrink();
                return Padding(
                  padding: const EdgeInsets.only(top: 14),
                  child: SizedBox(
                    width: double.infinity,
                    child: OutlinedButton.icon(
                      onPressed: widget.onNavigateToRoute,
                      icon: const Icon(
                        Icons.format_list_numbered_rounded,
                        color: AppColors.primary,
                      ),
                      label: Text(
                        'Xem toàn bộ lộ trình (${orders.length} điểm dừng)',
                        style: const TextStyle(color: AppColors.primary),
                      ),
                      style: OutlinedButton.styleFrom(
                        backgroundColor: AppColors.card,
                        minimumSize: const Size.fromHeight(50),
                      ),
                    ),
                  ),
                );
              },
              orElse: () => const SizedBox.shrink(),
            ),
          ],
        ),
      ),
    );
  }
}
