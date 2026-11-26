// features/driver/application/driver_provider.dart
//
// Riverpod providers for the Driver feature.
// Uses AsyncNotifier pattern for async API flows (as per Flutter coding rules).

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/offline/offline_action.dart';
import 'package:swiftroute_app/features/driver/data/datasources/driver_remote_data_source.dart';
import 'package:swiftroute_app/features/driver/data/models/delivery_proof_config_model.dart';
import 'package:swiftroute_app/features/driver/data/models/driver_model.dart';
import 'package:swiftroute_app/features/driver/data/models/dispatch_plan_model.dart';
import 'package:swiftroute_app/features/driver/data/models/driver_cod_collection_model.dart';
import 'package:swiftroute_app/features/driver/data/models/driver_earning_model.dart';
import 'package:swiftroute_app/features/driver/data/models/driver_notification_model.dart';
import 'package:swiftroute_app/features/order/data/datasources/order_remote_data_source.dart';
import 'package:swiftroute_app/features/order/data/models/order_model.dart';

// ---------------------------------------------------------------------------
// Driver Profile & Work Status Notifier
// ---------------------------------------------------------------------------

class DriverNotifier extends AsyncNotifier<DriverModel?> {
  @override
  Future<DriverModel?> build() async {
    // Automatically load current driver profile using token in Dio interceptor
    return ref.read(driverRemoteDataSourceProvider).getMyProfile();
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

  Future<bool> saveProfile({
    required String fullName,
    required String phone,
    required String licensePlate,
    required String vehicleType,
    required int capacity,
  }) async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(
      () => ref
          .read(driverRemoteDataSourceProvider)
          .updateMyProfile(
            fullName: fullName,
            phone: phone,
            licensePlate: licensePlate,
            vehicleType: vehicleType,
            capacity: capacity,
          ),
    );
    return !state.hasError;
  }

  Future<bool> submitProfile() async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(
      () => ref.read(driverRemoteDataSourceProvider).submitMyProfile(),
    );
    return !state.hasError;
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
    } on OfflineActionQueuedException {
      if (current != null) state = AsyncData(current);
      return true;
    } catch (_) {
      if (current != null) state = AsyncData(current);
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

  /// Applies an optimistic transform to the current order list, executes [remoteCall],
  /// refreshes from server on success, and rolls back on failure.
  Future<void> _runOptimisticOrderUpdate({
    required OrderModel Function(OrderModel order) transform,
    required Future<void> Function() remoteCall,
  }) async {
    final previousList = state.valueOrNull ?? [];
    state = AsyncData(previousList.map(transform).toList());
    try {
      await remoteCall();
      final fresh = await ref
          .read(orderRemoteDataSourceProvider)
          .getAssignedDriverOrders();
      state = AsyncData(fresh);
    } catch (_) {
      state = AsyncData(previousList);
      rethrow;
    }
  }

  /// Transition order status (IN_TRANSIT -> DELIVERED / FAILED)
  /// with optimistic state updating and rollback on error.
  Future<void> updateDeliveryTask({
    required String orderId,
    required String taskId,
    required DeliveryTaskStatus nextStatus,
    String? failureReason,
    String? notes,
    String? proofUrl,
  }) {
    return _runOptimisticOrderUpdate(
      transform: (order) => order.id == orderId
          ? order.copyWith(deliveryTaskStatus: nextStatus)
          : order,
      remoteCall: () => ref
          .read(orderRemoteDataSourceProvider)
          .updateDeliveryTaskStatus(
            taskId: taskId,
            status: nextStatus,
            failureReason: failureReason,
            notes: notes,
            proofUrl: proofUrl,
          ),
    );
  }

  /// Completes the delivery first, then records COD as a separate, explicit ledger event.
  /// A COD failure never rewinds the already-completed delivery task.
  Future<void> completeDelivery({
    required String orderId,
    required String taskId,
    String? notes,
    String? proofUrl,
    double? codAmount,
    String? codOutcome,
    String? codReason,
  }) async {
    await ref
        .read(orderRemoteDataSourceProvider)
        .updateDeliveryTaskStatus(
          taskId: taskId,
          status: DeliveryTaskStatus.completed,
          notes: notes,
          proofUrl: proofUrl,
        );
    try {
      if (codAmount != null && codOutcome != null) {
        await ref
            .read(orderRemoteDataSourceProvider)
            .collectCod(
              orderId: orderId,
              amount: codAmount,
              outcome: codOutcome,
              reason: codReason,
              evidenceUrl: proofUrl,
            );
      }
    } finally {
      if (codAmount != null && codOutcome != null) {
        ref.invalidate(driverCodHandoverProvider);
      }
      await refreshOrders();
    }
  }
}

/// Global provider for driver route orders
final driverOrdersProvider =
    AsyncNotifierProvider<DriverOrdersNotifier, List<OrderModel>>(
      DriverOrdersNotifier.new,
    );

class DriverCodHandoverNotifier extends AsyncNotifier<DriverCodCustodyModel> {
  @override
  Future<DriverCodCustodyModel> build() =>
      ref.read(orderRemoteDataSourceProvider).getCodCustody();
  Future<void> refresh() async {
    state = await AsyncValue.guard(
      () => ref.read(orderRemoteDataSourceProvider).getCodCustody(),
    );
  }

  Future<void> submit({
    required List<DriverCodCollectionModel> collections,
    required double declaredTotal,
    String? notes,
  }) async {
    await ref
        .read(orderRemoteDataSourceProvider)
        .submitCodHandover(
          collectionIds: collections.map((item) => item.id).toList(),
          declaredTotal: declaredTotal,
          notes: notes,
        );
    await refresh();
  }
}

final driverCodHandoverProvider =
    AsyncNotifierProvider<DriverCodHandoverNotifier, DriverCodCustodyModel>(
      DriverCodHandoverNotifier.new,
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

class DriverDispatchPlansNotifier
    extends AsyncNotifier<List<DispatchPlanModel>> {
  @override
  Future<List<DispatchPlanModel>> build() =>
      ref.read(orderRemoteDataSourceProvider).getDriverDispatchPlans();

  Future<void> refresh() async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(
      () => ref.read(orderRemoteDataSourceProvider).getDriverDispatchPlans(),
    );
  }
}

final driverDispatchPlansProvider =
    AsyncNotifierProvider<DriverDispatchPlansNotifier, List<DispatchPlanModel>>(
      DriverDispatchPlansNotifier.new,
    );

class DriverNotificationsNotifier
    extends AsyncNotifier<List<DriverNotificationModel>> {
  @override
  Future<List<DriverNotificationModel>> build() =>
      ref.read(driverRemoteDataSourceProvider).getMyNotifications();

  Future<void> refresh() async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(
      () => ref.read(driverRemoteDataSourceProvider).getMyNotifications(),
    );
  }
}

final driverNotificationsProvider =
    AsyncNotifierProvider<
      DriverNotificationsNotifier,
      List<DriverNotificationModel>
    >(DriverNotificationsNotifier.new);

class DriverReadNotificationsNotifier extends Notifier<Set<String>> {
  @override
  Set<String> build() => <String>{};

  void markAllRead(Iterable<String> ids) {
    state = {...state, ...ids};
  }
}

final driverReadNotificationIdsProvider =
    NotifierProvider<DriverReadNotificationsNotifier, Set<String>>(
      DriverReadNotificationsNotifier.new,
    );

class DeliveryProofConfigNotifier
    extends AsyncNotifier<DeliveryProofConfigModel> {
  @override
  Future<DeliveryProofConfigModel> build() =>
      ref.read(orderRemoteDataSourceProvider).getDeliveryProofConfig();

  Future<void> refresh() async {
    final result = await AsyncValue.guard(
      () => ref.read(orderRemoteDataSourceProvider).getDeliveryProofConfig(),
    );
    if (result.valueOrNull != null || !state.hasValue) {
      state = result;
    }
  }
}

final deliveryProofConfigProvider =
    AsyncNotifierProvider<
      DeliveryProofConfigNotifier,
      DeliveryProofConfigModel
    >(DeliveryProofConfigNotifier.new);
