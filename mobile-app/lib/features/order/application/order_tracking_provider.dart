// features/order/application/order_tracking_provider.dart

import 'dart:async';

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

  const OrderTrackingState({
    this.order,
    this.driverInfo,
    this.status = OrderStatus.planned,
    this.driverPosition,
    this.routePoints = const [],
    this.currentRouteIndex = 0,
    this.isSimulating = false,
    this.simulationSpeed = 1,
  });

  bool get isDelivered => status == OrderStatus.delivered;

  /// True when an active delivery is currently underway (not delivered, cancelled, or failed).
  bool get hasActiveOrder =>
      order != null &&
      status != OrderStatus.delivered &&
      status != OrderStatus.cancelled &&
      status != OrderStatus.failed;

  double get progressPercentage {
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
    );
  }
}

/// StateNotifier managing real-time driver tracking and route movement simulation.
class OrderTrackingNotifier extends StateNotifier<OrderTrackingState> {
  final DriverRemoteDataSource _driverDataSource;
  final OrderRemoteDataSource _orderDataSource;
  final GoongRemoteDataSource _goongDataSource;
  Timer? _simulationTimer;

  OrderTrackingNotifier({
    required DriverRemoteDataSource driverDataSource,
    required OrderRemoteDataSource orderDataSource,
    required GoongRemoteDataSource goongDataSource,
  })  : _driverDataSource = driverDataSource,
        _orderDataSource = orderDataSource,
        _goongDataSource = goongDataSource,
        super(const OrderTrackingState());

  /// Initializes the tracking state with order data and decoded Goong route.
  void initTracking({required OrderModel order, GoongDirection? direction}) {
    _simulationTimer?.cancel();

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
      final dStartToPickup = (points.first.latitude - order.pickupLat).abs() +
          (points.first.longitude - order.pickupLng).abs();
      final dStartToDelivery = (points.first.latitude - order.deliveryLat).abs() +
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
    );

    // If real backend driverId is available, load live public profile
    if (order.assignedDriverId != null &&
        order.assignedDriverId != 'mock-driver-uuid') {
      _loadLiveDriverInfo(order.assignedDriverId!);
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
      }
    } catch (e) {
      debugPrint('[OrderTrackingNotifier] Could not fetch Goong directions: $e');
    }
  }

  /// Checks for any active order from the backend and initializes tracking if found.
  Future<void> fetchActiveOrder() async {
    try {
      final orders = await _orderDataSource.getMyOrders();
      final activeList = orders.where((o) =>
          o.status != OrderStatus.delivered &&
          o.status != OrderStatus.cancelled &&
          o.status != OrderStatus.failed).toList();

      if (activeList.isNotEmpty) {
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
        state = state.copyWith(
          currentRouteIndex: nextIndex,
          driverPosition: state.routePoints[nextIndex],
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
    state = state.copyWith(
      currentRouteIndex: 0,
      driverPosition: state.routePoints.isNotEmpty
          ? state.routePoints.first
          : null,
      isSimulating: false,
      status: state.order?.status ?? OrderStatus.planned,
    );
  }

  @override
  void dispose() {
    _simulationTimer?.cancel();
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
