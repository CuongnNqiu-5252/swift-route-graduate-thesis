// features/driver/application/driver_provider.dart
//
// Riverpod providers for the Driver feature.
// Uses AsyncNotifier pattern for async API flows (as per Flutter coding rules).

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/features/driver/data/datasources/driver_remote_data_source.dart';
import 'package:swiftroute_app/features/driver/data/models/driver_model.dart';
import 'package:swiftroute_app/features/order/data/datasources/order_remote_data_source.dart';
import 'package:swiftroute_app/features/order/data/models/order_model.dart';

// ---------------------------------------------------------------------------
// Driver Profile & Work Status Notifier
// ---------------------------------------------------------------------------

class DriverNotifier extends AsyncNotifier<DriverModel?> {
  @override
  Future<DriverModel?> build() async {
    // Automatically load current driver profile using token in Dio interceptor
    try {
      return await ref.read(driverRemoteDataSourceProvider).getMyProfile();
    } catch (_) {
      return null;
    }
  }

  /// Manually refresh current driver profile
  Future<void> refreshProfile() async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(
      () => ref.read(driverRemoteDataSourceProvider).getMyProfile(),
    );
  }

  /// Toggle online / offline status with optimistic local update
  Future<void> toggleAvailability() async {
    final current = state.valueOrNull;
    if (current == null) return;

    final nextAvailable = !current.isAvailable;
    // Optimistic update
    state = AsyncData(current.copyWith(isAvailable: nextAvailable));

    final result = await AsyncValue.guard(
      () => ref.read(driverRemoteDataSourceProvider).updateMyAvailability(
            isAvailable: nextAvailable,
          ),
    );

    // Rollback if request failed
    if (result is AsyncError) {
      state = AsyncData(current);
    } else if (result.valueOrNull != null) {
      state = result;
    }
  }

  /// Send GPS coordinates to the backend
  Future<bool> reportLocation({
    required double lat,
    required double lng,
  }) async {
    final current = state.valueOrNull;
    try {
      final updated = await ref.read(driverRemoteDataSourceProvider).updateMyLocation(
            lat: lat,
            lng: lng,
          );
      state = AsyncData(updated);
      return true;
    } catch (_) {
      if (current != null) {
        state = AsyncData(current.copyWith(isAvailable: current.isAvailable));
      }
      return false;
    }
  }
}

/// Global provider for the driver profile and work status
final driverProvider = AsyncNotifierProvider<DriverNotifier, DriverModel?>(
  DriverNotifier.new,
);

// ---------------------------------------------------------------------------
// Driver Assigned Orders Notifier (D05 Today Route)
// ---------------------------------------------------------------------------

class DriverOrdersNotifier extends AsyncNotifier<List<OrderModel>> {
  @override
  Future<List<OrderModel>> build() async {
    return ref.read(orderRemoteDataSourceProvider).getAssignedDriverOrders();
  }

  /// Refresh assigned route stops from backend
  Future<void> refreshOrders() async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(
      () => ref.read(orderRemoteDataSourceProvider).getAssignedDriverOrders(),
    );
  }

  /// Transition order status (PLANNED -> IN_TRANSIT -> DELIVERED / FAILED)
  /// with optimistic state updating and rollback on error.
  Future<void> updateOrderStatus({
    required String orderId,
    required OrderStatus nextStatus,
  }) async {
    final previousList = state.valueOrNull ?? [];
    // Optimistically update list
    final optimisticList = previousList
        .map((order) => order.id == orderId ? order.copyWith(status: nextStatus) : order)
        .toList();
    state = AsyncData(optimisticList);

    try {
      await ref.read(orderRemoteDataSourceProvider).updateOrderStatus(
            orderId: orderId,
            status: nextStatus,
          );
      // Fetch fresh list from server to ensure full sync
      final freshList = await ref.read(orderRemoteDataSourceProvider).getAssignedDriverOrders();
      state = AsyncData(freshList);
    } catch (e) {
      // Rollback on error
      state = AsyncData(previousList);
      rethrow;
    }
  }

  /// Reports a delivery incident (traffic, customer unreachable, breakdown, etc.)
  /// Marks order as FAILED and refreshes list.
  Future<void> reportIncident({
    required String orderId,
    required String incidentType,
    String? notes,
  }) async {
    final previousList = state.valueOrNull ?? [];
    // Optimistically update status to FAILED
    final optimisticList = previousList
        .map((order) => order.id == orderId ? order.copyWith(status: OrderStatus.failed) : order)
        .toList();
    state = AsyncData(optimisticList);

    try {
      await ref.read(orderRemoteDataSourceProvider).reportIncident(
            orderId: orderId,
            incidentType: incidentType,
            notes: notes,
          );
      final freshList = await ref.read(orderRemoteDataSourceProvider).getAssignedDriverOrders();
      state = AsyncData(freshList);
    } catch (e) {
      state = AsyncData(previousList);
      rethrow;
    }
  }
}

/// Global provider for driver route orders
final driverOrdersProvider = AsyncNotifierProvider<DriverOrdersNotifier, List<OrderModel>>(
  DriverOrdersNotifier.new,
);
