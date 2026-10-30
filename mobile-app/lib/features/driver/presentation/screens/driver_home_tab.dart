import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/theme/app_colors.dart';
import 'package:swiftroute_app/features/driver/application/driver_provider.dart';
import 'package:swiftroute_app/features/driver/presentation/widgets/driver_status_card.dart';
import 'package:swiftroute_app/features/driver/presentation/widgets/next_stop_spotlight_card.dart';
import 'package:swiftroute_app/features/driver/presentation/widgets/shift_stats_card.dart';
import 'package:swiftroute_app/features/routing/data/models/optimization_result_model.dart';
import 'package:swiftroute_app/features/routing/presentation/screens/multi_stop_route_screen.dart';
import 'package:swiftroute_app/presentation/providers/auth_provider.dart';

/// Driver Dashboard & Status Screen (D02)
class DriverHomeTab extends ConsumerWidget {
  const DriverHomeTab({super.key, required this.onNavigateToRoute});

  final VoidCallback onNavigateToRoute;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final driverState = ref.watch(driverProvider);
    final ordersState = ref.watch(driverOrdersProvider);
    final user = ref.watch(currentUserProvider);

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        backgroundColor: Colors.white,
        elevation: 0,
        titleSpacing: 16,
        title: Row(
          children: [
            CircleAvatar(
              radius: 18,
              backgroundColor: AppColors.primary,
              child: Text(
                (user?.name.isNotEmpty == true ? user!.name[0] : 'T').toUpperCase(),
                style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 16),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    user?.name ?? 'Tài xế SwiftRoute',
                    style: const TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: AppColors.textPrimary),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  const Text(
                    'SwiftRoute Express · Đội xe Quận 1',
                    style: TextStyle(fontSize: 11, color: AppColors.textSecondary),
                  ),
                ],
              ),
            ),
          ],
        ),
        actions: [
          IconButton(
            tooltip: 'Làm mới dữ liệu',
            onPressed: () {
              ref.read(driverProvider.notifier).refreshProfile();
              ref.read(driverOrdersProvider.notifier).refreshOrders();
            },
            icon: const Icon(Icons.refresh_rounded, color: AppColors.textPrimary),
          ),
          IconButton(
            tooltip: 'Đăng xuất',
            onPressed: () => ref.read(authProvider.notifier).logout(),
            icon: const Icon(Icons.logout_rounded, color: AppColors.textSecondary),
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () async {
          await Future.wait([
            ref.read(driverProvider.notifier).refreshProfile(),
            ref.read(driverOrdersProvider.notifier).refreshOrders(),
          ]);
        },
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 28),
          children: [
            // 1. Status Card (Online/Offline + GPS)
            driverState.when(
              data: (driver) => DriverStatusCard(driver: driver),
              loading: () => const _LoadingShimmer(height: 120),
              error: (_, _) => DriverStatusCard(driver: null),
            ),
            const SizedBox(height: 16),

            // 2. Next Stop Spotlight Card
            ordersState.when(
              data: (orders) => NextStopSpotlightCard(orders: orders),
              loading: () => const _LoadingShimmer(height: 180),
              error: (err, _) => Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(16),
                  border: Border.all(color: AppColors.divider),
                ),
                child: Column(
                  children: [
                    const Icon(Icons.cloud_off_rounded, color: AppColors.textSecondary, size: 32),
                    const SizedBox(height: 8),
                    const Text('Không tải được danh sách đơn hàng', style: TextStyle(fontWeight: FontWeight.bold)),
                    const SizedBox(height: 8),
                    TextButton(
                      onPressed: () => ref.read(driverOrdersProvider.notifier).refreshOrders(),
                      child: const Text('Thử lại'),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 16),

            // 3. Shift Statistics Overview
            ordersState.maybeWhen(
              data: (orders) => ShiftStatsCard(orders: orders),
              orElse: () => const SizedBox.shrink(),
            ),
            const SizedBox(height: 16),

            // 4. Quick Action Banners
            ordersState.maybeWhen(
              data: (orders) {
                if (orders.isEmpty) return const SizedBox.shrink();
                return Column(
                  children: [
                    // Button to open Route tab
                    OutlinedButton.icon(
                      onPressed: onNavigateToRoute,
                      icon: const Icon(Icons.format_list_bulleted_rounded, color: AppColors.primary),
                      label: Text('Xem toàn bộ lộ trình (${orders.length} điểm giao)'),
                      style: OutlinedButton.styleFrom(
                        padding: const EdgeInsets.symmetric(vertical: 14),
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                        side: const BorderSide(color: AppColors.primary, width: 1.2),
                      ),
                    ),
                    const SizedBox(height: 10),
                    // Button to trigger AI Optimization
                    ElevatedButton.icon(
                      onPressed: () {
                        final depot = LocationPointModel(
                          lat: orders.first.pickupLat,
                          lng: orders.first.pickupLng,
                          address: orders.first.pickupAddress,
                        );
                        Navigator.of(context).push(
                          MaterialPageRoute(
                            builder: (_) => MultiStopRouteScreen(
                              orders: orders,
                              depot: depot,
                            ),
                          ),
                        );
                      },
                      icon: const Icon(Icons.auto_awesome_rounded, color: Colors.white),
                      label: const Text(
                        'TỐI ƯU TOÀN TUYẾN BẰNG AI (HELD-KARP DP)',
                        style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13),
                      ),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: AppColors.primary,
                        foregroundColor: Colors.white,
                        padding: const EdgeInsets.symmetric(vertical: 14),
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                      ),
                    ),
                  ],
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

class _LoadingShimmer extends StatelessWidget {
  const _LoadingShimmer({required this.height});
  final double height;

  @override
  Widget build(BuildContext context) {
    return Container(
      height: height,
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(16),
      ),
    );
  }
}
