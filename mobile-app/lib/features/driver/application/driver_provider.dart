// features/driver/application/driver_provider.dart
//
// Riverpod providers for the Driver feature.
// Uses AsyncNotifier pattern for async API flows (as per Flutter coding rules).

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/offline/offline_action.dart';
import 'package:swiftroute_app/features/driver/data/datasources/driver_remote_data_source.dart';
import 'package:swiftroute_app/features/driver/data/models/driver_model.dart';
import 'package:swiftroute_app/features/driver/data/models/dispatch_plan_model.dart';
import 'package:swiftroute_app/features/driver/data/models/driver_cod_collection_model.dart';
import 'package:swiftroute_app/features/driver/data/models/package_handover_model.dart';
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

  /// 1-Tap Batch Accept: transitions all ASSIGNED tasks on the route to ACCEPTED.
  Future<void> acceptAllAssigned({List<String>? orderIds}) async {
    final previousList = state.valueOrNull ?? [];
    final targetIds = orderIds?.toSet();
    state = AsyncData(
      previousList.map((order) {
        final match = targetIds == null || targetIds.contains(order.id);
        if (match && order.deliveryTaskStatus == DeliveryTaskStatus.assigned) {
          return order.copyWith(
            deliveryTaskStatus: DeliveryTaskStatus.accepted,
          );
        }
        return order;
      }).toList(),
    );
    try {
      await ref
          .read(orderRemoteDataSourceProvider)
          .batchAcceptDeliveryTasks(orderIds: orderIds);
      final fresh = await ref
          .read(orderRemoteDataSourceProvider)
          .getAssignedDriverOrders();
      state = AsyncData(fresh);
    } catch (e) {
      state = AsyncData(previousList);
      rethrow;
    }
  }

  /// 1-Swipe Depot Pickup: confirms physical package pickup for a group of orders
  /// and transitions them directly to OUT_FOR_DELIVERY.
  Future<void> confirmBatchPickup({
    required List<String> orderIds,
    String? notes,
  }) async {
    final previousList = state.valueOrNull ?? [];
    final idSet = orderIds.toSet();
    state = AsyncData(
      previousList.map((order) {
        if (idSet.contains(order.id)) {
          return order.copyWith(
            status: OrderStatus.inTransit,
            deliveryTaskStatus: DeliveryTaskStatus.outForDelivery,
          );
        }
        return order;
      }).toList(),
    );
    try {
      await ref
          .read(orderRemoteDataSourceProvider)
          .batchPickupDeliveryTasks(orderIds: orderIds, notes: notes);
      final fresh = await ref
          .read(orderRemoteDataSourceProvider)
          .getAssignedDriverOrders();
      state = AsyncData(fresh);
    } catch (e) {
      state = AsyncData(previousList);
      rethrow;
    }
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
    String? codOutcome,
    String? codReason,
  }) async {
    await ref
        .read(orderRemoteDataSourceProvider)
        .updateDeliveryTaskStatus(
          taskId: taskId,
          status: DeliveryTaskStatus.completed,
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

  Future<void> acknowledge(String planId) async {
    await ref
        .read(orderRemoteDataSourceProvider)
        .acknowledgeDispatchPlan(planId);
    await refresh();
    await ref.read(driverOrdersProvider.notifier).refreshOrders();
  }

  Future<void> decline(String planId, String reason) async {
    await ref
        .read(orderRemoteDataSourceProvider)
        .declineDispatchPlan(planId: planId, reason: reason);
    await refresh();
    await ref.read(driverOrdersProvider.notifier).refreshOrders();
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
