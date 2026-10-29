// features/order/application/order_form_provider.dart

import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/utils/location_helper.dart';
import 'package:swiftroute_app/features/order/data/datasources/goong_remote_data_source.dart';
import 'package:swiftroute_app/features/order/data/datasources/order_remote_data_source.dart';
import 'package:swiftroute_app/features/order/data/models/goong_direction_model.dart';
import 'package:swiftroute_app/features/order/data/models/order_model.dart';

/// State of the Customer Order Creation Form.
@immutable
class OrderFormState {
  final String? pickupAddress;
  final double? pickupLat;
  final double? pickupLng;

  final String? deliveryAddress;
  final double? deliveryLat;
  final double? deliveryLng;

  final double weightKg;
  final String priority;

  final GoongDirection? direction;
  final double? estimatedFee;

  final bool isLoadingLocation;
  final bool isCalculatingRoute;
  final bool isSubmitting;
  final String? errorMessage;
  final OrderModel? createdOrder;

  const OrderFormState({
    this.pickupAddress,
    this.pickupLat,
    this.pickupLng,
    this.deliveryAddress,
    this.deliveryLat,
    this.deliveryLng,
    this.weightKg = 1.0,
    this.priority = 'NORMAL',
    this.direction,
    this.estimatedFee,
    this.isLoadingLocation = false,
    this.isCalculatingRoute = false,
    this.isSubmitting = false,
    this.errorMessage,
    this.createdOrder,
  });

  bool get hasValidPickup =>
      pickupLat != null &&
      pickupLng != null &&
      (pickupAddress?.isNotEmpty ?? false);

  bool get hasValidDelivery =>
      deliveryLat != null &&
      deliveryLng != null &&
      (deliveryAddress?.isNotEmpty ?? false);

  bool get canSubmit =>
      hasValidPickup &&
      hasValidDelivery &&
      !isSubmitting &&
      !isCalculatingRoute;

  OrderFormState copyWith({
    String? pickupAddress,
    double? pickupLat,
    double? pickupLng,
    String? deliveryAddress,
    double? deliveryLat,
    double? deliveryLng,
    double? weightKg,
    String? priority,
    GoongDirection? direction,
    double? estimatedFee,
    bool? isLoadingLocation,
    bool? isCalculatingRoute,
    bool? isSubmitting,
    String? errorMessage,
    OrderModel? createdOrder,
    bool clearDirection = false,
    bool clearError = false,
  }) {
    return OrderFormState(
      pickupAddress: pickupAddress ?? this.pickupAddress,
      pickupLat: pickupLat ?? this.pickupLat,
      pickupLng: pickupLng ?? this.pickupLng,
      deliveryAddress: deliveryAddress ?? this.deliveryAddress,
      deliveryLat: deliveryLat ?? this.deliveryLat,
      deliveryLng: deliveryLng ?? this.deliveryLng,
      weightKg: weightKg ?? this.weightKg,
      priority: priority ?? this.priority,
      direction: clearDirection ? null : (direction ?? this.direction),
      estimatedFee: clearDirection ? null : (estimatedFee ?? this.estimatedFee),
      isLoadingLocation: isLoadingLocation ?? this.isLoadingLocation,
      isCalculatingRoute: isCalculatingRoute ?? this.isCalculatingRoute,
      isSubmitting: isSubmitting ?? this.isSubmitting,
      errorMessage: clearError ? null : (errorMessage ?? this.errorMessage),
      createdOrder: createdOrder ?? this.createdOrder,
    );
  }
}

/// StateNotifier managing the Order Creation flow, GPS lookup, and Goong Routing.
class OrderFormNotifier extends StateNotifier<OrderFormState> {
  final GoongRemoteDataSource _goongDataSource;
  final OrderRemoteDataSource _orderDataSource;

  OrderFormNotifier(this._goongDataSource, this._orderDataSource)
    : super(const OrderFormState());

  /// Uses device GPS sensor to automatically set pickup address.
  Future<void> useCurrentLocationAsPickup() async {
    state = state.copyWith(isLoadingLocation: true, clearError: true);

    try {
      final position = await LocationHelper.getCurrentPosition();
      if (position == null) {
        state = state.copyWith(
          isLoadingLocation: false,
          errorMessage: 'Không thể lấy GPS. Vui lòng bật vị trí và cấp quyền.',
        );
        return;
      }

      final address = await _goongDataSource.reverseGeocode(
        position.latitude,
        position.longitude,
      );

      state = state.copyWith(
        pickupLat: position.latitude,
        pickupLng: position.longitude,
        pickupAddress: address ?? 'Vị trí hiện tại của tôi',
        isLoadingLocation: false,
      );

      // If delivery is already chosen, compute motorbike route immediately
      if (state.hasValidDelivery) {
        await calculateRoute();
      }
    } catch (e) {
      state = state.copyWith(
        isLoadingLocation: false,
        errorMessage: 'Lỗi định vị: $e',
      );
    }
  }

  /// Sets pickup location manually from Goong search or map click.
  Future<void> setPickupLocation({
    required String address,
    required double lat,
    required double lng,
  }) async {
    state = state.copyWith(
      pickupAddress: address,
      pickupLat: lat,
      pickupLng: lng,
      clearError: true,
    );

    if (state.hasValidDelivery) {
      await calculateRoute();
    }
  }

  /// Sets delivery location manually from Goong search or map click.
  Future<void> setDeliveryLocation({
    required String address,
    required double lat,
    required double lng,
  }) async {
    state = state.copyWith(
      deliveryAddress: address,
      deliveryLat: lat,
      deliveryLng: lng,
      clearError: true,
    );

    if (state.hasValidPickup) {
      await calculateRoute();
    }
  }

  /// Updates package weight and recalculates estimated fee.
  void setWeight(double weight) {
    if (weight <= 0) return;
    state = state.copyWith(weightKg: weight);
    if (state.direction != null) {
      final fee = _computeDeliveryFee(state.direction!.distanceKm, weight);
      state = state.copyWith(estimatedFee: fee);
    }
  }

  /// Updates order delivery priority (NORMAL / URGENT).
  void setPriority(String priority) {
    state = state.copyWith(priority: priority);
  }

  /// Computes street-level routing between pickup and delivery using Goong Direction API.
  Future<void> calculateRoute() async {
    if (!state.hasValidPickup || !state.hasValidDelivery) return;

    state = state.copyWith(isCalculatingRoute: true, clearError: true);

    try {
      final direction = await _goongDataSource.getDirections(
        originLat: state.pickupLat!,
        originLng: state.pickupLng!,
        destLat: state.deliveryLat!,
        destLng: state.deliveryLng!,
        vehicle: 'bike',
      );

      final fee = _computeDeliveryFee(direction.distanceKm, state.weightKg);

      state = state.copyWith(
        direction: direction,
        estimatedFee: fee,
        isCalculatingRoute: false,
      );
    } catch (e) {
      debugPrint('[OrderFormNotifier] calculateRoute error: $e');
      state = state.copyWith(
        isCalculatingRoute: false,
        errorMessage: 'Không thể tính toán tuyến đường.',
      );
    }
  }

  /// Submits the order to backend order-service.
  Future<OrderModel?> submitOrder({String? tenantId}) async {
    if (!state.canSubmit) return null;

    state = state.copyWith(isSubmitting: true, clearError: true);

    try {
      final order = await _orderDataSource.createOrder(
        pickupLat: state.pickupLat!,
        pickupLng: state.pickupLng!,
        pickupAddress: state.pickupAddress!,
        deliveryLat: state.deliveryLat!,
        deliveryLng: state.deliveryLng!,
        deliveryAddress: state.deliveryAddress!,
        weightKg: state.weightKg,
        priority: state.priority,
        tenantId: tenantId,
      );

      state = state.copyWith(isSubmitting: false, createdOrder: order);

      return order;
    } catch (e) {
      debugPrint('[OrderFormNotifier] submitOrder error: $e');
      state = state.copyWith(
        isSubmitting: false,
        errorMessage: 'Không thể tạo đơn hàng: $e',
      );
      return null;
    }
  }

  /// Calculates estimated shipping fee based on distance and weight.
  /// Base fee: 15,000 VND (first 2 km) + 5,000 VND / additional km + 3,000 VND / kg over 2 kg.
  double _computeDeliveryFee(double distanceKm, double weightKg) {
    double fee = 15000.0;
    if (distanceKm > 2.0) {
      fee += (distanceKm - 2.0) * 5000.0;
    }
    if (weightKg > 2.0) {
      fee += (weightKg - 2.0) * 3000.0;
    }
    // Round to nearest 1,000 VND
    return (fee / 1000).round() * 1000.0;
  }

  /// Resets the form.
  void reset() {
    state = const OrderFormState();
  }
}

/// Global provider for OrderFormNotifier.
final orderFormProvider =
    StateNotifierProvider<OrderFormNotifier, OrderFormState>((ref) {
      return OrderFormNotifier(
        ref.read(goongRemoteDataSourceProvider),
        ref.read(orderRemoteDataSourceProvider),
      );
    });
