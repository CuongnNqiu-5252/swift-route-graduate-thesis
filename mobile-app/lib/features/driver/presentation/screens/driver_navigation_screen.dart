// features/driver/presentation/screens/driver_navigation_screen.dart

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:geolocator/geolocator.dart';
import 'package:swiftroute_app/core/theme/app_colors.dart';
import 'package:swiftroute_app/core/theme/app_theme.dart';
import 'package:swiftroute_app/core/utils/location_helper.dart';
import 'package:swiftroute_app/core/utils/polyline_decoder.dart';
import 'package:swiftroute_app/core/widgets/field_ops_widgets.dart';
import 'package:swiftroute_app/core/widgets/slide_to_confirm_button.dart';
import 'package:swiftroute_app/features/driver/application/driver_provider.dart';
import 'package:swiftroute_app/features/driver/presentation/widgets/driver_delivery_pod_sheet.dart';
import 'package:swiftroute_app/features/driver/presentation/widgets/driver_incident_sheet.dart';
import 'package:swiftroute_app/features/driver/presentation/widgets/driver_stop_detail_sheet.dart';
import 'package:swiftroute_app/features/order/data/datasources/goong_remote_data_source.dart';
import 'package:swiftroute_app/features/order/data/models/goong_direction_model.dart';
import 'package:swiftroute_app/features/order/data/models/order_model.dart';
import 'package:swiftroute_app/features/order/presentation/widgets/goong_map_widget.dart';

/// Zero-Switch Active Trip Cockpit.
/// Drivers stay on this single map screen from depot batch pickup through sequential customer stops.
class DriverNavigationScreen extends ConsumerStatefulWidget {
  const DriverNavigationScreen({super.key, required this.order});

  final OrderModel order;

  @override
  ConsumerState<DriverNavigationScreen> createState() =>
      _DriverNavigationScreenState();
}

class _DriverNavigationScreenState
    extends ConsumerState<DriverNavigationScreen> {
  late String _activeOrderId;
  String? _lastDestinationKey;

  GoongDirection? _direction;
  LatLngPoint? _driverPosition;
  double? _straightDistanceMeters;
  bool _isUsingPickupFallback = false;
  bool _isLoadingRoute = true;
  bool _mapReady = false;
  String? _routeError;
  MapFocusMode _focusMode = MapFocusMode.route;
  int _focusRevision = 0;

  @override
  void initState() {
    super.initState();
    _activeOrderId = widget.order.id;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _syncRouteForCurrentStop(force: true);
    });
  }

  /// Resolves the live order from [driverOrdersProvider], or advances automatically
  /// to the next active stop when the current stop finishes.
  OrderModel? _resolveActiveOrder(List<OrderModel> orders) {
    final activeStops = orders
        .where(
          (o) =>
              o.deliveryTaskStatus != DeliveryTaskStatus.completed &&
              o.deliveryTaskStatus != DeliveryTaskStatus.cancelled &&
              o.deliveryTaskStatus != DeliveryTaskStatus.failed,
        )
        .toList();

    final exact = activeStops.where((o) => o.id == _activeOrderId).firstOrNull;
    if (exact != null) return exact;

    // Auto-advance: prioritize remaining in-transit deliveries, then pending pickups, then returns
    final nextInTransit = activeStops
        .where((o) => o.isOutForDelivery)
        .firstOrNull;
    if (nextInTransit != null) return nextInTransit;

    final nextPickup = activeStops.where((o) => o.needsPickup).firstOrNull;
    if (nextPickup != null) return nextPickup;

    return activeStops.firstOrNull;
  }

  bool _isNavigatingToDepot(OrderModel order) =>
      order.isReturnTask || order.needsPickup;

  LatLngPoint _destinationFor(OrderModel order) {
    final toDepot = _isNavigatingToDepot(order);
    return LatLngPoint(
      toDepot ? order.pickupLat : order.deliveryLat,
      toDepot ? order.pickupLng : order.deliveryLng,
    );
  }

  Future<void> _syncRouteForCurrentStop({bool force = false}) async {
    final orders = ref.read(driverOrdersProvider).valueOrNull ?? [widget.order];
    final current = _resolveActiveOrder(orders);
    if (current == null) return;

    final dest = _destinationFor(current);
    final destKey =
        '${current.id}:${_isNavigatingToDepot(current)}:${dest.latitude.toStringAsFixed(5)},${dest.longitude.toStringAsFixed(5)}';
    if (!force && _lastDestinationKey == destKey) return;
    _lastDestinationKey = destKey;
    _activeOrderId = current.id;

    if (mounted) {
      setState(() {
        _isLoadingRoute = true;
        _routeError = null;
      });
    }

    try {
      final position = await LocationHelper.getCurrentPosition();
      final driverPos = position == null
          ? LatLngPoint(current.pickupLat, current.pickupLng)
          : LatLngPoint(position.latitude, position.longitude);

      final meters = Geolocator.distanceBetween(
        driverPos.latitude,
        driverPos.longitude,
        dest.latitude,
        dest.longitude,
      );

      final direction = await ref
          .read(goongRemoteDataSourceProvider)
          .getDirections(
            originLat: driverPos.latitude,
            originLng: driverPos.longitude,
            destLat: dest.latitude,
            destLng: dest.longitude,
            vehicle: 'bike',
          );

      if (!mounted) return;
      setState(() {
        _driverPosition = driverPos;
        _straightDistanceMeters = position == null ? null : meters;
        _direction = direction;
        _isUsingPickupFallback = position == null;
        _isLoadingRoute = false;
        _focusMode = MapFocusMode.route;
        _focusRevision++;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _isLoadingRoute = false;
        _routeError =
            'Không tải được đường đi. Kiểm tra mạng hoặc quyền vị trí rồi thử lại.';
      });
    }
  }

  Future<void> _handleBatchPickup(List<OrderModel> depotGroup) async {
    try {
      await ref
          .read(driverOrdersProvider.notifier)
          .confirmBatchPickup(orderIds: depotGroup.map((o) => o.id).toList());
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          backgroundColor: AppColors.success,
          content: Text(
            'Đã nhận ${depotGroup.length} đơn tại kho. Chuyển sang dẫn đường giao khách!',
          ),
        ),
      );
      await _syncRouteForCurrentStop(force: true);
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          backgroundColor: AppColors.error,
          content: Text('Không xác nhận được lấy hàng: $e'),
        ),
      );
    }
  }

  Future<void> _handleCompleteDelivery(OrderModel current) async {
    final completed = await DriverDeliveryPodSheet.show(context, current);
    if (!completed || !mounted) return;

    final remaining = (ref.read(driverOrdersProvider).valueOrNull ?? [])
        .where(
          (o) =>
              o.deliveryTaskStatus != DeliveryTaskStatus.completed &&
              o.deliveryTaskStatus != DeliveryTaskStatus.cancelled &&
              o.deliveryTaskStatus != DeliveryTaskStatus.failed,
        )
        .toList();

    if (remaining.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          backgroundColor: AppColors.success,
          content: Text('Hoàn thành toàn bộ điểm dừng trong chuyến!'),
        ),
      );
      Navigator.of(context).pop();
      return;
    }

    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        backgroundColor: AppColors.primary,
        content: Text(
          'Đã giao xong ${current.displayCode}. Tự động chuyển sang điểm tiếp theo (${remaining.length} điểm còn lại).',
        ),
      ),
    );
    await _syncRouteForCurrentStop(force: true);
  }

  Future<void> _handleReturnSwipe(OrderModel current) async {
    final taskId = current.deliveryTaskId;
    if (taskId == null) return;
    final status = current.deliveryTaskStatus ?? DeliveryTaskStatus.assigned;
    try {
      if (status == DeliveryTaskStatus.assigned ||
          status == DeliveryTaskStatus.accepted) {
        await ref
            .read(driverOrdersProvider.notifier)
            .confirmBatchPickup(
              orderIds: [current.id],
              notes: 'Bắt đầu hoàn kiện về kho',
            );
      } else {
        await ref
            .read(driverOrdersProvider.notifier)
            .updateDeliveryTask(
              orderId: current.id,
              taskId: taskId,
              nextStatus: DeliveryTaskStatus.completed,
              notes: 'Đã bàn giao kiện hoàn cho kho',
            );
      }
      if (!mounted) return;
      await _syncRouteForCurrentStop(force: true);
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          backgroundColor: AppColors.error,
          content: Text('Không cập nhật được đơn hoàn: $e'),
        ),
      );
    }
  }

  void _focus(MapFocusMode mode) {
    setState(() {
      _focusMode = mode;
      _focusRevision++;
    });
  }

  @override
  Widget build(BuildContext context) {
    final ordersAsync = ref.watch(driverOrdersProvider);
    final allOrders = ordersAsync.valueOrNull ?? [widget.order];
    final current = _resolveActiveOrder(allOrders);

    // If all stops finished externally, show clean completion card
    if (current == null) {
      return Scaffold(
        backgroundColor: AppColors.background,
        appBar: AppBar(title: const Text('Lộ trình đã hoàn tất')),
        body: Center(
          child: Padding(
            padding: const EdgeInsets.all(20),
            child: EmptyStateCard(
              icon: Icons.check_circle_rounded,
              title: 'Đã hoàn thành mọi điểm dừng!',
              message:
                  'Tất cả đơn trong chuyến hiện tại đã được xử lý. Kiểm tra tab Bàn giao nếu bạn có thu tiền mặt COD.',
              actionLabel: 'Quay về lộ trình',
              onAction: () => Navigator.of(context).pop(),
            ),
          ),
        ),
      );
    }

    // Trigger route refresh seamlessly when active order or phase changes
    final dest = _destinationFor(current);
    final destKey =
        '${current.id}:${_isNavigatingToDepot(current)}:${dest.latitude.toStringAsFixed(5)},${dest.longitude.toStringAsFixed(5)}';
    if (_lastDestinationKey != null && _lastDestinationKey != destKey) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        _syncRouteForCurrentStop(force: true);
      });
    }

    final toDepot = _isNavigatingToDepot(current);
    final depotGroup = toDepot && !current.isReturnTask
        ? allOrders
              .where(
                (o) =>
                    o.needsPickup && o.pickupGroupKey == current.pickupGroupKey,
              )
              .toList()
        : <OrderModel>[current];

    final activeRemainingCount = allOrders
        .where(
          (o) =>
              o.deliveryTaskStatus != DeliveryTaskStatus.completed &&
              o.deliveryTaskStatus != DeliveryTaskStatus.cancelled &&
              o.deliveryTaskStatus != DeliveryTaskStatus.failed,
        )
        .length;

    final isNearStop =
        _straightDistanceMeters != null && _straightDistanceMeters! <= 120;
    final bottomInset = MediaQuery.paddingOf(context).bottom;

    return Scaffold(
      backgroundColor: const Color(0xFFE2E8F0),
      body: Stack(
        fit: StackFit.expand,
        children: [
          GoongMapWidget(
            pickupLocation: toDepot ? dest : null,
            deliveryLocation: toDepot ? null : dest,
            driverLocation: _driverPosition,
            routePoints: _direction?.points ?? const [],
            focusMode: _focusMode,
            focusRevision: _focusRevision,
            onMapReady: () {
              if (mounted) setState(() => _mapReady = true);
            },
          ),
          if (!_mapReady)
            const Positioned.fill(
              child: ColoredBox(
                color: Color(0xFFE2E8F0),
                child: Center(child: CircularProgressIndicator()),
              ),
            ),

          // Top Navigation Header
          SafeArea(
            bottom: false,
            child: Padding(
              padding: const EdgeInsets.fromLTRB(12, 8, 12, 0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      _FloatingMapIconButton(
                        icon: Icons.arrow_back_rounded,
                        tooltip: 'Quay lại danh sách',
                        onPressed: () => Navigator.of(context).pop(),
                      ),
                      const SizedBox(width: 8),
                      Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 11,
                          vertical: 9,
                        ),
                        decoration: BoxDecoration(
                          color: AppColors.card,
                          borderRadius: BorderRadius.circular(12),
                          boxShadow: const [
                            BoxShadow(
                              color: Color(0x220F172A),
                              blurRadius: 12,
                              offset: Offset(0, 4),
                            ),
                          ],
                        ),
                        child: Text(
                          'Còn $activeRemainingCount điểm',
                          style: AppTheme.numStyle(
                            fontSize: 12.5,
                            fontWeight: FontWeight.w800,
                            color: AppColors.primary,
                          ),
                        ),
                      ),
                      const Spacer(),
                      _GpsStatusPill(
                        isLoading: _isLoadingRoute,
                        isFallback: _isUsingPickupFallback,
                      ),
                    ],
                  ),
                  const SizedBox(height: 10),
                  _TopTurnBanner(
                    toDepot: toDepot,
                    isReturn: current.isReturnTask,
                    title: toDepot
                        ? (current.isReturnTask
                              ? 'Hoàn kiện về ${current.pickupDisplayName}'
                              : 'Đến lấy hàng · ${current.pickupDisplayName}')
                        : 'Điểm giao · ${current.recipientName ?? current.displayCode}',
                    subtitle: toDepot
                        ? current.pickupAddress
                        : current.deliveryAddress,
                    distanceText: _direction?.distanceText,
                  ),
                ],
              ),
            ),
          ),

          // Right-hand Floating Map Camera Controls
          Positioned(
            right: 12,
            bottom: 288 + bottomInset,
            child: Column(
              children: [
                _FloatingMapIconButton(
                  icon: Icons.my_location_rounded,
                  tooltip: 'Vị trí của tôi',
                  selected: _focusMode == MapFocusMode.driver,
                  onPressed: () => _focus(MapFocusMode.driver),
                ),
                const SizedBox(height: 8),
                _FloatingMapIconButton(
                  icon: Icons.route_rounded,
                  tooltip: 'Toàn tuyến',
                  selected: _focusMode == MapFocusMode.route,
                  onPressed: () => _focus(MapFocusMode.route),
                ),
              ],
            ),
          ),

          // Bottom Zero-Switch Action Cockpit
          Align(
            alignment: Alignment.bottomCenter,
            child: _BottomTripCockpit(
              order: current,
              depotGroup: depotGroup,
              toDepot: toDepot,
              isNearStop: isNearStop,
              direction: _direction,
              isLoadingRoute: _isLoadingRoute,
              isFallback: _isUsingPickupFallback,
              routeError: _routeError,
              bottomInset: bottomInset,
              onRefreshRoute: () => _syncRouteForCurrentStop(force: true),
              onOpenDetails: () => DriverStopDetailSheet.show(
                context,
                order: current,
                sequenceNumber: 1,
              ),
              onSwipePickup: () => _handleBatchPickup(depotGroup),
              onCompleteDelivery: () => _handleCompleteDelivery(current),
              onReportIncident: () async {
                await DriverIncidentSheet.show(context, current);
                if (mounted) await _syncRouteForCurrentStop(force: true);
              },
              onSwipeReturn: () => _handleReturnSwipe(current),
            ),
          ),
        ],
      ),
    );
  }
}

class _TopTurnBanner extends StatelessWidget {
  const _TopTurnBanner({
    required this.toDepot,
    required this.isReturn,
    required this.title,
    required this.subtitle,
    required this.distanceText,
  });

  final bool toDepot;
  final bool isReturn;
  final String title;
  final String subtitle;
  final String? distanceText;

  @override
  Widget build(BuildContext context) {
    final bgColor = isReturn
        ? AppColors.errorDark
        : (toDepot ? AppColors.primaryDark : AppColors.primary);

    return Container(
      constraints: const BoxConstraints(maxWidth: 560),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: bgColor,
        borderRadius: BorderRadius.circular(16),
        boxShadow: const [
          BoxShadow(
            color: Color(0x330F172A),
            blurRadius: 18,
            offset: Offset(0, 6),
          ),
        ],
      ),
      child: Row(
        children: [
          Container(
            width: 44,
            height: 44,
            decoration: BoxDecoration(
              color: Colors.white.withValues(alpha: 0.16),
              borderRadius: BorderRadius.circular(12),
            ),
            child: Icon(
              isReturn
                  ? Icons.assignment_return_rounded
                  : (toDepot
                        ? Icons.warehouse_rounded
                        : Icons.location_on_rounded),
              color: Colors.white,
              size: 24,
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 15.5,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  subtitle,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    color: Colors.white.withValues(alpha: 0.85),
                    fontSize: 12.5,
                  ),
                ),
              ],
            ),
          ),
          if (distanceText != null) ...[
            const SizedBox(width: 10),
            Text(
              distanceText!,
              style: AppTheme.numStyle(
                fontSize: 15,
                fontWeight: FontWeight.w800,
                color: Colors.white,
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class _BottomTripCockpit extends StatelessWidget {
  const _BottomTripCockpit({
    required this.order,
    required this.depotGroup,
    required this.toDepot,
    required this.isNearStop,
    required this.direction,
    required this.isLoadingRoute,
    required this.isFallback,
    required this.routeError,
    required this.bottomInset,
    required this.onRefreshRoute,
    required this.onOpenDetails,
    required this.onSwipePickup,
    required this.onCompleteDelivery,
    required this.onReportIncident,
    required this.onSwipeReturn,
  });

  final OrderModel order;
  final List<OrderModel> depotGroup;
  final bool toDepot;
  final bool isNearStop;
  final GoongDirection? direction;
  final bool isLoadingRoute;
  final bool isFallback;
  final String? routeError;
  final double bottomInset;
  final VoidCallback onRefreshRoute;
  final VoidCallback onOpenDetails;
  final Future<void> Function() onSwipePickup;
  final VoidCallback onCompleteDelivery;
  final VoidCallback onReportIncident;
  final Future<void> Function() onSwipeReturn;

  @override
  Widget build(BuildContext context) {
    final totalPackages = depotGroup.fold<int>(
      0,
      (sum, item) => sum + item.packageCount,
    );
    final contactPhone = toDepot ? order.senderPhone : order.recipientPhone;

    return Container(
      width: double.infinity,
      constraints: const BoxConstraints(maxWidth: 720),
      padding: EdgeInsets.fromLTRB(18, 14, 18, 16 + bottomInset),
      decoration: const BoxDecoration(
        color: AppColors.card,
        borderRadius: BorderRadius.vertical(top: Radius.circular(22)),
        boxShadow: [
          BoxShadow(
            color: Color(0x280F172A),
            blurRadius: 24,
            offset: Offset(0, -8),
          ),
        ],
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Center(
            child: Container(
              width: 36,
              height: 4,
              decoration: BoxDecoration(
                color: AppColors.surfaceVariant,
                borderRadius: BorderRadius.circular(99),
              ),
            ),
          ),
          const SizedBox(height: 12),

          // Header row: Badges + Quick Actions (Call / Details / Refresh)
          Row(
            children: [
              if (isNearStop) ...[
                const StatusPill(
                  label: 'Đã tới gần điểm dừng',
                  foreground: AppColors.success,
                  background: AppColors.successSoft,
                  borderColor: AppColors.successBorder,
                  icon: Icons.near_me_rounded,
                ),
                const SizedBox(width: 6),
              ],
              if (!toDepot)
                CodBadge(amount: order.codAmount)
              else
                StatusPill(
                  label: order.isReturnTask
                      ? 'Trả kiện hoàn về kho'
                      : '${depotGroup.length} đơn · $totalPackages kiện',
                  foreground: AppColors.warning,
                  background: AppColors.warningSoft,
                  borderColor: AppColors.warningBorder,
                  icon: Icons.inventory_2_outlined,
                ),
              const Spacer(),
              IconButton(
                tooltip: 'Chi tiết vận đơn',
                onPressed: onOpenDetails,
                icon: const Icon(Icons.info_outline_rounded, size: 22),
              ),
              IconButton(
                tooltip: 'Làm mới tuyến đường',
                onPressed: isLoadingRoute ? null : onRefreshRoute,
                icon: isLoadingRoute
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.refresh_rounded, size: 22),
              ),
              PhoneCallButton(phone: contactPhone, compact: true),
            ],
          ),
          const SizedBox(height: 8),

          // Primary stop address & customer/depot name
          Text(
            toDepot
                ? '${order.pickupDisplayName} — ${order.pickupAddress}'
                : (order.recipientName?.isNotEmpty == true
                      ? '${order.recipientName} · ${order.deliveryAddress}'
                      : order.deliveryAddress),
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(
              fontSize: 16,
              height: 1.3,
              fontWeight: FontWeight.w800,
              color: AppColors.textPrimary,
            ),
          ),

          if (!toDepot &&
              order.notes != null &&
              order.notes!.trim().isNotEmpty) ...[
            const SizedBox(height: 6),
            Text(
              'Ghi chú: ${order.notes!.trim()}',
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                fontSize: 12.5,
                fontWeight: FontWeight.w600,
                color: AppColors.warning,
              ),
            ),
          ],

          const SizedBox(height: 10),

          // Distance & ETA compact strip
          if (routeError != null)
            Padding(
              padding: const EdgeInsets.only(bottom: 10),
              child: Text(
                routeError!,
                style: const TextStyle(
                  fontSize: 12.5,
                  color: AppColors.error,
                  fontWeight: FontWeight.w600,
                ),
              ),
            )
          else
            Padding(
              padding: const EdgeInsets.only(bottom: 12),
              child: Row(
                children: [
                  const Icon(
                    Icons.route_rounded,
                    size: 17,
                    color: AppColors.primary,
                  ),
                  const SizedBox(width: 5),
                  Text(
                    direction?.distanceText ?? 'Đang tính…',
                    style: AppTheme.numStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                  const SizedBox(width: 16),
                  const Icon(
                    Icons.schedule_rounded,
                    size: 17,
                    color: AppColors.textSecondary,
                  ),
                  const SizedBox(width: 5),
                  Text(
                    direction?.durationText ?? 'Đang tính…',
                    style: AppTheme.numStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.w700,
                      color: AppColors.textSecondary,
                    ),
                  ),
                  if (isFallback) ...[
                    const SizedBox(width: 10),
                    const Expanded(
                      child: Text(
                        'Bật GPS để định vị chính xác',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          fontSize: 11.5,
                          color: AppColors.warning,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ),
                  ],
                ],
              ),
            ),

          // Zero-Switch Primary Operational CTA
          if (order.isReturnTask)
            SlideToConfirmButton(
              label:
                  order.deliveryTaskStatus == DeliveryTaskStatus.outForDelivery
                  ? 'Vuốt bàn giao kiện hoàn cho Kho'
                  : 'Vuốt bắt đầu mang kiện về Kho',
              backgroundColor: AppColors.primary,
              icon: Icons.assignment_return_rounded,
              onConfirmed: onSwipeReturn,
            )
          else if (toDepot)
            SlideToConfirmButton(
              label: depotGroup.length > 1
                  ? 'Vuốt đã lấy $totalPackages kiện (${depotGroup.length} đơn)'
                  : 'Vuốt xác nhận Đã lấy hàng',
              subtitle: 'Tự động chuyển sang dẫn đường giao khách',
              backgroundColor: AppColors.accent,
              icon: Icons.inventory_2_rounded,
              onConfirmed: onSwipePickup,
            )
          else
            Row(
              children: [
                Expanded(
                  flex: 4,
                  child: SizedBox(
                    height: 54,
                    child: OutlinedButton.icon(
                      onPressed: onReportIncident,
                      style: OutlinedButton.styleFrom(
                        foregroundColor: AppColors.error,
                        side: const BorderSide(
                          color: AppColors.errorBorder,
                          width: 1.5,
                        ),
                        backgroundColor: AppColors.errorSoft,
                      ),
                      icon: const Icon(Icons.report_problem_rounded, size: 19),
                      label: const Text('Sự cố'),
                    ),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  flex: 7,
                  child: SizedBox(
                    height: 54,
                    child: FilledButton.icon(
                      onPressed: onCompleteDelivery,
                      style: FilledButton.styleFrom(
                        backgroundColor: AppColors.success,
                      ),
                      icon: const Icon(Icons.check_circle_rounded, size: 20),
                      label: const Text('Giao thành công'),
                    ),
                  ),
                ),
              ],
            ),
        ],
      ),
    );
  }
}

class _GpsStatusPill extends StatelessWidget {
  const _GpsStatusPill({required this.isLoading, required this.isFallback});

  final bool isLoading;
  final bool isFallback;

  @override
  Widget build(BuildContext context) {
    final dotColor = isFallback ? AppColors.accent : AppColors.successBright;
    final label = isLoading
        ? 'Đang định vị'
        : (isFallback ? 'Chưa bật GPS' : 'GPS trực tiếp');

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 11, vertical: 9),
      decoration: BoxDecoration(
        color: AppColors.card,
        borderRadius: BorderRadius.circular(12),
        boxShadow: const [
          BoxShadow(
            color: Color(0x220F172A),
            blurRadius: 12,
            offset: Offset(0, 4),
          ),
        ],
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 8,
            height: 8,
            decoration: BoxDecoration(color: dotColor, shape: BoxShape.circle),
          ),
          const SizedBox(width: 7),
          Text(
            label,
            style: const TextStyle(
              fontSize: 12,
              fontWeight: FontWeight.w700,
              color: AppColors.textPrimary,
            ),
          ),
        ],
      ),
    );
  }
}

class _FloatingMapIconButton extends StatelessWidget {
  const _FloatingMapIconButton({
    required this.icon,
    required this.tooltip,
    required this.onPressed,
    this.selected = false,
  });

  final IconData icon;
  final String tooltip;
  final VoidCallback onPressed;
  final bool selected;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: selected ? AppColors.primary : AppColors.card,
      elevation: 4,
      shadowColor: const Color(0x330F172A),
      borderRadius: BorderRadius.circular(12),
      child: SizedBox(
        width: 48,
        height: 48,
        child: IconButton(
          tooltip: tooltip,
          onPressed: onPressed,
          icon: Icon(
            icon,
            color: selected ? Colors.white : AppColors.textPrimary,
          ),
        ),
      ),
    );
  }
}
