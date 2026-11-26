// features/driver/presentation/screens/driver_main_screen.dart

import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/offline/offline_conflict_sheet.dart';
import 'package:swiftroute_app/core/theme/app_colors.dart';
import 'package:swiftroute_app/core/offline/offline_sync_controller.dart';
import 'package:swiftroute_app/core/utils/location_helper.dart';
import 'package:swiftroute_app/core/widgets/field_ops_widgets.dart';
import 'package:swiftroute_app/features/driver/application/driver_provider.dart';
import 'package:swiftroute_app/features/driver/presentation/screens/driver_earnings_tab.dart';
import 'package:swiftroute_app/features/driver/presentation/screens/driver_home_tab.dart';
import 'package:swiftroute_app/features/driver/presentation/screens/driver_profile_tab.dart';
import 'package:swiftroute_app/features/driver/presentation/screens/driver_route_tab.dart';
import 'package:swiftroute_app/features/order/data/models/order_model.dart';
import 'package:swiftroute_app/presentation/providers/auth_provider.dart';

/// Operational shell for SwiftRoute Driver:
/// Hôm nay · Lộ trình · Thu nhập · Tài khoản
class DriverMainScreen extends ConsumerStatefulWidget {
  const DriverMainScreen({super.key});

  @override
  ConsumerState<DriverMainScreen> createState() => _DriverMainScreenState();
}

class _DriverMainScreenState extends ConsumerState<DriverMainScreen>
    with WidgetsBindingObserver {
  int _currentIndex = 0;
  Timer? _locationTimer;
  bool _locationRequestInFlight = false;
  int _locationFailures = 0;
  int _locationSequence = DateTime.now().millisecondsSinceEpoch;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    Future.microtask(() async {
      await ref.read(authProvider.notifier).getCurrentUser();
      await ref.read(driverProvider.notifier).refreshProfile();
      await ref.read(driverOrdersProvider.notifier).refreshOrders();
      await ref.read(offlineSyncProvider.notifier).synchronize();
      await _syncLocation();
    });
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed && mounted) {
      unawaited(ref.read(authProvider.notifier).getCurrentUser());
      unawaited(ref.read(driverOrdersProvider.notifier).refreshOrders());
      unawaited(ref.read(driverDispatchPlansProvider.notifier).refresh());
      unawaited(ref.read(driverCodHandoverProvider.notifier).refresh());
    }
  }

  void _selectTab(int index) {
    if (index == _currentIndex) return;
    setState(() => _currentIndex = index);
    if (index == 1) {
      unawaited(ref.read(driverOrdersProvider.notifier).refreshOrders());
    }
  }

  Future<void> _syncLocation() async {
    final driver = ref.read(driverProvider).valueOrNull;
    if (!mounted || _locationRequestInFlight) return;
    if (driver == null || !driver.isAvailable) {
      _scheduleNextLocationSync();
      return;
    }
    _locationRequestInFlight = true;
    try {
      final position = await LocationHelper.getCurrentPosition();
      if (position == null) throw StateError('Không lấy được vị trí');
      final nowSequence = DateTime.now().millisecondsSinceEpoch;
      _locationSequence = nowSequence > _locationSequence
          ? nowSequence
          : _locationSequence + 1;
      final ok = await ref
          .read(driverProvider.notifier)
          .reportLocation(
            lat: position.latitude,
            lng: position.longitude,
            accuracyM: position.accuracy < 0 ? 0 : position.accuracy,
            speedMps: position.speed < 0 ? 0 : position.speed,
            bearing: position.heading < 0 ? 0 : position.heading,
            recordedAt: position.timestamp,
            sequence: _locationSequence,
          );
      if (!ok) throw StateError('Không đồng bộ được vị trí');
      if (mounted && _locationFailures > 0) {
        setState(() => _locationFailures = 0);
      }
    } catch (_) {
      if (mounted) {
        setState(() => _locationFailures++);
      }
    } finally {
      _locationRequestInFlight = false;
      _scheduleNextLocationSync();
    }
  }

  void _scheduleNextLocationSync() {
    _locationTimer?.cancel();
    if (!mounted) return;
    final tasks =
        ref.read(driverOrdersProvider).valueOrNull ?? const <OrderModel>[];
    final hasActiveTask = tasks.any(
      (order) => switch (order.deliveryTaskStatus) {
        DeliveryTaskStatus.assigned ||
        DeliveryTaskStatus.accepted ||
        DeliveryTaskStatus.arrived ||
        DeliveryTaskStatus.pickedUp ||
        DeliveryTaskStatus.outForDelivery => true,
        _ => false,
      },
    );
    _locationTimer = Timer(
      hasActiveTask ? const Duration(seconds: 5) : const Duration(seconds: 30),
      _syncLocation,
    );
  }

  Future<void> _resolveSyncConflicts() async {
    final controller = ref.read(offlineSyncProvider.notifier);
    final conflicts = await controller.conflictedActions();
    if (!mounted || conflicts.isEmpty) return;
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      builder: (context) => FractionallySizedBox(
        heightFactor: 0.86,
        child: OfflineConflictSheet(
          conflicts: conflicts,
          onAcceptServerState: controller.acceptServerState,
          onEscalate: (actionId, note) =>
              controller.escalateConflict(actionId, driverNote: note),
        ),
      ),
    );
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _locationTimer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    ref.listen(authProvider, (previous, next) {
      if (!next.isAuthenticated && Navigator.of(context).canPop()) {
        Navigator.of(context).popUntil((route) => route.isFirst);
      }
    });

    final ordersState = ref.watch(driverOrdersProvider);
    final syncState = ref.watch(offlineSyncProvider);

    final activeOrdersCount =
        ordersState.valueOrNull
            ?.where(
              (o) =>
                  o.deliveryTaskStatus != DeliveryTaskStatus.completed &&
                  o.deliveryTaskStatus != DeliveryTaskStatus.cancelled &&
                  o.deliveryTaskStatus != DeliveryTaskStatus.failed,
            )
            .length ??
        0;

    return Scaffold(
      body: Column(
        children: [
          SafeArea(
            bottom: false,
            top: _locationFailures >= 3,
            child: GpsSyncBanner(
              hasLocationIssue: _locationFailures >= 3,
              pendingActions: syncState.pending,
              conflicts: syncState.conflicts,
              isSyncing: syncState.isSyncing,
              onResolveConflicts: _resolveSyncConflicts,
              onRetry: () {
                unawaited(ref.read(offlineSyncProvider.notifier).synchronize());
                unawaited(_syncLocation());
              },
            ),
          ),
          Expanded(
            child: IndexedStack(
              index: _currentIndex,
              children: [
                DriverHomeTab(
                  onNavigateToRoute: () => _selectTab(1),
                  onNavigateToEarnings: () => _selectTab(2),
                  onNavigateToProfile: () => _selectTab(3),
                ),
                const DriverRouteTab(),
                const DriverEarningsTab(),
                const DriverProfileTab(),
              ],
            ),
          ),
        ],
      ),
      bottomNavigationBar: Container(
        decoration: const BoxDecoration(
          border: Border(top: BorderSide(color: AppColors.divider)),
        ),
        child: NavigationBar(
          selectedIndex: _currentIndex,
          onDestinationSelected: _selectTab,
          destinations: [
            const NavigationDestination(
              icon: Icon(Icons.today_outlined),
              selectedIcon: Icon(Icons.today_rounded),
              label: 'Hôm nay',
            ),
            NavigationDestination(
              icon: Badge(
                isLabelVisible: activeOrdersCount > 0,
                backgroundColor: AppColors.primary,
                label: Text('$activeOrdersCount'),
                child: const Icon(Icons.alt_route_rounded),
              ),
              selectedIcon: Badge(
                isLabelVisible: activeOrdersCount > 0,
                backgroundColor: AppColors.primary,
                label: Text('$activeOrdersCount'),
                child: const Icon(Icons.alt_route_rounded),
              ),
              label: 'Lộ trình',
            ),
            const NavigationDestination(
              icon: Icon(Icons.receipt_long_outlined),
              selectedIcon: Icon(Icons.receipt_long_rounded),
              label: 'Thu nhập',
            ),
            const NavigationDestination(
              icon: Icon(Icons.person_outline_rounded),
              selectedIcon: Icon(Icons.person_rounded),
              label: 'Tài khoản',
            ),
          ],
        ),
      ),
    );
  }
}
