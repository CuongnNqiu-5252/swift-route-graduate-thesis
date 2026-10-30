import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/theme/app_colors.dart';
import 'package:swiftroute_app/features/driver/application/driver_provider.dart';
import 'package:swiftroute_app/features/driver/presentation/widgets/driver_stop_card.dart';
import 'package:swiftroute_app/features/order/data/models/order_model.dart';
import 'package:swiftroute_app/features/routing/data/models/optimization_result_model.dart';
import 'package:swiftroute_app/features/routing/presentation/screens/multi_stop_route_screen.dart';

enum _RouteFilter { all, planned, inTransit, delivered }

/// Today's Route Stops List Screen (D05)
class DriverRouteTab extends ConsumerStatefulWidget {
  const DriverRouteTab({super.key});

  @override
  ConsumerState<DriverRouteTab> createState() => _DriverRouteTabState();
}

class _DriverRouteTabState extends ConsumerState<DriverRouteTab> {
  _RouteFilter _filter = _RouteFilter.all;

  @override
  Widget build(BuildContext context) {
    final ordersState = ref.watch(driverOrdersProvider);

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        backgroundColor: Colors.white,
        elevation: 0,
        title: const Text(
          'Lộ trình giao hôm nay',
          style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: AppColors.textPrimary),
        ),
        actions: [
          IconButton(
            tooltip: 'Tải lại danh sách',
            onPressed: () => ref.read(driverOrdersProvider.notifier).refreshOrders(),
            icon: const Icon(Icons.refresh_rounded, color: AppColors.textPrimary),
          ),
        ],
      ),
      body: ordersState.when(
        loading: () => const _LoadingRouteList(),
        error: (err, _) => _RouteErrorView(
          error: err,
          onRetry: () => ref.read(driverOrdersProvider.notifier).refreshOrders(),
        ),
        data: (allStops) {
          if (allStops.isEmpty) {
            return RefreshIndicator(
              onRefresh: () => ref.read(driverOrdersProvider.notifier).refreshOrders(),
              child: const _EmptyRouteList(),
            );
          }

          final filteredStops = switch (_filter) {
            _RouteFilter.all => allStops,
            _RouteFilter.planned => allStops.where((o) => o.status == OrderStatus.planned).toList(),
            _RouteFilter.inTransit => allStops.where((o) => o.status == OrderStatus.inTransit).toList(),
            _RouteFilter.delivered => allStops.where((o) => o.status == OrderStatus.delivered).toList(),
          };

          final inTransitCount = allStops.where((o) => o.status == OrderStatus.inTransit).length;
          final plannedCount = allStops.where((o) => o.status == OrderStatus.planned).length;
          final deliveredCount = allStops.where((o) => o.status == OrderStatus.delivered).length;

          return RefreshIndicator(
            onRefresh: () => ref.read(driverOrdersProvider.notifier).refreshOrders(),
            child: ListView(
              physics: const AlwaysScrollableScrollPhysics(),
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 32),
              children: [
                // Top filter chips
                SingleChildScrollView(
                  scrollDirection: Axis.horizontal,
                  child: Row(
                    children: [
                      _FilterChip(
                        label: 'Tất cả (${allStops.length})',
                        isSelected: _filter == _RouteFilter.all,
                        onTap: () => setState(() => _filter = _RouteFilter.all),
                      ),
                      const SizedBox(width: 8),
                      _FilterChip(
                        label: 'Đang giao ($inTransitCount)',
                        isSelected: _filter == _RouteFilter.inTransit,
                        activeColor: AppColors.accent,
                        onTap: () => setState(() => _filter = _RouteFilter.inTransit),
                      ),
                      const SizedBox(width: 8),
                      _FilterChip(
                        label: 'Chờ giao ($plannedCount)',
                        isSelected: _filter == _RouteFilter.planned,
                        activeColor: Colors.blue.shade700,
                        onTap: () => setState(() => _filter = _RouteFilter.planned),
                      ),
                      const SizedBox(width: 8),
                      _FilterChip(
                        label: 'Đã giao ($deliveredCount)',
                        isSelected: _filter == _RouteFilter.delivered,
                        activeColor: AppColors.success,
                        onTap: () => setState(() => _filter = _RouteFilter.delivered),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 14),

                // AI Optimization Button
                ElevatedButton.icon(
                  onPressed: () {
                    final depot = LocationPointModel(
                      lat: allStops.first.pickupLat,
                      lng: allStops.first.pickupLng,
                      address: allStops.first.pickupAddress,
                    );
                    Navigator.of(context).push(
                      MaterialPageRoute(
                        builder: (_) => MultiStopRouteScreen(
                          orders: allStops,
                          depot: depot,
                        ),
                      ),
                    );
                  },
                  icon: const Icon(Icons.route_rounded, color: Colors.white, size: 20),
                  label: const Text(
                    'TỐI ƯU TOÀN BỘ TUYẾN ĐƯỜNG (AI / DP)',
                    style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13),
                  ),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppColors.primary,
                    foregroundColor: Colors.white,
                    padding: const EdgeInsets.symmetric(vertical: 12),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                  ),
                ),
                const SizedBox(height: 16),

                // Stop timeline cards
                if (filteredStops.isEmpty)
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: 40),
                    child: Center(
                      child: Text(
                        'Không có đơn hàng nào ở mục này',
                        style: TextStyle(color: AppColors.textSecondary.withValues(alpha: 0.8)),
                      ),
                    ),
                  )
                else
                  for (int i = 0; i < filteredStops.length; i++) ...[
                    DriverStopCard(
                      stop: filteredStops[i],
                      sequence: allStops.indexOf(filteredStops[i]) + 1,
                    ),
                    if (i < filteredStops.length - 1)
                      Padding(
                        padding: const EdgeInsets.only(left: 24),
                        child: Container(
                          width: 2,
                          height: 16,
                          color: AppColors.divider,
                        ),
                      ),
                  ],
              ],
            ),
          );
        },
      ),
    );
  }
}

class _FilterChip extends StatelessWidget {
  const _FilterChip({
    required this.label,
    required this.isSelected,
    required this.onTap,
    this.activeColor = AppColors.primary,
  });

  final String label;
  final bool isSelected;
  final VoidCallback onTap;
  final Color activeColor;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(20),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
        decoration: BoxDecoration(
          color: isSelected ? activeColor : AppColors.surface,
          borderRadius: BorderRadius.circular(20),
        ),
        child: Text(
          label,
          style: TextStyle(
            fontSize: 12,
            fontWeight: isSelected ? FontWeight.bold : FontWeight.w500,
            color: isSelected ? Colors.white : AppColors.textSecondary,
          ),
        ),
      ),
    );
  }
}

class _LoadingRouteList extends StatelessWidget {
  const _LoadingRouteList();

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: List.generate(
        4,
        (_) => Container(
          height: 120,
          margin: const EdgeInsets.only(bottom: 12),
          decoration: BoxDecoration(
            color: AppColors.surface,
            borderRadius: BorderRadius.circular(12),
          ),
        ),
      ),
    );
  }
}

class _EmptyRouteList extends StatelessWidget {
  const _EmptyRouteList();

  @override
  Widget build(BuildContext context) {
    return ListView(
      physics: const AlwaysScrollableScrollPhysics(),
      padding: const EdgeInsets.all(24),
      children: const [
        SizedBox(height: 100),
        Icon(Icons.route_outlined, size: 56, color: AppColors.textSecondary),
        SizedBox(height: 16),
        Center(
          child: Text(
            'Chưa có điểm giao được phân công',
            style: TextStyle(fontSize: 17, fontWeight: FontWeight.bold),
          ),
        ),
        SizedBox(height: 8),
        Center(
          child: Text(
            'Khi bộ phận điều phối phân công kế hoạch tối ưu, tuyến đường sẽ hiển thị ở đây.',
            textAlign: TextAlign.center,
            style: TextStyle(color: AppColors.textSecondary, fontSize: 13),
          ),
        ),
      ],
    );
  }
}

class _RouteErrorView extends StatelessWidget {
  const _RouteErrorView({required this.error, required this.onRetry});

  final Object error;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.cloud_off_rounded, size: 48, color: AppColors.textSecondary),
            const SizedBox(height: 12),
            const Text(
              'Không thể tải lộ trình hôm nay',
              style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 6),
            const Text(
              'Kiểm tra kết nối mạng hoặc thử làm mới lại.',
              textAlign: TextAlign.center,
              style: TextStyle(color: AppColors.textSecondary, fontSize: 13),
            ),
            const SizedBox(height: 16),
            FilledButton.icon(
              onPressed: onRetry,
              icon: const Icon(Icons.refresh_rounded, size: 18),
              label: const Text('Thử lại'),
            ),
          ],
        ),
      ),
    );
  }
}

