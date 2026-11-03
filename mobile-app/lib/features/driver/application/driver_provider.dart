// features/driver/application/driver_provider.dart
//
// Riverpod providers for the Driver feature.
// Uses AsyncNotifier pattern for async API flows (as per Flutter coding rules).

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/features/driver/data/datasources/driver_remote_data_source.dart';
import 'package:swiftroute_app/features/driver/data/models/driver_model.dart';
import 'package:swiftroute_app/features/driver/data/models/driver_cod_collection_model.dart';
import 'package:swiftroute_app/features/driver/data/models/package_handover_model.dart';
import 'package:swiftroute_app/features/driver/data/models/driver_earning_model.dart';
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
      () => ref
          .read(driverRemoteDataSourceProvider)
          .updateMyAvailability(isAvailable: nextAvailable),
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
    required double accuracyM,
    required double speedMps,
    required double bearing,
    required DateTime recordedAt,
    required int sequence,
  }) async {
    final current = state.valueOrNull;
    try {
      final updated = await ref
          .read(driverRemoteDataSourceProvider)
          .updateMyLocation(
            lat: lat,
            lng: lng,
            accuracyM: accuracyM,
            speedMps: speedMps,
            bearing: bearing,
            recordedAt: recordedAt,
            sequence: sequence,
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
  Future<void> updateDeliveryTask({
    required String orderId,
    required String taskId,
    required DeliveryTaskStatus nextStatus,
    String? failureReason,
    String? notes,
    String? proofUrl,
  }) async {
    final previousList = state.valueOrNull ?? [];
    final optimisticList = previousList
        .map(
          (order) => order.id == orderId
              ? order.copyWith(deliveryTaskStatus: nextStatus)
              : order,
        )
        .toList();
    state = AsyncData(optimisticList);

    try {
      await ref
          .read(orderRemoteDataSourceProvider)
          .updateDeliveryTaskStatus(
            taskId: taskId,
            status: nextStatus,
            failureReason: failureReason,
            notes: notes,
            proofUrl: proofUrl,
          );
      // Fetch fresh list from server to ensure full sync
      final freshList = await ref
          .read(orderRemoteDataSourceProvider)
          .getAssignedDriverOrders();
      state = AsyncData(freshList);
    } catch (e) {
      // Rollback on error
      state = AsyncData(previousList);
      rethrow;
    }
  }

  /// Reports a failed delivery through the canonical delivery-task execution API.
  Future<void> reportIncident({
    required String orderId,
    required String taskId,
    required String incidentType,
    String? notes,
  }) async {
    try {
      await ref
          .read(orderRemoteDataSourceProvider)
          .createOperationalIncident(
            orderId: orderId,
            incidentType: incidentType,
            notes: notes,
          );
      await ref
          .read(orderRemoteDataSourceProvider)
          .updateDeliveryTaskStatus(
            taskId: taskId,
            status: DeliveryTaskStatus.failed,
            failureReason: incidentType,
            notes: notes,
          );
      final freshList = await ref
          .read(orderRemoteDataSourceProvider)
          .getAssignedDriverOrders();
      state = AsyncData(freshList);
    } catch (_) {
      rethrow;
    }
  }

  /// Completes the delivery first, then records COD as a separate, explicit ledger event.
  /// A COD failure never rewinds the already-completed delivery task.
  Future<void> completeDelivery({
    required String orderId,
    required String taskId,
    String? proofUrl,
    double? codAmount,
  }) async {
    await ref
        .read(orderRemoteDataSourceProvider)
        .updateDeliveryTaskStatus(
          taskId: taskId,
          status: DeliveryTaskStatus.completed,
          proofUrl: proofUrl,
        );
    try {
      if (codAmount != null && codAmount > 0) {
        await ref
            .read(orderRemoteDataSourceProvider)
            .collectCod(orderId: orderId, amount: codAmount);
      }
    } finally {
      await refreshOrders();
    }
  }
}

/// Global provider for driver route orders
final driverOrdersProvider =
    AsyncNotifierProvider<DriverOrdersNotifier, List<OrderModel>>(
      DriverOrdersNotifier.new,
    );

class DriverCodHandoverNotifier
    extends AsyncNotifier<List<DriverCodCollectionModel>> {
  @override
  Future<List<DriverCodCollectionModel>> build() =>
      ref.read(orderRemoteDataSourceProvider).getPendingCodHandover();
  Future<void> refresh() async {
    state = await AsyncValue.guard(
      () => ref.read(orderRemoteDataSourceProvider).getPendingCodHandover(),
    );
  }

  Future<void> declare(DriverCodCollectionModel collection) async {
    await ref
        .read(orderRemoteDataSourceProvider)
        .declareCodHandover(
          collectionId: collection.id,
          amount: collection.amount,
        );
    await refresh();
  }
}

final driverCodHandoverProvider =
    AsyncNotifierProvider<
      DriverCodHandoverNotifier,
      List<DriverCodCollectionModel>
    >(DriverCodHandoverNotifier.new);

class PackageHandoverNotifier
    extends AsyncNotifier<List<PackageHandoverModel>> {
  @override
  Future<List<PackageHandoverModel>> build() =>
      ref.read(orderRemoteDataSourceProvider).getMyPackageHandovers();
  Future<void> refresh() async {
    state = await AsyncValue.guard(
      () => ref.read(orderRemoteDataSourceProvider).getMyPackageHandovers(),
    );
  }

  Future<void> act(PackageHandoverModel handover, String action) async {
    await ref
        .read(orderRemoteDataSourceProvider)
        .actOnPackageHandover(handoverId: handover.id, action: action);
    await refresh();
    await ref.read(driverOrdersProvider.notifier).refreshOrders();
  }
}

final packageHandoverProvider =
    AsyncNotifierProvider<PackageHandoverNotifier, List<PackageHandoverModel>>(
      PackageHandoverNotifier.new,
    );

class DriverEarningsNotifier extends AsyncNotifier<DriverEarningsBundle> {
  @override
  Future<DriverEarningsBundle> build() =>
      ref.read(driverRemoteDataSourceProvider).getMyEarnings();

  Future<void> refresh() async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(
      () => ref.read(driverRemoteDataSourceProvider).getMyEarnings(),
    );
  }
}

final driverEarningsProvider =
    AsyncNotifierProvider<DriverEarningsNotifier, DriverEarningsBundle>(
      DriverEarningsNotifier.new,
    );
