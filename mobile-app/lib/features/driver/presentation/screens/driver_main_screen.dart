import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/theme/app_colors.dart';
import 'package:swiftroute_app/features/driver/application/driver_provider.dart';
import 'package:swiftroute_app/features/driver/presentation/screens/driver_home_tab.dart';
import 'package:swiftroute_app/features/driver/presentation/screens/driver_profile_tab.dart';
import 'package:swiftroute_app/features/driver/presentation/screens/driver_route_tab.dart';
import 'package:swiftroute_app/features/order/data/models/order_model.dart';

/// The master shell screen for drivers after logging in.
/// Hosts Tab 0 (Trang chủ / D02), Tab 1 (Lộ trình / D05), and Tab 2 (Hồ sơ & Ca / D09).
class DriverMainScreen extends ConsumerStatefulWidget {
  const DriverMainScreen({super.key});

  @override
  ConsumerState<DriverMainScreen> createState() => _DriverMainScreenState();
}

class _DriverMainScreenState extends ConsumerState<DriverMainScreen> {
  int _currentIndex = 0;

  @override
  void initState() {
    super.initState();
    // Auto-fetch profile and assigned orders when the driver shell opens
    Future.microtask(() {
      ref.read(driverProvider.notifier).refreshProfile();
      ref.read(driverOrdersProvider.notifier).refreshOrders();
    });
  }

  @override
  Widget build(BuildContext context) {
    final ordersState = ref.watch(driverOrdersProvider);
    final activeOrdersCount = ordersState.valueOrNull
            ?.where((o) => o.status == OrderStatus.planned || o.status == OrderStatus.inTransit)
            .length ??
        0;

    return Scaffold(
      body: IndexedStack(
        index: _currentIndex,
        children: [
          DriverHomeTab(onNavigateToRoute: () => setState(() => _currentIndex = 1)),
          const DriverRouteTab(),
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
              selectedIcon: Icon(Icons.dashboard_rounded, color: AppColors.primary),
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
                child: const Icon(Icons.alt_route_rounded, color: AppColors.primary),
              ),
              label: 'Lộ trình',
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

