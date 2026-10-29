import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/theme/app_colors.dart';
import 'package:swiftroute_app/features/driver/presentation/screens/driver_navigation_screen.dart';
import 'package:swiftroute_app/features/order/data/datasources/order_remote_data_source.dart';
import 'package:swiftroute_app/features/order/data/models/order_model.dart';
import 'package:swiftroute_app/presentation/providers/auth_provider.dart';

final driverOrdersProvider = FutureProvider.autoDispose<List<OrderModel>>((ref) {
  return ref.read(orderRemoteDataSourceProvider).getAssignedDriverOrders();
});

/// The working route list for a signed-in driver.
///
/// The largest element answers the driver's first question: which delivery
/// should be handled next? A route stop can be moved only through the backend's
/// PLANNED -> IN_TRANSIT -> DELIVERED state machine.
class DriverOrdersScreen extends ConsumerWidget {
  const DriverOrdersScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final orders = ref.watch(driverOrdersProvider);
    final user = ref.watch(currentUserProvider);

    return Scaffold(
      appBar: AppBar(
        titleSpacing: 20,
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Tuyến giao hôm nay'),
            Text(
              user?.name ?? 'Tài xế SwiftRoute',
              style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w400),
            ),
          ],
        ),
        actions: [
          IconButton(
            tooltip: 'Tải lại tuyến giao',
            onPressed: () => ref.invalidate(driverOrdersProvider),
            icon: const Icon(Icons.refresh),
          ),
          IconButton(
            tooltip: 'Đăng xuất',
            onPressed: () => ref.read(authProvider.notifier).logout(),
            icon: const Icon(Icons.logout),
          ),
        ],
      ),
      body: orders.when(
        loading: () => const _LoadingRouteList(),
        error: (error, _) => _RouteLoadError(
          error: error,
          onRetry: () => ref.invalidate(driverOrdersProvider),
        ),
        data: (routeStops) => RefreshIndicator(
          onRefresh: () async => ref.refresh(driverOrdersProvider.future),
          child: routeStops.isEmpty
              ? const _EmptyRouteList()
              : _RouteList(stops: routeStops),
        ),
      ),
    );
  }
}

class _RouteList extends ConsumerWidget {
  const _RouteList({required this.stops});

  final List<OrderModel> stops;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final inTransitCount = stops.where((order) => order.status == OrderStatus.inTransit).length;

    return ListView(
      physics: const AlwaysScrollableScrollPhysics(),
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 28),
      children: [
        Text(
          '${stops.length} điểm giao trong tuyến',
          style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w700),
        ),
        const SizedBox(height: 4),
        Text(
          inTransitCount > 0
              ? '$inTransitCount đơn đang trên đường — hoàn tất điểm giao hiện tại trước.'
              : 'Bắt đầu từ điểm giao đầu tiên trong danh sách.',
          style: const TextStyle(color: AppColors.textSecondary),
        ),
        const SizedBox(height: 16),
        for (var index = 0; index < stops.length; index++) ...[
          _RouteStopCard(stop: stops[index], sequence: index + 1),
          if (index < stops.length - 1)
            const Padding(
              padding: EdgeInsets.only(left: 20),
              child: SizedBox(height: 16, child: VerticalDivider()),
            ),
        ],
      ],
    );
  }
}

class _RouteStopCard extends ConsumerStatefulWidget {
  const _RouteStopCard({required this.stop, required this.sequence});

  final OrderModel stop;
  final int sequence;

  @override
  ConsumerState<_RouteStopCard> createState() => _RouteStopCardState();
}

class _RouteStopCardState extends ConsumerState<_RouteStopCard> {
  var _isUpdating = false;

  Future<void> _advance() async {
    final nextStatus = widget.stop.status == OrderStatus.planned
        ? OrderStatus.inTransit
        : OrderStatus.delivered;
    setState(() => _isUpdating = true);

    try {
      await ref.read(orderRemoteDataSourceProvider).updateOrderStatus(
            orderId: widget.stop.id,
            status: nextStatus,
          );
      ref.invalidate(driverOrdersProvider);
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Không thể cập nhật đơn. Hãy thử lại.')),
        );
      }
    } finally {
      if (mounted) setState(() => _isUpdating = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final isOnTheWay = widget.stop.status == OrderStatus.inTransit;
    final statusColor = isOnTheWay ? AppColors.accent : AppColors.primary;
    final actionLabel = isOnTheWay ? 'Đã giao thành công' : 'Bắt đầu giao';
    final actionIcon = isOnTheWay ? Icons.check_circle_outline : Icons.two_wheeler;

    return Card(
      margin: EdgeInsets.zero,
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(8),
        side: const BorderSide(color: AppColors.divider),
      ),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Container(
                  width: 28,
                  height: 28,
                  alignment: Alignment.center,
                  decoration: const BoxDecoration(
                    color: AppColors.textPrimary,
                    shape: BoxShape.circle,
                  ),
                  child: Text(
                    '${widget.sequence}',
                    style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w700),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    'Đơn #${widget.stop.id.length > 8 ? widget.stop.id.substring(0, 8) : widget.stop.id}',
                    style: const TextStyle(fontWeight: FontWeight.w600),
                  ),
                ),
                _StatusPill(status: widget.stop.status, color: statusColor),
              ],
            ),
            const SizedBox(height: 16),
            const Text('Điểm giao', style: TextStyle(fontSize: 12, color: AppColors.textSecondary)),
            const SizedBox(height: 4),
            Text(widget.stop.deliveryAddress, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600)),
            const SizedBox(height: 6),
            Text(
              'Khung giờ: ${_formatTime(widget.stop.timeWindowStart)} – ${_formatTime(widget.stop.timeWindowEnd)} · ${widget.stop.weightKg.toStringAsFixed(1)} kg',
              style: const TextStyle(fontSize: 13, color: AppColors.textSecondary),
            ),
            const SizedBox(height: 16),
            SizedBox(
              width: double.infinity,
              height: 40,
              child: OutlinedButton.icon(
                onPressed: () => Navigator.of(context).push(
                  MaterialPageRoute(
                    builder: (_) => DriverNavigationScreen(order: widget.stop),
                  ),
                ),
                icon: const Icon(Icons.map_outlined, size: 18),
                label: const Text('Xem đường đi tới khách hàng'),
              ),
            ),
            const SizedBox(height: 8),
            SizedBox(
              width: double.infinity,
              height: 44,
              child: FilledButton.icon(
                onPressed: _isUpdating ? null : _advance,
                icon: _isUpdating
                    ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                    : Icon(actionIcon, size: 18),
                label: Text(actionLabel),
                style: FilledButton.styleFrom(backgroundColor: AppColors.textPrimary),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _StatusPill extends StatelessWidget {
  const _StatusPill({required this.status, required this.color});

  final OrderStatus status;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(color: color.withAlpha(24), borderRadius: BorderRadius.circular(4)),
      child: Text(status.displayName, style: TextStyle(fontSize: 11, fontWeight: FontWeight.w600, color: color)),
    );
  }
}

class _LoadingRouteList extends StatelessWidget {
  const _LoadingRouteList();

  @override
  Widget build(BuildContext context) => ListView(
        padding: const EdgeInsets.all(16),
        children: List.generate(
          3,
          (_) => Container(
            height: 168,
            margin: const EdgeInsets.only(bottom: 16),
            decoration: BoxDecoration(color: AppColors.surface, borderRadius: BorderRadius.circular(8)),
          ),
        ),
      );
}

class _EmptyRouteList extends StatelessWidget {
  const _EmptyRouteList();

  @override
  Widget build(BuildContext context) => ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.all(24),
        children: const [
          SizedBox(height: 120),
          Icon(Icons.route_outlined, size: 44, color: AppColors.textSecondary),
          SizedBox(height: 16),
          Center(child: Text('Chưa có điểm giao được phân công', style: TextStyle(fontSize: 17, fontWeight: FontWeight.w600))),
          SizedBox(height: 8),
          Center(child: Text('Khi điều phối duyệt kế hoạch, các điểm giao sẽ xuất hiện ở đây.', textAlign: TextAlign.center, style: TextStyle(color: AppColors.textSecondary))),
        ],
      );
}

class _RouteLoadError extends StatelessWidget {
  const _RouteLoadError({required this.error, required this.onRetry});

  final Object error;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) => Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(Icons.cloud_off_outlined, size: 40, color: AppColors.textSecondary),
              const SizedBox(height: 12),
              const Text('Không tải được tuyến giao', style: TextStyle(fontWeight: FontWeight.w600)),
              const SizedBox(height: 6),
              const Text('Kiểm tra kết nối đến máy chủ rồi thử lại.', textAlign: TextAlign.center, style: TextStyle(color: AppColors.textSecondary)),
              const SizedBox(height: 12),
              TextButton(onPressed: onRetry, child: const Text('Tải lại')),
            ],
          ),
        ),
      );
}

String _formatTime(DateTime dateTime) {
  final local = dateTime.toLocal();
  final hour = local.hour.toString().padLeft(2, '0');
  final minute = local.minute.toString().padLeft(2, '0');
  return '$hour:$minute';
}
