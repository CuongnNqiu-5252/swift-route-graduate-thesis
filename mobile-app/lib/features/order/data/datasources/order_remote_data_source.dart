// features/order/data/datasources/order_remote_data_source.dart

import 'dart:async';
import 'dart:convert';
import 'dart:math';
import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/network/dio.dart';
import 'package:swiftroute_app/core/offline/offline_action.dart';
import 'package:swiftroute_app/core/offline/offline_sync_controller.dart';
import 'package:swiftroute_app/features/order/data/models/order_model.dart';
import 'package:swiftroute_app/features/driver/data/models/dispatch_plan_model.dart';
import 'package:swiftroute_app/features/driver/data/models/driver_cod_collection_model.dart';
import 'package:swiftroute_app/features/driver/data/models/package_handover_model.dart';

class OrderRemoteDataSource {
  final Dio _dio;
  final OfflineSyncController _offline;

  OrderRemoteDataSource(this._dio, this._offline);

  static const _ordersCacheKey = 'driver.assigned_orders.v1';
  static const _plansCacheKey = 'driver.dispatch_plans.v1';

  bool _isTransient(Object error) =>
      error is DioException &&
      (error.response == null ||
          (error.response?.statusCode ?? 0) >= 500 ||
          error.response?.statusCode == 408 ||
          error.response?.statusCode == 429);

  Future<void> _queue({
    required OfflineActionType type,
    required String method,
    required String path,
    required Map<String, dynamic> body,
    String? entityId,
  }) => _offline.queue(
    OfflineAction.create(
      type: type,
      method: method,
      path: path,
      entityId: entityId,
      body: body,
    ),
  );

  Future<void> _cacheOrders(List<OrderModel> orders) => _offline.putCache(
    _ordersCacheKey,
    jsonEncode(orders.map((order) => order.toJson()).toList()),
  );

  Future<List<OrderModel>> _cachedOrders() async {
    final raw = await _offline.getCache(_ordersCacheKey);
    if (raw == null) return [];
    final decoded = jsonDecode(raw);
    return decoded is List
        ? decoded
              .whereType<Map>()
              .map(
                (item) => OrderModel.fromJson(Map<String, dynamic>.from(item)),
              )
              .toList()
        : [];
  }

  Future<void> _cachePlans(List<DispatchPlanModel> plans) => _offline.putCache(
    _plansCacheKey,
    jsonEncode(plans.map((plan) => plan.toJson()).toList()),
  );

  Future<List<DispatchPlanModel>> _cachedPlans() async {
    final raw = await _offline.getCache(_plansCacheKey);
    if (raw == null) return [];
    final decoded = jsonDecode(raw);
    return decoded is List
        ? decoded
              .whereType<Map>()
              .map(
                (item) =>
                    DispatchPlanModel.fromJson(Map<String, dynamic>.from(item)),
              )
              .toList()
        : [];
  }

  /// Returns the active route stops assigned to the authenticated driver.
  /// The gateway derives the driver's identity from the bearer token.
  Future<List<OrderModel>> getAssignedDriverOrders() async {
    try {
      final tasksResponse = await _dio.get('/delivery-tasks/mine');
      final tasksData = tasksResponse.data;
      final taskList = tasksData is List
          ? tasksData
          : (tasksData is Map && tasksData['data'] is List
                ? tasksData['data'] as List
                : <dynamic>[]);
      final orders = taskList
          .whereType<Map>()
          .map((rawTask) {
            final task = Map<String, dynamic>.from(rawTask);
            final rawOrder = task['order'];
            if (rawOrder is! Map) return null;
            final returnDestination =
                task['return_destination'] ?? task['returnDestination'];
            final destination = returnDestination is Map
                ? returnDestination
                : null;
            return OrderModel.fromJson(
              Map<String, dynamic>.from(rawOrder),
            ).copyWith(
              pickupLocationId: destination?['id'] as String?,
              pickupLocationName: destination?['name'] as String?,
              pickupAddress: destination?['address'] as String?,
              pickupLat: (destination?['lat'] as num?)?.toDouble(),
              pickupLng: (destination?['lng'] as num?)?.toDouble(),
              deliveryTaskId:
                  task['task_id'] as String? ?? task['taskId'] as String?,
              deliveryTaskType:
                  task['task_type'] as String? ??
                  task['taskType'] as String? ??
                  'DELIVERY',
              deliveryTaskStatus: DeliveryTaskStatus.fromString(
                task['status'] as String?,
              ),
            );
          })
          .whereType<OrderModel>()
          .toList();
      await _cacheOrders(orders);
      unawaited(_offline.synchronize());
      return orders;
    } catch (e, stack) {
      debugPrint(
        '[OrderRemoteDataSource] getAssignedDriverOrders error: $e\n$stack',
      );
      return _cachedOrders();
    }
  }

  Future<void> updateDeliveryTaskStatus({
    required String taskId,
    required DeliveryTaskStatus status,
    String? failureReason,
    String? notes,
    String? proofUrl,
  }) async {
    final body = <String, dynamic>{
      'status': status.serverValue,
      if (failureReason != null && failureReason.isNotEmpty)
        'failure_reason': failureReason,
      if (notes != null && notes.isNotEmpty) 'notes': notes,
      if (proofUrl != null && proofUrl.isNotEmpty) 'proof_url': proofUrl,
    };
    try {
      await _dio.patch('/delivery-tasks/$taskId', data: body);
    } catch (error) {
      if (!_isTransient(error)) rethrow;
      await _queue(
        type: OfflineActionType.deliveryTaskUpdate,
        method: 'PATCH',
        path: '/delivery-tasks/$taskId',
        body: body,
      );
    }
  }

  String _operationId() {
    final random = Random.secure();
    final bytes = List<int>.generate(16, (_) => random.nextInt(256));
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    final hex = bytes
        .map((value) => value.toRadixString(16).padLeft(2, '0'))
        .join();
    return '${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}';
  }

  /// Records the exact COD outcome separately from delivery completion.
  Future<void> collectCod({
    required String orderId,
    required double amount,
    required String outcome,
    String? reason,
    String? evidenceUrl,
  }) async {
    final body = <String, dynamic>{
      'operation_id': _operationId(),
      'amount': amount,
      'outcome': outcome,
      if (reason != null && reason.isNotEmpty) 'reason': reason,
      if (evidenceUrl != null && evidenceUrl.isNotEmpty)
        'evidence_url': evidenceUrl,
    };
    try {
      await _dio.post('/cod/orders/$orderId/collections', data: body);
    } catch (error) {
      if (!_isTransient(error)) rethrow;
      await _queue(
        type: OfflineActionType.codCollection,
        method: 'POST',
        path: '/cod/orders/$orderId/collections',
        entityId: orderId,
        body: body,
      );
    }
  }

  /// Creates the auditable incident record used by the operations queue.
  Future<void> createOperationalIncident({
    required String orderId,
    required String incidentType,
    String? notes,
  }) async {
    final body = <String, dynamic>{
      'incident_type': incidentType,
      if (notes != null && notes.isNotEmpty) 'notes': notes,
    };
    try {
      await _dio.post('/operations/orders/$orderId/incidents', data: body);
    } catch (error) {
      if (!_isTransient(error)) rethrow;
      await _queue(
        type: OfflineActionType.incident,
        method: 'POST',
        path: '/operations/orders/$orderId/incidents',
        entityId: orderId,
        body: body,
      );
    }
  }

  Future<DriverCodCustodyModel> getCodCustody() async {
    final response = await _dio.get('/cod/mine/custody');
    final data = response.data;
    final raw = data is Map && data['data'] is Map
        ? data['data'] as Map
        : (data is Map ? data : <dynamic, dynamic>{});
    return DriverCodCustodyModel.fromJson(Map<String, dynamic>.from(raw));
  }

  Future<void> submitCodHandover({
    required List<String> collectionIds,
    required double declaredTotal,
    String? notes,
  }) async {
    final body = <String, dynamic>{
      'operation_id': _operationId(),
      'collection_ids': collectionIds,
      'order_ids': collectionIds,
      'declared_total': declaredTotal,
      if (notes != null && notes.isNotEmpty) 'notes': notes,
    };
    try {
      await _dio.post('/cod/handovers', data: body);
    } catch (error) {
      if (!_isTransient(error)) rethrow;
      await _queue(
        type: OfflineActionType.codHandover,
        method: 'POST',
        path: '/cod/handovers',
        body: body,
      );
    }
  }

  Future<List<PackageHandoverModel>> getMyPackageHandovers() async {
    final response = await _dio.get('/custody/handovers/mine');
    final data = response.data;
    final raw = data is Map && data['data'] is List
        ? data['data'] as List
        : (data is List ? data : <dynamic>[]);
    return raw
        .whereType<Map>()
        .map(
          (item) =>
              PackageHandoverModel.fromJson(Map<String, dynamic>.from(item)),
        )
        .toList();
  }

  Future<void> createPackageHandover({
    required String orderId,
    required String reason,
  }) async {
    await _dio.post(
      '/custody/handovers',
      data: {'order_id': orderId, 'reason': reason},
    );
  }

  Future<void> actOnPackageHandover({
    required String handoverId,
    required String action,
  }) async {
    await _dio.patch(
      '/custody/handovers/$handoverId/$action',
      data: {'notes': 'Xác nhận trên ứng dụng tài xế'},
    );
  }

  /// Reports a delivery incident, transitioning the order to FAILED.
  Future<OrderModel> reportIncident({
    required String orderId,
    required String incidentType,
    String? notes,
  }) async {
    final response = await _dio.patch(
      '/orders/$orderId/incident',
      data: {
        'incident_type': incidentType,
        if (notes != null && notes.isNotEmpty) 'notes': notes,
      },
    );
    final resData = response.data;
    final map = (resData is Map && resData['data'] is Map)
        ? resData['data'] as Map
        : (resData is Map ? resData : null);
    if (map == null) throw Exception('Failed to report delivery incident');
    return OrderModel.fromJson(Map<String, dynamic>.from(map));
  }

  /// Fetches active DISPATCHED route plans assigned to the authenticated driver.
  Future<List<DispatchPlanModel>> getDriverDispatchPlans() async {
    try {
      final response = await _dio.get('/orders/dispatch/driver-plans');
      final resData = response.data;
      final rawList = (resData is Map && resData['data'] is List)
          ? resData['data'] as List
          : (resData is List ? resData : <dynamic>[]);
      final plans = rawList
          .whereType<Map>()
          .map(
            (item) =>
                DispatchPlanModel.fromJson(Map<String, dynamic>.from(item)),
          )
          .toList();
      await _cachePlans(plans);
      unawaited(_offline.synchronize());
      return plans;
    } catch (e) {
      debugPrint('[OrderRemoteDataSource] getDriverDispatchPlans error: $e');
      return _cachedPlans();
    }
  }

  /// Driver acknowledges receipt and acceptance of a dispatched route plan.
  Future<DispatchPlanModel> acknowledgeDispatchPlan(String planId) async {
    Response<dynamic> response;
    try {
      response = await _dio.post('/orders/dispatch/plans/$planId/acknowledge');
    } catch (error) {
      if (!_isTransient(error)) rethrow;
      await _queue(
        type: OfflineActionType.routeAcknowledgement,
        method: 'POST',
        path: '/orders/dispatch/plans/$planId/acknowledge',
        entityId: planId,
        body: const {},
      );
      final plans = await _cachedPlans();
      final current = plans.where((plan) => plan.planId == planId).firstOrNull;
      if (current == null) rethrow;
      final optimistic = current.copyWith(acknowledgedAt: DateTime.now());
      await _cachePlans([
        for (final plan in plans) plan.planId == planId ? optimistic : plan,
      ]);
      return optimistic;
    }
    final resData = response.data;
    final map = (resData is Map && resData['data'] is Map)
        ? resData['data'] as Map
        : (resData is Map ? resData : null);
    if (map == null) throw Exception('Không thể xác nhận nhận tuyến');
    return DispatchPlanModel.fromJson(Map<String, dynamic>.from(map));
  }

  /// Driver declines a dispatched route plan with a reason.
  Future<DispatchPlanModel> declineDispatchPlan({
    required String planId,
    required String reason,
  }) async {
    final body = {'reason': reason};
    Response<dynamic> response;
    try {
      response = await _dio.post(
        '/orders/dispatch/plans/$planId/decline',
        data: body,
      );
    } catch (error) {
      if (!_isTransient(error)) rethrow;
      await _queue(
        type: OfflineActionType.routeDecline,
        method: 'POST',
        path: '/orders/dispatch/plans/$planId/decline',
        entityId: planId,
        body: body,
      );
      final plans = await _cachedPlans();
      final current = plans.where((plan) => plan.planId == planId).firstOrNull;
      if (current == null) rethrow;
      final optimistic = current.copyWith(planStatus: 'DECLINED');
      await _cachePlans([
        for (final plan in plans) plan.planId == planId ? optimistic : plan,
      ]);
      return optimistic;
    }
    final resData = response.data;
    final map = (resData is Map && resData['data'] is Map)
        ? resData['data'] as Map
        : (resData is Map ? resData : null);
    if (map == null) throw Exception('Không thể từ chối lộ trình');
    return DispatchPlanModel.fromJson(Map<String, dynamic>.from(map));
  }

  /// Uploads an evidence or POD photo to Cloudinary via the backend.
  Future<String> uploadEvidenceImage({
    required List<int> bytes,
    required String fileName,
    String folder = 'evidence',
  }) async {
    final formData = FormData.fromMap({
      'file': MultipartFile.fromBytes(bytes, filename: fileName),
      'folder': folder,
    });
    final response = await _dio.post(
      '/uploads/image',
      data: formData,
      options: Options(contentType: 'multipart/form-data'),
    );
    final resData = response.data;
    final map = (resData is Map && resData['data'] is Map)
        ? resData['data'] as Map
        : (resData is Map ? resData : <String, dynamic>{});
    final url = map['url']?.toString();
    if (url == null || url.isEmpty) {
      throw StateError('Máy chủ không trả về URL bằng chứng hợp lệ');
    }
    return url;
  }

  /// Reports an operational incident with severity, evidence, and delivery impact.
  Future<Map<String, dynamic>> reportOperationalIncident({
    String? orderId,
    String? planId,
    required String incidentType,
    String severity = 'MEDIUM',
    String? notes,
    List<String>? evidenceUrls,
    bool canContinueDelivery = true,
    bool needsImmediateAssistance = false,
    int contactAttempts = 1,
  }) async {
    final payload = <String, dynamic>{
      'incident_type': incidentType,
      'severity': severity,
      'can_continue_delivery': canContinueDelivery,
      'needs_immediate_assistance': needsImmediateAssistance,
      'contact_attempts': contactAttempts,
    };
    if (orderId != null) payload['order_id'] = orderId;
    if (planId != null) payload['dispatch_plan_id'] = planId;
    if (notes != null && notes.isNotEmpty) payload['notes'] = notes;
    if (evidenceUrls != null && evidenceUrls.isNotEmpty) {
      payload['evidence_urls'] = evidenceUrls;
    }

    Response<dynamic> response;
    try {
      response = await _dio.post('/operations/incidents', data: payload);
    } catch (error) {
      if (!_isTransient(error)) rethrow;
      await _queue(
        type: OfflineActionType.incident,
        method: 'POST',
        path: '/operations/incidents',
        entityId: orderId ?? planId,
        body: payload,
      );
      return {'offline_queued': true};
    }
    final resData = response.data;
    final map = (resData is Map && resData['data'] is Map)
        ? resData['data'] as Map
        : (resData is Map ? resData : <String, dynamic>{});
    return Map<String, dynamic>.from(map);
  }

  /// Batch-accepts all (or specified) ASSIGNED tasks for the driver in 1 tap.
  Future<void> batchAcceptDeliveryTasks({
    List<String>? taskIds,
    List<String>? orderIds,
    String? notes,
  }) async {
    final body = <String, dynamic>{
      if (taskIds != null && taskIds.isNotEmpty) 'task_ids': taskIds,
      if (orderIds != null && orderIds.isNotEmpty) 'order_ids': orderIds,
      if (notes != null && notes.isNotEmpty) 'notes': notes,
    };
    try {
      await _dio.post('/delivery-tasks/batch-accept', data: body);
    } catch (error) {
      if (!_isTransient(error)) rethrow;
      await _queue(
        type: OfflineActionType.deliveryTaskUpdate,
        method: 'POST',
        path: '/delivery-tasks/batch-accept',
        body: body,
      );
    }
  }

  /// Single-swipe batch pickup: acquires package custody and moves grouped depot tasks directly to OUT_FOR_DELIVERY.
  Future<void> batchPickupDeliveryTasks({
    List<String>? taskIds,
    List<String>? orderIds,
    String? notes,
  }) async {
    final body = <String, dynamic>{
      if (taskIds != null && taskIds.isNotEmpty) 'task_ids': taskIds,
      if (orderIds != null && orderIds.isNotEmpty) 'order_ids': orderIds,
      if (notes != null && notes.isNotEmpty) 'notes': notes,
    };
    try {
      await _dio.post('/delivery-tasks/batch-pickup', data: body);
    } catch (error) {
      if (!_isTransient(error)) rethrow;
      await _queue(
        type: OfflineActionType.deliveryTaskUpdate,
        method: 'POST',
        path: '/delivery-tasks/batch-pickup',
        body: body,
      );
    }
  }
}

/// Riverpod provider for OrderRemoteDataSource
final orderRemoteDataSourceProvider = Provider<OrderRemoteDataSource>((ref) {
  return OrderRemoteDataSource(
    ref.read(dioProvider),
    ref.read(offlineSyncProvider.notifier),
  );
});
