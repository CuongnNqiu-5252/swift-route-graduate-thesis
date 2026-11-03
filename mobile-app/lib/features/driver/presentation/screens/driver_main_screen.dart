import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/theme/app_colors.dart';
import 'package:swiftroute_app/features/driver/application/driver_provider.dart';
import 'package:swiftroute_app/features/driver/presentation/screens/driver_home_tab.dart';
import 'package:swiftroute_app/features/driver/presentation/screens/driver_profile_tab.dart';
import 'package:swiftroute_app/features/driver/presentation/screens/driver_route_tab.dart';
import 'package:swiftroute_app/features/driver/presentation/screens/driver_cod_handover_tab.dart';
import 'package:swiftroute_app/features/driver/presentation/screens/driver_earnings_tab.dart';
import 'package:swiftroute_app/features/order/data/models/order_model.dart';
import 'package:swiftroute_app/presentation/providers/auth_provider.dart';
import 'package:swiftroute_app/core/utils/location_helper.dart';

/// The master shell screen for drivers after logging in.
/// Hosts daily execution, COD handover, and profile tabs.
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
    // Auto-fetch profile and assigned orders when the driver shell opens
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
      _locationFailures = 0;
    } catch (_) {
      _locationFailures++;
      if (mounted && _locationFailures == 3) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
              'GPS chưa đồng bộ. Hãy kiểm tra quyền vị trí và kết nối mạng.',
            ),
          ),
        );
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
    // Automatically redirect back to login if user becomes unauthenticated
    ref.listen(authProvider, (previous, next) {
      if (!next.isAuthenticated) {
        Navigator.of(
          context,
        ).pushNamedAndRemoveUntil('/login', (route) => false);
      }
    });

    final ordersState = ref.watch(driverOrdersProvider);
    final activeOrdersCount =
        ordersState.valueOrNull
            ?.where(
              (o) =>
                  o.deliveryTaskStatus != DeliveryTaskStatus.completed &&
                  o.deliveryTaskStatus != DeliveryTaskStatus.cancelled,
            )
            .length ??
        0;

    return Scaffold(
      body: IndexedStack(
        index: _currentIndex,
        children: [
          DriverHomeTab(
            onNavigateToRoute: () => setState(() => _currentIndex = 1),
          ),
          const DriverRouteTab(),
          const DriverEarningsTab(),
          const DriverCodHandoverTab(),
          const DriverProfileTab(),
        ],
      ),
      bottomNavigationBar: Container(
        decoration: const BoxDecoration(
          border: Border(top: BorderSide(color: AppColors.divider)),
        ),
        child: NavigationBar(
          selectedIndex: _currentIndex,
          backgroundColor: Colors.white,
          indicatorColor: AppColors.primary.withValues(alpha: 0.12),
          onDestinationSelected: (idx) => setState(() => _currentIndex = idx),
          destinations: [
            const NavigationDestination(
              icon: Icon(Icons.dashboard_outlined),
              selectedIcon: Icon(
                Icons.dashboard_rounded,
                color: AppColors.primary,
              ),
              label: 'Trang chủ',
            ),
            NavigationDestination(
              icon: Badge(
                isLabelVisible: activeOrdersCount > 0,
                label: Text('$activeOrdersCount'),
                backgroundColor: AppColors.accent,
                child: const Icon(Icons.alt_route_rounded),
              ),
              selectedIcon: Badge(
                isLabelVisible: activeOrdersCount > 0,
                label: Text('$activeOrdersCount'),
                backgroundColor: AppColors.accent,
                child: const Icon(
                  Icons.alt_route_rounded,
                  color: AppColors.primary,
                ),
              ),
              label: 'Lộ trình',
            ),
            const NavigationDestination(
              icon: Icon(Icons.account_balance_wallet_outlined),
              selectedIcon: Icon(
                Icons.account_balance_wallet_rounded,
                color: AppColors.primary,
              ),
              label: 'Thu nhập',
            ),
            const NavigationDestination(
              icon: Icon(Icons.payments_outlined),
              selectedIcon: Icon(
                Icons.payments_rounded,
                color: AppColors.primary,
              ),
              label: 'COD',
            ),
            const NavigationDestination(
              icon: Icon(Icons.badge_outlined),
              selectedIcon: Icon(Icons.badge_rounded, color: AppColors.primary),
              label: 'Hồ sơ & Ca',
            ),
          ],
        ),
      ),
    );
  }
}
