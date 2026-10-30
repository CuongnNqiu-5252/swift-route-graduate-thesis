// features/order/application/order_tracking_provider.dart

import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/utils/polyline_decoder.dart';
import 'package:swiftroute_app/features/driver/data/datasources/driver_remote_data_source.dart';
import 'package:swiftroute_app/features/order/data/datasources/goong_remote_data_source.dart';
import 'package:swiftroute_app/features/order/data/datasources/order_remote_data_source.dart';
import 'package:swiftroute_app/features/order/data/models/goong_direction_model.dart';
import 'package:swiftroute_app/features/order/data/models/order_model.dart';

/// State of the Live Order Tracking screen.
@immutable
class OrderTrackingState {
  final OrderModel? order;
  final Map<String, dynamic>? driverInfo;
  final OrderStatus status;

  /// Current real-time position of the driver's motorbike.
  final LatLngPoint? driverPosition;

  /// Street-level path coordinates decoded from Goong Direction API.
  final List<LatLngPoint> routePoints;

  /// Current index of the driver along the [routePoints] path.
  final int currentRouteIndex;

  /// Whether the driver simulation is currently active.
  final bool isSimulating;

  /// Simulation speed multiplier (1x, 2x, 4x).
  final int simulationSpeed;

  /// Whether the client is actively receiving real-time SSE updates.
  final bool? _isSseConnected;
  bool get isSseConnected => _isSseConnected ?? false;

  /// Current vehicle bearing (degrees 0-360) from live telemetry.
  final double? _currentBearing;
  double get currentBearing => _currentBearing ?? 0.0;

  /// Current vehicle speed (km/h) from live telemetry.
  final double? _currentSpeedKmh;
  double get currentSpeedKmh => _currentSpeedKmh ?? 0.0;

  /// Current waypoint step in route simulation.
  final int? _sseStep;
  int get sseStep => _sseStep ?? 0;

  /// Total waypoint steps along the route.
  final int? _sseTotalSteps;
  int get sseTotalSteps => _sseTotalSteps ?? 0;

  const OrderTrackingState({
    this.order,
    this.driverInfo,
    this.status = OrderStatus.planned,
    this.driverPosition,
    this.routePoints = const [],
    this.currentRouteIndex = 0,
    this.isSimulating = false,
    this.simulationSpeed = 1,
    bool? isSseConnected,
    double? currentBearing,
    double? currentSpeedKmh,
    int? sseStep,
    int? sseTotalSteps,
  }) : _isSseConnected = isSseConnected ?? false,
       _currentBearing = currentBearing ?? 0.0,
       _currentSpeedKmh = currentSpeedKmh ?? 0.0,
       _sseStep = sseStep ?? 0,
       _sseTotalSteps = sseTotalSteps ?? 0;

  bool get isDelivered => status == OrderStatus.delivered;

  /// True when an active delivery is currently underway (not delivered, cancelled, or failed).
  bool get hasActiveOrder =>
      order != null &&
      status != OrderStatus.delivered &&
      status != OrderStatus.cancelled &&
      status != OrderStatus.failed;

  double get progressPercentage {
    if (sseTotalSteps > 0) {
      return (sseStep / sseTotalSteps).clamp(0.0, 1.0);
    }
    if (routePoints.isEmpty) return 0.0;
    return (currentRouteIndex / (routePoints.length - 1)).clamp(0.0, 1.0);
  }

  OrderTrackingState copyWith({
    OrderModel? order,
    Map<String, dynamic>? driverInfo,
    OrderStatus? status,
    LatLngPoint? driverPosition,
    List<LatLngPoint>? routePoints,
    int? currentRouteIndex,
    bool? isSimulating,
    int? simulationSpeed,
    bool? isSseConnected,
    double? currentBearing,
    double? currentSpeedKmh,
    int? sseStep,
    int? sseTotalSteps,
  }) {
    return OrderTrackingState(
      order: order ?? this.order,
      driverInfo: driverInfo ?? this.driverInfo,
      status: status ?? this.status,
      driverPosition: driverPosition ?? this.driverPosition,
      routePoints: routePoints ?? this.routePoints,
      currentRouteIndex: currentRouteIndex ?? this.currentRouteIndex,
      isSimulating: isSimulating ?? this.isSimulating,
      simulationSpeed: simulationSpeed ?? this.simulationSpeed,
      isSseConnected: isSseConnected ?? this.isSseConnected,
      currentBearing: currentBearing ?? this.currentBearing,
      currentSpeedKmh: currentSpeedKmh ?? this.currentSpeedKmh,
      sseStep: sseStep ?? this.sseStep,
      sseTotalSteps: sseTotalSteps ?? this.sseTotalSteps,
    );
  }
}

/// StateNotifier managing real-time driver tracking and route movement simulation.
class OrderTrackingNotifier extends StateNotifier<OrderTrackingState> {
  final DriverRemoteDataSource _driverDataSource;
  final OrderRemoteDataSource _orderDataSource;
  final GoongRemoteDataSource _goongDataSource;
  Timer? _simulationTimer;
  StreamSubscription<Map<String, dynamic>>? _sseSubscription;

  OrderTrackingNotifier({
    required DriverRemoteDataSource driverDataSource,
    required OrderRemoteDataSource orderDataSource,
    required GoongRemoteDataSource goongDataSource,
  }) : _driverDataSource = driverDataSource,
       _orderDataSource = orderDataSource,
       _goongDataSource = goongDataSource,
       super(const OrderTrackingState());

  /// Initializes the tracking state with order data and decoded Goong route.
  void initTracking({required OrderModel order, GoongDirection? direction}) {
    _simulationTimer?.cancel();
    _sseSubscription?.cancel();

    // Default route points from Goong direction, or fallback interpolation
    List<LatLngPoint> points = List.from(direction?.points ?? []);
    if (points.isEmpty) {
      points = [
        LatLngPoint(order.pickupLat, order.pickupLng),
        LatLngPoint(order.deliveryLat, order.deliveryLng),
      ];
      // Fetch street-level polyline in background
      _fetchDirectionForOrder(order);
    } else if (points.length >= 2) {
      // Ensure orientation: points.first must be closest to pickup, not delivery
      final dStartToPickup =
          (points.first.latitude - order.pickupLat).abs() +
          (points.first.longitude - order.pickupLng).abs();
      final dStartToDelivery =
          (points.first.latitude - order.deliveryLat).abs() +
          (points.first.longitude - order.deliveryLng).abs();
      if (dStartToDelivery < dStartToPickup) {
        points = points.reversed.toList();
      }
    }

    final initialDriverPos = points.isNotEmpty ? points.first : null;

    final defaultDriverInfo = {
      'id': order.assignedDriverId ?? 'driver-uuid-001',
      'full_name': 'Nguyễn Văn Tài (Tài xế)',
      'phone_number': '0909 123 456',
      'license_plate': '59-X1 888.88',
      'vehicle_type': 'Xe máy (Honda Wave Alpha)',
      'rating': 4.9,
    };

    state = OrderTrackingState(
      order: order,
      driverInfo: defaultDriverInfo,
      status: order.status,
      routePoints: points,
      driverPosition: initialDriverPos,
      currentRouteIndex: 0,
      isSimulating: false,
      simulationSpeed: 1,
      isSseConnected: false,
      currentBearing: 0.0,
      currentSpeedKmh: 0.0,
      sseStep: 0,
      sseTotalSteps: 0,
    );

    // If real backend driverId is available, load live public profile
    if (order.assignedDriverId != null &&
        order.assignedDriverId != 'mock-driver-uuid') {
      _loadLiveDriverInfo(order.assignedDriverId!);
    }

    // Automatically connect to real-time location stream
    if (order.status != OrderStatus.delivered &&
        order.status != OrderStatus.cancelled &&
        order.status != OrderStatus.failed) {
      startSseTracking();
    }
  }

  Future<void> _fetchDirectionForOrder(OrderModel order) async {
    try {
      final dir = await _goongDataSource.getDirections(
        originLat: order.pickupLat,
        originLng: order.pickupLng,
        destLat: order.deliveryLat,
        destLng: order.deliveryLng,
      );
      if (dir.points.isNotEmpty && state.order?.id == order.id) {
        state = state.copyWith(
          routePoints: dir.points,
          driverPosition: state.driverPosition ?? dir.points.first,
        );
        if (!state.isDelivered && !state.isSimulating) {
          startSimulation();
        }
      }
    } catch (e) {
      debugPrint(
        '[OrderTrackingNotifier] Could not fetch Goong directions: $e',
      );
    }
  }

  /// Checks for any active order from the backend and initializes tracking if found.
  Future<void> fetchActiveOrder() async {
    try {
      final orders = await _orderDataSource.getMyOrders();
      final activeList = orders
          .where(
            (o) =>
                o.status != OrderStatus.delivered &&
                o.status != OrderStatus.cancelled &&
                o.status != OrderStatus.failed,
          )
          .toList();

      if (activeList.isNotEmpty) {
        // Sort newest active order first
        activeList.sort((a, b) => b.createdAt.compareTo(a.createdAt));
        final latest = activeList.first;
        if (state.order == null || state.order!.id != latest.id) {
          initTracking(order: latest);
        }
      }
    } catch (e) {
      debugPrint('[OrderTrackingNotifier] fetchActiveOrder error: $e');
    }
  }

  Future<void> _loadLiveDriverInfo(String driverId) async {
    try {
      final info = await _driverDataSource.getDriverPublicInfo(driverId);
      state = state.copyWith(driverInfo: info);
    } catch (e) {
      debugPrint('[OrderTrackingNotifier] Could not load live driver info: $e');
    }
  }

  /// Starts or resumes the driver movement simulation along the Goong route.
  void startSimulation() {
    if (state.routePoints.isEmpty || state.isDelivered) return;

    _simulationTimer?.cancel();
    state = state.copyWith(isSimulating: true, status: OrderStatus.inTransit);

    final intervalMs = (1000 ~/ state.simulationSpeed).clamp(200, 1000);

    _simulationTimer = Timer.periodic(Duration(milliseconds: intervalMs), (
      timer,
    ) {
      if (state.currentRouteIndex < state.routePoints.length - 1) {
        final nextIndex = state.currentRouteIndex + 1;
        final currentPoint = state.routePoints[state.currentRouteIndex];
        final nextPoint = state.routePoints[nextIndex];

        // Calculate heading in degrees
        final dLng = (nextPoint.longitude - currentPoint.longitude) * math.pi / 180.0;
        final lat1 = currentPoint.latitude * math.pi / 180.0;
        final lat2 = nextPoint.latitude * math.pi / 180.0;
        final y = math.sin(dLng) * math.cos(lat2);
        final x = math.cos(lat1) * math.sin(lat2) - math.sin(lat1) * math.cos(lat2) * math.cos(dLng);
        final bearing = (math.atan2(y, x) * 180.0 / math.pi + 360.0) % 360.0;

        state = state.copyWith(
          currentRouteIndex: nextIndex,
          driverPosition: nextPoint,
          currentBearing: bearing,
          currentSpeedKmh: 30.0,
          sseStep: nextIndex,
          sseTotalSteps: state.routePoints.length,
          status: OrderStatus.inTransit,
        );
      } else {
        // Reached destination!
        timer.cancel();
        state = state.copyWith(
          isSimulating: false,
          status: OrderStatus.delivered,
        );
      }
    });
  }

  /// Pauses the simulation.
  void pauseSimulation() {
    _simulationTimer?.cancel();
    state = state.copyWith(isSimulating: false);
  }

  /// Adjusts the simulation speed multiplier (1x, 2x, 4x).
  void setSimulationSpeed(int speed) {
    state = state.copyWith(simulationSpeed: speed);
    if (state.isSimulating) {
      startSimulation(); // Restart timer with new interval
    }
  }

  /// Resets the simulation to the beginning of the route.
  void resetSimulation() {
    _simulationTimer?.cancel();
    _sseSubscription?.cancel();
    state = state.copyWith(
      currentRouteIndex: 0,
      driverPosition: state.routePoints.isNotEmpty
          ? state.routePoints.first
          : null,
      isSimulating: false,
      isSseConnected: false,
      sseStep: 0,
      status: state.order?.status ?? OrderStatus.planned,
    );
  }

  /// Connects to the backend Server-Sent Events (SSE) live tracking stream.
  /// Receives real-time waypoints (lat, lng, bearing, speed) from server simulation/GPS.
  void startSseTracking() {
    final currentOrder = state.order;
    if (currentOrder == null) return;

    _simulationTimer?.cancel();
    _sseSubscription?.cancel();

    state = state.copyWith(
      isSseConnected: true,
      isSimulating: true,
      status: OrderStatus.inTransit,
    );

    // If no SSE event arrives within 2.5s (e.g. streaming buffered on Web),
    // automatically fallback to local simulation along the Goong polyline
    Future.delayed(const Duration(milliseconds: 2500), () {
      if (state.order?.id == currentOrder.id &&
          state.sseStep == 0 &&
          !state.isDelivered &&
          !state.isSimulating &&
          state.routePoints.isNotEmpty) {
        startSimulation();
      }
    });

    _sseSubscription = _orderDataSource
        .streamOrderTracking(currentOrder.id)
        .listen(
          (data) {
            final eventType = data['sse_event'] as String? ?? 'message';

            if (eventType == 'connected') {
              final total = (data['total_steps'] as num?)?.toInt() ?? 24;
              state = state.copyWith(
                isSseConnected: true,
                isSimulating: true,
                sseTotalSteps: total,
                status: OrderStatus.inTransit,
              );
            } else if (eventType == 'location_update') {
              // Once SSE pushes real waypoints, cancel any local fallback timer
              _simulationTimer?.cancel();
              final lat = (data['lat'] as num?)?.toDouble();
              final lng = (data['lng'] as num?)?.toDouble();
              final bearing =
                  (data['bearing'] as num?)?.toDouble() ?? state.currentBearing;
              final speed = (data['speed_kmh'] as num?)?.toDouble() ?? 25.0;
              final step = (data['step'] as num?)?.toInt() ?? state.sseStep;
              final total =
                  (data['total_steps'] as num?)?.toInt() ?? state.sseTotalSteps;

              if (lat != null && lng != null) {
                state = state.copyWith(
                  driverPosition: LatLngPoint(lat, lng),
                  currentBearing: bearing,
                  currentSpeedKmh: speed,
                  sseStep: step,
                  sseTotalSteps: total,
                  isSimulating: true,
                  status: OrderStatus.inTransit,
                );
              }
            } else if (eventType == 'status_update') {
              final statusStr = data['status'] as String?;
              if (statusStr == 'DELIVERED') {
                state = state.copyWith(
                  isSimulating: false,
                  isSseConnected: false,
                  status: OrderStatus.delivered,
                );
              }
            }
          },
          onError: (err) {
            debugPrint('[OrderTrackingNotifier] SSE Stream error: $err, falling back to local simulation');
            state = state.copyWith(isSseConnected: false);
            if (state.routePoints.isNotEmpty && !state.isDelivered) {
              startSimulation();
            }
          },
          onDone: () {
            debugPrint('[OrderTrackingNotifier] SSE Stream completed');
            state = state.copyWith(isSseConnected: false);
            if (state.routePoints.isNotEmpty && !state.isDelivered) {
              startSimulation();
            }
          },
        );
  }

  /// Disconnects from the SSE stream.
  void stopSseTracking() {
    _sseSubscription?.cancel();
    _sseSubscription = null;
    state = state.copyWith(isSseConnected: false, isSimulating: false);
  }

  @override
  void dispose() {
    _simulationTimer?.cancel();
    _sseSubscription?.cancel();
    super.dispose();
  }
}

/// Global provider for OrderTrackingNotifier.
final orderTrackingProvider =
    StateNotifierProvider<OrderTrackingNotifier, OrderTrackingState>((ref) {
      return OrderTrackingNotifier(
        driverDataSource: ref.read(driverRemoteDataSourceProvider),
        orderDataSource: ref.read(orderRemoteDataSourceProvider),
        goongDataSource: ref.read(goongRemoteDataSourceProvider),
      );
    });
