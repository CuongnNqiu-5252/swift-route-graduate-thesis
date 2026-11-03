import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/theme/app_colors.dart';
import 'package:swiftroute_app/core/utils/location_helper.dart';
import 'package:swiftroute_app/core/utils/polyline_decoder.dart';
import 'package:swiftroute_app/features/order/data/datasources/goong_remote_data_source.dart';
import 'package:swiftroute_app/features/order/data/models/goong_direction_model.dart';
import 'package:swiftroute_app/features/order/data/models/order_model.dart';
import 'package:swiftroute_app/features/order/presentation/widgets/goong_map_widget.dart';

class DriverNavigationScreen extends ConsumerStatefulWidget {
  const DriverNavigationScreen({super.key, required this.order});

  final OrderModel order;

  @override
  ConsumerState<DriverNavigationScreen> createState() =>
      _DriverNavigationScreenState();
}

class _DriverNavigationScreenState
    extends ConsumerState<DriverNavigationScreen> {
  GoongDirection? _direction;
  LatLngPoint? _driverPosition;
  bool _isUsingPickupFallback = false;
  bool _isLoading = true;
  bool _mapReady = false;
  String? _error;
  MapFocusMode _focusMode = MapFocusMode.route;
  int _focusRevision = 0;
  DateTime? _lastUpdatedAt;

  bool get _navigatingToPickup =>
      widget.order.deliveryTaskType == 'RETURN' ||
      (widget.order.deliveryTaskStatus ?? DeliveryTaskStatus.assigned).index <
          DeliveryTaskStatus.pickedUp.index;

  LatLngPoint get _destination => LatLngPoint(
    _navigatingToPickup ? widget.order.pickupLat : widget.order.deliveryLat,
    _navigatingToPickup ? widget.order.pickupLng : widget.order.deliveryLng,
  );

  String get _destinationAddress => _navigatingToPickup
      ? widget.order.pickupAddress
      : widget.order.deliveryAddress;

  @override
  void initState() {
    super.initState();
    _loadRoute();
  }

  Future<void> _loadRoute() async {
    if (mounted) {
      setState(() {
        _isLoading = true;
        _error = null;
      });
    }
    try {
      final position = await LocationHelper.getCurrentPosition();
      final driverPosition = position == null
          ? LatLngPoint(widget.order.pickupLat, widget.order.pickupLng)
          : LatLngPoint(position.latitude, position.longitude);
      final direction = await ref
          .read(goongRemoteDataSourceProvider)
          .getDirections(
            originLat: driverPosition.latitude,
            originLng: driverPosition.longitude,
            destLat: _destination.latitude,
            destLng: _destination.longitude,
            vehicle: 'bike',
          );
      if (!mounted) return;
      setState(() {
        _driverPosition = driverPosition;
        _direction = direction;
        _isUsingPickupFallback = position == null;
        _isLoading = false;
        _lastUpdatedAt = DateTime.now();
        _focusMode = MapFocusMode.route;
        _focusRevision++;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _isLoading = false;
        _error =
            'Không thể tạo tuyến đường. Kiểm tra quyền GPS và kết nối mạng rồi thử lại.';
      });
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
    final direction = _direction;
    final bottomInset = MediaQuery.paddingOf(context).bottom;
    return Scaffold(
      backgroundColor: const Color(0xFFE8EDF3),
      body: Stack(
        fit: StackFit.expand,
        children: [
          GoongMapWidget(
            pickupLocation: _navigatingToPickup ? _destination : null,
            deliveryLocation: _navigatingToPickup ? null : _destination,
            driverLocation: _driverPosition,
            routePoints: direction?.points ?? const [],
            focusMode: _focusMode,
            focusRevision: _focusRevision,
            onMapReady: () {
              if (mounted) setState(() => _mapReady = true);
            },
          ),
          if (!_mapReady)
            const Positioned.fill(
              child: ColoredBox(
                color: Color(0xFFE8EDF3),
                child: Center(child: CircularProgressIndicator()),
              ),
            ),
          SafeArea(
            bottom: false,
            child: Padding(
              padding: const EdgeInsets.fromLTRB(12, 10, 12, 0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      _MapButton(
                        icon: Icons.arrow_back_rounded,
                        label: 'Quay lại',
                        onPressed: () => Navigator.of(context).pop(),
                      ),
                      const Spacer(),
                      _GpsChip(
                        isLoading: _isLoading,
                        isFallback: _isUsingPickupFallback,
                        updatedAt: _lastUpdatedAt,
                      ),
                    ],
                  ),
                  const SizedBox(height: 10),
                  ConstrainedBox(
                    constraints: const BoxConstraints(maxWidth: 560),
                    child: _NextActionPanel(
                      navigatingToPickup: _navigatingToPickup,
                      address: _destinationAddress,
                      distance: direction?.distanceText,
                    ),
                  ),
                ],
              ),
            ),
          ),
          Positioned(
            right: 12,
            bottom: 252 + bottomInset,
            child: Column(
              children: [
                _MapButton(
                  icon: Icons.my_location_rounded,
                  label: 'Theo tôi',
                  selected: _focusMode == MapFocusMode.driver,
                  onPressed: () => _focus(MapFocusMode.driver),
                ),
                const SizedBox(height: 8),
                _MapButton(
                  icon: Icons.route_rounded,
                  label: 'Toàn tuyến',
                  selected: _focusMode == MapFocusMode.route,
                  onPressed: () => _focus(MapFocusMode.route),
                ),
              ],
            ),
          ),
          Align(
            alignment: Alignment.bottomCenter,
            child: _RouteBottomSheet(
              direction: direction,
              destinationAddress: _destinationAddress,
              destinationLabel: _navigatingToPickup
                  ? 'Điểm lấy hàng'
                  : 'Điểm giao',
              isLoading: _isLoading,
              isFallback: _isUsingPickupFallback,
              error: _error,
              bottomInset: bottomInset,
              onRefresh: _loadRoute,
            ),
          ),
        ],
      ),
    );
  }
}

class _NextActionPanel extends StatelessWidget {
  const _NextActionPanel({
    required this.navigatingToPickup,
    required this.address,
    required this.distance,
  });
  final bool navigatingToPickup;
  final String address;
  final String? distance;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: AppColors.primary,
      elevation: 5,
      shadowColor: Colors.black26,
      borderRadius: BorderRadius.circular(16),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Row(
          children: [
            Container(
              width: 46,
              height: 46,
              decoration: BoxDecoration(
                color: Colors.white.withValues(alpha: 0.14),
                borderRadius: BorderRadius.circular(13),
              ),
              child: Icon(
                navigatingToPickup
                    ? Icons.inventory_2_rounded
                    : Icons.flag_rounded,
                color: Colors.white,
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    navigatingToPickup
                        ? 'Tiếp tục đến điểm lấy hàng'
                        : 'Tiếp tục đến điểm giao',
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 16,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                  const SizedBox(height: 3),
                  Text(
                    address,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      color: Colors.white.withValues(alpha: 0.78),
                      fontSize: 12,
                    ),
                  ),
                ],
              ),
            ),
            if (distance != null) ...[
              const SizedBox(width: 10),
              Text(
                distance!,
                style: const TextStyle(
                  color: Colors.white,
                  fontWeight: FontWeight.w800,
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _RouteBottomSheet extends StatelessWidget {
  const _RouteBottomSheet({
    required this.direction,
    required this.destinationAddress,
    required this.destinationLabel,
    required this.isLoading,
    required this.isFallback,
    required this.error,
    required this.bottomInset,
    required this.onRefresh,
  });
  final GoongDirection? direction;
  final String destinationAddress;
  final String destinationLabel;
  final bool isLoading;
  final bool isFallback;
  final String? error;
  final double bottomInset;
  final VoidCallback onRefresh;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      constraints: const BoxConstraints(maxWidth: 760),
      padding: EdgeInsets.fromLTRB(20, 16, 20, 16 + bottomInset),
      decoration: const BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.vertical(top: Radius.circular(22)),
        boxShadow: [
          BoxShadow(
            color: Color(0x290F172A),
            blurRadius: 24,
            offset: Offset(0, -8),
          ),
        ],
      ),
      child: error != null
          ? Row(
              children: [
                const Icon(Icons.location_off_outlined, color: AppColors.error),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    error!,
                    style: const TextStyle(fontSize: 13, height: 1.35),
                  ),
                ),
                TextButton(onPressed: onRefresh, child: const Text('Thử lại')),
              ],
            )
          : Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        destinationLabel,
                        style: const TextStyle(
                          fontSize: 12,
                          fontWeight: FontWeight.w700,
                          color: AppColors.textSecondary,
                        ),
                      ),
                    ),
                    IconButton(
                      tooltip: 'Cập nhật vị trí và tuyến đường',
                      onPressed: isLoading ? null : onRefresh,
                      icon: isLoading
                          ? const SizedBox.square(
                              dimension: 18,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : const Icon(Icons.refresh_rounded),
                    ),
                  ],
                ),
                Text(
                  destinationAddress,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    fontSize: 17,
                    height: 1.25,
                    fontWeight: FontWeight.w800,
                    color: AppColors.textPrimary,
                  ),
                ),
                const SizedBox(height: 14),
                Row(
                  children: [
                    Expanded(
                      child: _RouteMetric(
                        icon: Icons.route_rounded,
                        label: 'Còn lại',
                        value: direction?.distanceText ?? 'Đang tính…',
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: _RouteMetric(
                        icon: Icons.schedule_rounded,
                        label: 'Dự kiến',
                        value: direction?.durationText ?? 'Đang tính…',
                      ),
                    ),
                  ],
                ),
                if (isFallback) ...[
                  const SizedBox(height: 12),
                  const Text(
                    'Đang dùng điểm lấy hàng làm vị trí tạm. Bật quyền GPS rồi nhấn cập nhật để có tuyến chính xác.',
                    style: TextStyle(
                      fontSize: 12,
                      height: 1.35,
                      color: Color(0xFF9A5B13),
                    ),
                  ),
                ],
              ],
            ),
    );
  }
}

class _RouteMetric extends StatelessWidget {
  const _RouteMetric({
    required this.icon,
    required this.label,
    required this.value,
  });
  final IconData icon;
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Icon(icon, size: 20, color: AppColors.primary),
        const SizedBox(width: 8),
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              label,
              style: const TextStyle(
                fontSize: 11,
                color: AppColors.textSecondary,
              ),
            ),
            Text(
              value,
              style: const TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.w800,
                color: AppColors.textPrimary,
              ),
            ),
          ],
        ),
      ],
    );
  }
}

class _GpsChip extends StatelessWidget {
  const _GpsChip({
    required this.isLoading,
    required this.isFallback,
    required this.updatedAt,
  });
  final bool isLoading;
  final bool isFallback;
  final DateTime? updatedAt;

  @override
  Widget build(BuildContext context) {
    final color = isFallback ? const Color(0xFFB76E12) : AppColors.success;
    final text = isLoading
        ? 'Đang cập nhật GPS'
        : isFallback
        ? 'GPS chưa sẵn sàng'
        : 'GPS trực tiếp';
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 11, vertical: 9),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        boxShadow: const [
          BoxShadow(
            color: Color(0x1F0F172A),
            blurRadius: 12,
            offset: Offset(0, 4),
          ),
        ],
      ),
      child: Row(
        children: [
          Container(
            width: 8,
            height: 8,
            decoration: BoxDecoration(color: color, shape: BoxShape.circle),
          ),
          const SizedBox(width: 7),
          Text(
            text,
            style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w700),
          ),
        ],
      ),
    );
  }
}

class _MapButton extends StatelessWidget {
  const _MapButton({
    required this.icon,
    required this.label,
    required this.onPressed,
    this.selected = false,
  });
  final IconData icon;
  final String label;
  final VoidCallback onPressed;
  final bool selected;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: selected ? AppColors.primary : Colors.white,
      elevation: 4,
      shadowColor: Colors.black26,
      borderRadius: BorderRadius.circular(12),
      child: IconButton(
        tooltip: label,
        onPressed: onPressed,
        icon: Icon(
          icon,
          color: selected ? Colors.white : AppColors.textPrimary,
        ),
      ),
    );
  }
}
