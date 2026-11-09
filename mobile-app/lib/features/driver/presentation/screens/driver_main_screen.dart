// features/driver/presentation/screens/driver_main_screen.dart

import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/theme/app_colors.dart';
import 'package:swiftroute_app/core/utils/location_helper.dart';
import 'package:swiftroute_app/core/widgets/field_ops_widgets.dart';
import 'package:swiftroute_app/features/driver/application/driver_provider.dart';
import 'package:swiftroute_app/features/driver/presentation/screens/driver_cod_handover_tab.dart';
import 'package:swiftroute_app/features/driver/presentation/screens/driver_earnings_tab.dart';
import 'package:swiftroute_app/features/driver/presentation/screens/driver_home_tab.dart';
import 'package:swiftroute_app/features/driver/presentation/screens/driver_profile_tab.dart';
import 'package:swiftroute_app/features/driver/presentation/screens/driver_route_tab.dart';
import 'package:swiftroute_app/features/order/data/models/order_model.dart';
import 'package:swiftroute_app/presentation/providers/auth_provider.dart';

/// Master 5-tab operational shell for SwiftRoute Driver:
/// Hôm nay · Lộ trình · Thu nhập · Bàn giao · Tài khoản
class DriverMainScreen extends ConsumerStatefulWidget {
  const DriverMainScreen({super.key});

  @override
  ConsumerState<DriverMainScreen> createState() => _DriverMainScreenState();
}

class _DriverMainScreenState extends ConsumerState<DriverMainScreen> {
  int _currentIndex = 0;
  Timer? _locationTimer;
  bool _locationRequestInFlight = false;
  int _locationFailures = 0;
  int _locationSequence = DateTime.now().millisecondsSinceEpoch;

  @override
  void initState() {
    super.initState();
    Future.microtask(() async {
      await ref.read(driverProvider.notifier).refreshProfile();
      await ref.read(driverOrdersProvider.notifier).refreshOrders();
      await _syncLocation();
    });
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

  @override
  void dispose() {
    _locationTimer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    ref.listen(authProvider, (previous, next) {
      if (!next.isAuthenticated) {
        Navigator.of(
          context,
        ).pushNamedAndRemoveUntil('/login', (route) => false);
      }
    });

    final ordersState = ref.watch(driverOrdersProvider);
    final codState = ref.watch(driverCodHandoverProvider);

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

    final pendingCodCount =
        codState.valueOrNull?.eligibleCollections.length ?? 0;

    return Scaffold(
      body: Column(
        children: [
          SafeArea(
            bottom: false,
            top: _locationFailures >= 3,
            child: GpsSyncBanner(
              hasLocationIssue: _locationFailures >= 3,
              onRetry: _syncLocation,
            ),
          ),
          Expanded(
            child: IndexedStack(
              index: _currentIndex,
              children: [
                DriverHomeTab(
                  onNavigateToRoute: () => setState(() => _currentIndex = 1),
                  onNavigateToEarnings: () => setState(() => _currentIndex = 2),
                  onNavigateToHandover: () => setState(() => _currentIndex = 3),
                  onNavigateToProfile: () => setState(() => _currentIndex = 4),
                ),
                const DriverRouteTab(),
                const DriverEarningsTab(),
                const DriverCodHandoverTab(),
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
          onDestinationSelected: (idx) => setState(() => _currentIndex = idx),
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
            NavigationDestination(
              icon: Badge(
                isLabelVisible: pendingCodCount > 0,
                backgroundColor: AppColors.accent,
                label: Text('$pendingCodCount'),
                child: const Icon(Icons.payments_outlined),
              ),
              selectedIcon: Badge(
                isLabelVisible: pendingCodCount > 0,
                backgroundColor: AppColors.accent,
                label: Text('$pendingCodCount'),
                child: const Icon(Icons.payments_rounded),
              ),
              label: 'Bàn giao',
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
