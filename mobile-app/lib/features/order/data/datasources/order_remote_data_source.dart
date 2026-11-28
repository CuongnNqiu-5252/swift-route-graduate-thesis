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
import 'package:swiftroute_app/features/driver/data/models/delivery_proof_config_model.dart';
import 'package:swiftroute_app/features/driver/data/models/dispatch_plan_model.dart';
import 'package:swiftroute_app/features/driver/data/models/driver_cod_collection_model.dart';

class OrderRemoteDataSource {
  final Dio _dio;
  final OfflineSyncController _offline;

  OrderRemoteDataSource(this._dio, this._offline);

  static const _ordersCacheKey = 'driver.assigned_orders.v1';
  static const _plansCacheKey = 'driver.dispatch_plans.v1';
  static const _proofConfigCacheKey = 'driver.delivery_proof_config.v1';

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
      final tasksResponse = await _dio.get(
        '/delivery-tasks/mine',
        queryParameters: {'include_completed': true},
      );
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
            return OrderModel.fromJson(
              Map<String, dynamic>.from(rawOrder),
            ).copyWith(
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

  /// Reports a failed delivery stop with reason, optional note, and photo evidence.
  Future<Map<String, dynamic>> reportOperationalIncident({
    String? orderId,
    String? planId,
    required String incidentType,
    String? notes,
    List<String>? evidenceUrls,
  }) async {
    final payload = <String, dynamic>{
      'incident_type': incidentType,
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

  /// Fetches the tenant's Proof of Delivery (POD) configuration for notes and photos.
  /// Caches the last known policy locally so offline/poor-network stops still enforce it.
  Future<DeliveryProofConfigModel> getDeliveryProofConfig() async {
    try {
      final response = await _dio.get('/tenants/me/delivery-proof-config');
      final resData = response.data;
      final rawMap = resData is Map && resData['data'] is Map
          ? Map<String, dynamic>.from(resData['data'] as Map)
          : (resData is Map
                ? Map<String, dynamic>.from(resData)
                : <String, dynamic>{});
      final config = DeliveryProofConfigModel.fromJson(rawMap);
      await _offline.putCache(_proofConfigCacheKey, jsonEncode(config.toJson()));
      return config;
    } catch (e) {
      debugPrint('[OrderRemoteDataSource] getDeliveryProofConfig fallback: $e');
      final cached = await _offline.getCache(_proofConfigCacheKey);
      if (cached != null) {
        try {
          final decoded = jsonDecode(cached);
          if (decoded is Map) {
            return DeliveryProofConfigModel.fromJson(
              Map<String, dynamic>.from(decoded),
            );
          }
        } catch (_) {}
      }
      return const DeliveryProofConfigModel();
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
