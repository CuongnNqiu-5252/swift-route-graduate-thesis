// features/driver/presentation/screens/driver_home_tab.dart

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/theme/app_colors.dart';
import 'package:swiftroute_app/core/widgets/field_ops_widgets.dart';
import 'package:swiftroute_app/features/driver/application/driver_provider.dart';
import 'package:swiftroute_app/features/driver/data/models/driver_notification_model.dart';
import 'package:swiftroute_app/features/driver/presentation/screens/driver_chat_screen.dart';
import 'package:swiftroute_app/features/driver/presentation/screens/driver_cod_screen.dart';
import 'package:swiftroute_app/features/driver/presentation/screens/driver_route_tab.dart';
import 'package:swiftroute_app/features/driver/presentation/widgets/driver_notifications_sheet.dart';
import 'package:swiftroute_app/features/driver/presentation/widgets/driver_status_card.dart';

/// Driver shift controls and today’s delivery schedule.
class DriverHomeTab extends ConsumerStatefulWidget {
  const DriverHomeTab({
    super.key,
    this.onNavigateToEarnings,
    this.onNavigateToProfile,
  });

  final VoidCallback? onNavigateToEarnings;
  final VoidCallback? onNavigateToProfile;

  @override
  ConsumerState<DriverHomeTab> createState() => _DriverHomeTabState();
}

class _DriverHomeTabState extends ConsumerState<DriverHomeTab>
    with SingleTickerProviderStateMixin {
  late final TabController _tabs = TabController(length: 2, vsync: this);

  @override
  void dispose() {
    _tabs.dispose();
    super.dispose();
  }

  Future<void> _refresh() async {
    await Future.wait([
      ref.read(driverProvider.notifier).refreshProfile(),
      ref.read(driverOrdersProvider.notifier).refreshOrders(),
      ref.read(driverDispatchPlansProvider.notifier).refresh(),
      ref.read(driverCodHandoverProvider.notifier).refresh(),
      ref.read(driverNotificationsProvider.notifier).refresh(),
    ]);
  }

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
        onNavigateToRoute: () => _tabs.animateTo(0),
        onOpenCod: _openCod,
        onNavigateToEarnings: widget.onNavigateToEarnings,
        onNavigateToProfile: widget.onNavigateToProfile,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final driverState = ref.watch(driverProvider);
    final notificationsState = ref.watch(driverNotificationsProvider);
    final readNotificationIds = ref.watch(driverReadNotificationIdsProvider);
    final notifications = notificationsState.valueOrNull ?? const [];
    final unreadNotificationsCount = notifications
        .where((n) => !readNotificationIds.contains(n.id))
        .length;

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        automaticallyImplyLeading: false,
        backgroundColor: AppColors.primaryDark,
        foregroundColor: Colors.white,
        titleTextStyle: Theme.of(
          context,
        ).appBarTheme.titleTextStyle?.copyWith(color: Colors.white),
        title: const Text('Đơn hàng'),
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
          const SizedBox(width: 4),
        ],
      ),
      body: Column(
        children: [
          ColoredBox(
            color: AppColors.primaryDark,
            child: Padding(
              padding: const EdgeInsets.fromLTRB(16, 4, 16, 16),
              child: driverState.when(
                data: (driver) => DriverStatusCard(driver: driver),
                loading: () => const ShimmerBox(height: 72),
                error: (_, _) => const DriverStatusCard(driver: null),
              ),
            ),
          ),
          Material(
            color: AppColors.card,
            child: TabBar(
              controller: _tabs,
              tabs: const [
                Tab(text: 'Cần làm'),
                Tab(text: 'Hoàn thành'),
              ],
            ),
          ),
          Expanded(
            child: TabBarView(
              controller: _tabs,
              children: [
                DriverRouteTab(completed: false, onRefresh: _refresh),
                DriverRouteTab(completed: true, onRefresh: _refresh),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
