import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/theme/app_colors.dart';
import 'package:swiftroute_app/core/utils/location_helper.dart';
import 'package:swiftroute_app/core/utils/polyline_decoder.dart';
import 'package:swiftroute_app/features/order/data/datasources/goong_remote_data_source.dart';
import 'package:swiftroute_app/features/order/data/models/goong_direction_model.dart';
import 'package:swiftroute_app/features/order/data/models/order_model.dart';
import 'package:swiftroute_app/features/order/presentation/widgets/goong_map_widget.dart';

/// Shows the live leg from the driver's current device location to a customer.
class DriverNavigationScreen extends ConsumerStatefulWidget {
  const DriverNavigationScreen({super.key, required this.order});

  final OrderModel order;

  @override
  ConsumerState<DriverNavigationScreen> createState() =>
      _DriverNavigationScreenState();
}

class _DriverNavigationScreenState extends ConsumerState<DriverNavigationScreen> {
  GoongDirection? _direction;
  LatLngPoint? _driverPosition;
  var _isUsingPickupFallback = false;
  var _isLoading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _loadRoute();
  }

  Future<void> _loadRoute() async {
    setState(() {
      _isLoading = true;
      _error = null;
    });

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
            destLat: widget.order.deliveryLat,
            destLng: widget.order.deliveryLng,
            vehicle: 'bike',
          );

      if (!mounted) return;
      setState(() {
        _driverPosition = driverPosition;
        _direction = direction;
        _isUsingPickupFallback = position == null;
        _isLoading = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _isLoading = false;
        _error = 'Không thể tạo tuyến đường. Kiểm tra GPS hoặc kết nối mạng.';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final delivery = LatLngPoint(
      widget.order.deliveryLat,
      widget.order.deliveryLng,
    );

    return Scaffold(
      appBar: AppBar(title: const Text('Đường tới khách hàng')),
      body: Column(
        children: [
          Expanded(
            flex: 5,
            child: _isLoading
                ? const Center(child: CircularProgressIndicator())
                : _error != null
                ? _RouteError(message: _error!, onRetry: _loadRoute)
                : GoongMapWidget(
                    driverLocation: _driverPosition,
                    deliveryLocation: delivery,
                    routePoints: _direction?.points ?? const [],
                  ),
          ),
          Container(
            width: double.infinity,
            padding: const EdgeInsets.fromLTRB(20, 18, 20, 24),
            decoration: const BoxDecoration(
              color: Colors.white,
              border: Border(top: BorderSide(color: AppColors.divider)),
            ),
            child: _isLoading || _error != null
                ? const SizedBox(height: 124)
                : Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text(
                        'Điểm giao',
                        style: TextStyle(
                          fontSize: 12,
                          color: AppColors.textSecondary,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        widget.order.deliveryAddress,
                        style: const TextStyle(
                          fontSize: 17,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      const SizedBox(height: 12),
                      Row(
                        children: [
                          Expanded(
                            child: _RouteMetric(
                              label: 'Quãng đường',
                              value: _direction!.distanceText,
                            ),
                          ),
                          Expanded(
                            child: _RouteMetric(
                              label: 'Thời gian dự kiến',
                              value: _direction!.durationText,
                            ),
                          ),
                        ],
                      ),
                      if (_isUsingPickupFallback) ...[
                        const SizedBox(height: 14),
                        const Text(
                          'Không lấy được GPS tài xế; tuyến đang bắt đầu từ điểm lấy hàng.',
                          style: TextStyle(
                            fontSize: 12,
                            color: AppColors.textSecondary,
                          ),
                        ),
                      ],
                      const SizedBox(height: 14),
                      SizedBox(
                        width: double.infinity,
                        height: 44,
                        child: OutlinedButton.icon(
                          onPressed: _loadRoute,
                          icon: const Icon(Icons.refresh, size: 18),
                          label: const Text('Cập nhật vị trí và tuyến đường'),
                        ),
                      ),
                    ],
                  ),
          ),
        ],
      ),
    );
  }
}

class _RouteMetric extends StatelessWidget {
  const _RouteMetric({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label,
          style: const TextStyle(fontSize: 12, color: AppColors.textSecondary),
        ),
        const SizedBox(height: 2),
        Text(value, style: const TextStyle(fontWeight: FontWeight.w700)),
      ],
    );
  }
}

class _RouteError extends StatelessWidget {
  const _RouteError({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(
              Icons.location_off_outlined,
              size: 42,
              color: AppColors.textSecondary,
            ),
            const SizedBox(height: 12),
            Text(message, textAlign: TextAlign.center),
            const SizedBox(height: 12),
            TextButton(onPressed: onRetry, child: const Text('Thử lại')),
          ],
        ),
      ),
    );
  }
}
