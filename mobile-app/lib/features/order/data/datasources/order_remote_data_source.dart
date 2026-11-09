// features/order/data/datasources/order_remote_data_source.dart

import 'dart:async';
import 'dart:convert';
import 'dart:math';
import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/network/dio.dart';
import 'package:swiftroute_app/features/order/data/models/order_model.dart';
import 'package:swiftroute_app/features/driver/data/models/dispatch_plan_model.dart';
import 'package:swiftroute_app/features/driver/data/models/driver_cod_collection_model.dart';
import 'package:swiftroute_app/features/driver/data/models/package_handover_model.dart';

class OrderRemoteDataSource {
  final Dio _dio;

  OrderRemoteDataSource(this._dio);

  /// Subscribes to the live Server-Sent Events (SSE) tracking stream for [orderId].
  /// Yields decoded events: location_update, connected, status_update, etc.
  Stream<Map<String, dynamic>> streamOrderTracking(String orderId) async* {
    try {
      final response = await _dio.get<ResponseBody>(
        '/orders/$orderId/tracking/stream',
        options: Options(
          responseType: ResponseType.stream,
          receiveTimeout: const Duration(hours: 1),
          headers: {'Accept': 'text/event-stream', 'Cache-Control': 'no-cache'},
        ),
      );

      final stream = response.data?.stream;
      if (stream == null) return;

      String? currentEventName;

      await for (final line
          in stream
              .cast<List<int>>()
              .transform(utf8.decoder)
              .transform(const LineSplitter())) {
        final trimmed = line.trim();
        if (trimmed.isEmpty) continue;

        if (trimmed.startsWith('event:')) {
          currentEventName = trimmed.substring(6).trim();
        } else if (trimmed.startsWith('data:')) {
          final dataString = trimmed.substring(5).trim();
          try {
            final decoded = jsonDecode(dataString);
            if (decoded is Map<String, dynamic>) {
              decoded['sse_event'] = currentEventName ?? 'message';
              yield decoded;
            }
          } catch (e) {
            debugPrint('[OrderRemoteDataSource] SSE JSON parse error: $e');
          }
        }
      }
    } catch (e) {
      debugPrint('[OrderRemoteDataSource] streamOrderTracking error: $e');
      rethrow;
    }
  }

  /// Creates a new delivery order.
  Future<OrderModel> createOrder({
    required double pickupLat,
    required double pickupLng,
    required String pickupAddress,
    required double deliveryLat,
    required double deliveryLng,
    required String deliveryAddress,
    required double weightKg,
    String priority = 'NORMAL',
    String? tenantId,
  }) async {
    final now = DateTime.now().toUtc();
    final body = {
      'pickup_lat': pickupLat,
      'pickup_lng': pickupLng,
      'pickup_address': pickupAddress,
      'delivery_lat': deliveryLat,
      'delivery_lng': deliveryLng,
      'delivery_address': deliveryAddress,
      'time_window_start': now.toIso8601String(),
      'time_window_end': now.add(const Duration(hours: 4)).toIso8601String(),
      'weight_kg': weightKg,
      'priority': priority,
    };

    final headers = <String, dynamic>{};
    final effectiveTenant = (tenantId != null && tenantId.isNotEmpty)
        ? tenantId
        : 'a1111111-1111-1111-1111-111111111111';
    headers['X-Tenant-Id'] = effectiveTenant;

    final response = await _dio.post(
      '/orders',
      data: body,
      options: Options(headers: headers),
    );

    final data = response.data['data'] as Map<String, dynamic>;
    return OrderModel.fromJson(data);
  }

  /// Retrieves order details by [orderId].
  Future<OrderModel> getOrderById(String orderId) async {
    try {
      final response = await _dio.get('/orders/$orderId');
      final data = response.data['data'] as Map<String, dynamic>;
      return OrderModel.fromJson(data);
    } catch (e) {
      debugPrint('[OrderRemoteDataSource] getOrderById error: $e');
      rethrow;
    }
  }

  /// Compatibility method for legacy customer order list screens.
  Future<List<OrderModel>> getMyOrders() async {
    return [];
  }

  /// Compatibility method for legacy customer order form (orders are created ready for dispatch).
  Future<OrderModel> confirmPayment(OrderModel order) async {
    return order;
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
      return taskList
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
    } catch (e, stack) {
      debugPrint(
        '[OrderRemoteDataSource] getAssignedDriverOrders error: $e\n$stack',
      );
      return [];
    }
  }

  Future<void> updateDeliveryTaskStatus({
    required String taskId,
    required DeliveryTaskStatus status,
    String? failureReason,
    String? notes,
    String? proofUrl,
  }) async {
    await _dio.patch(
      '/delivery-tasks/$taskId',
      data: {
        'status': status.serverValue,
        if (failureReason != null && failureReason.isNotEmpty)
          'failure_reason': failureReason,
        if (notes != null && notes.isNotEmpty) 'notes': notes,
        if (proofUrl != null && proofUrl.isNotEmpty) 'proof_url': proofUrl,
      },
    );
  }

  /// Advances a driver's assigned order through the delivery state machine.
  Future<OrderModel> updateOrderStatus({
    required String orderId,
    required OrderStatus status,
    String? failureReason,
    String? notes,
    String? proofUrl,
  }) async {
    final taskStatus = switch (status) {
      OrderStatus.planned => 'ACCEPTED',
      OrderStatus.inTransit => 'OUT_FOR_DELIVERY',
      OrderStatus.delivered => 'COMPLETED',
      OrderStatus.failed => 'FAILED',
      _ => throw Exception(
        'Trạng thái này không phải thao tác thực thi của tài xế',
      ),
    };
    await _dio.patch(
      '/delivery-tasks/order/$orderId',
      data: {
        'status': taskStatus,
        if (failureReason != null && failureReason.isNotEmpty)
          'failure_reason': failureReason,
        if (notes != null && notes.isNotEmpty) 'notes': notes,
        if (proofUrl != null && proofUrl.isNotEmpty) 'proof_url': proofUrl,
      },
    );
    final response = await _dio.get('/orders/$orderId');
    final resData = response.data;
    final map = (resData is Map && resData['data'] is Map)
        ? resData['data'] as Map
        : (resData is Map ? resData : null);
    if (map == null) throw Exception('Failed to update order status');
    return OrderModel.fromJson(Map<String, dynamic>.from(map));
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
    await _dio.post(
      '/cod/orders/$orderId/collections',
      data: {
        'operation_id': _operationId(),
        'amount': amount,
        'outcome': outcome,
        if (reason != null && reason.isNotEmpty) 'reason': reason,
        if (evidenceUrl != null && evidenceUrl.isNotEmpty)
          'evidence_url': evidenceUrl,
      },
    );
  }

  /// Creates the auditable incident record used by the operations queue.
  Future<void> createOperationalIncident({
    required String orderId,
    required String incidentType,
    String? notes,
  }) async {
    await _dio.post(
      '/operations/orders/$orderId/incidents',
      data: {
        'incident_type': incidentType,
        if (notes != null && notes.isNotEmpty) 'notes': notes,
      },
    );
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
    await _dio.post(
      '/cod/handovers',
      data: {
        'operation_id': _operationId(),
        'collection_ids': collectionIds,
        'order_ids': collectionIds,
        'declared_total': declaredTotal,
        if (notes != null && notes.isNotEmpty) 'notes': notes,
      },
    );
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
    required String toDriverId,
  }) async {
    final response = await _dio.get('/custody/orders/$orderId/packages');
    final data = response.data;
    final raw = data is Map && data['data'] is List
        ? data['data'] as List
        : <dynamic>[];
    final packageIds = raw
        .whereType<Map>()
        .map((item) => item['package_id'] ?? item['packageId'])
        .whereType<String>()
        .toList();
    if (packageIds.isEmpty) {
      throw StateError('Đơn chưa có kiện hàng đang được quản lý');
    }
    await _dio.post(
      '/custody/handovers',
      data: {
        'to_driver_id': toDriverId,
        'package_ids': packageIds,
        'reason': 'Tài xế bàn giao toàn bộ kiện của đơn',
      },
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

  /// Cancels an active order before delivery.
  Future<OrderModel> cancelOrder({
    required String orderId,
    String? reason,
  }) async {
    final response = await _dio.patch(
      '/orders/$orderId/cancel',
      data: {if (reason != null && reason.isNotEmpty) 'reason': reason},
    );
    final resData = response.data;
    final map = (resData is Map && resData['data'] is Map)
        ? resData['data'] as Map
        : (resData is Map ? resData : null);
    if (map == null) throw Exception('Failed to cancel order');
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
      return rawList
          .whereType<Map>()
          .map(
            (item) =>
                DispatchPlanModel.fromJson(Map<String, dynamic>.from(item)),
          )
          .toList();
    } catch (e) {
      debugPrint('[OrderRemoteDataSource] getDriverDispatchPlans error: $e');
      return [];
    }
  }

  /// Driver acknowledges receipt and acceptance of a dispatched route plan.
  Future<DispatchPlanModel> acknowledgeDispatchPlan(String planId) async {
    final response = await _dio.post(
      '/orders/dispatch/plans/$planId/acknowledge',
    );
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
    final response = await _dio.post(
      '/orders/dispatch/plans/$planId/decline',
      data: {'reason': reason},
    );
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
    return map['url']?.toString() ?? '';
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

    final response = await _dio.post('/operations/incidents', data: payload);
    final resData = response.data;
    final map = (resData is Map && resData['data'] is Map)
        ? resData['data'] as Map
        : (resData is Map ? resData : <String, dynamic>{});
    return Map<String, dynamic>.from(map);
  }

  /// Fetches return requests for driver.
  Future<List<Map<String, dynamic>>> getMyReturnRequests() async {
    try {
      final response = await _dio.get('/operations/returns');
      final resData = response.data;
      final rawList = (resData is Map && resData['data'] is List)
          ? resData['data'] as List
          : (resData is List ? resData : <dynamic>[]);
      return rawList
          .whereType<Map>()
          .map((item) => Map<String, dynamic>.from(item))
          .toList();
    } catch (e) {
      debugPrint('[OrderRemoteDataSource] getMyReturnRequests error: $e');
      return [];
    }
  }

  /// Batch-accepts all (or specified) ASSIGNED tasks for the driver in 1 tap.
  Future<void> batchAcceptDeliveryTasks({
    List<String>? taskIds,
    List<String>? orderIds,
    String? notes,
  }) async {
    await _dio.post(
      '/delivery-tasks/batch-accept',
      data: {
        if (taskIds != null && taskIds.isNotEmpty) 'task_ids': taskIds,
        if (orderIds != null && orderIds.isNotEmpty) 'order_ids': orderIds,
        if (notes != null && notes.isNotEmpty) 'notes': notes,
      },
    );
  }

  /// Single-swipe batch pickup: acquires package custody and moves grouped depot tasks directly to OUT_FOR_DELIVERY.
  Future<void> batchPickupDeliveryTasks({
    List<String>? taskIds,
    List<String>? orderIds,
    String? notes,
  }) async {
    await _dio.post(
      '/delivery-tasks/batch-pickup',
      data: {
        if (taskIds != null && taskIds.isNotEmpty) 'task_ids': taskIds,
        if (orderIds != null && orderIds.isNotEmpty) 'order_ids': orderIds,
        if (notes != null && notes.isNotEmpty) 'notes': notes,
      },
    );
  }

  /// Driver confirms arrival at return location warehouse.
  Future<void> arriveAtReturnLocation(String returnRequestId) async {
    await _dio.patch('/operations/returns/$returnRequestId/arrive');
  }

  /// Driver hands over returned goods to warehouse staff (awaits receipt confirmation).
  Future<void> handoverReturnGoods(String returnRequestId) async {
    await _dio.patch('/operations/returns/$returnRequestId/handover');
  }
}

/// Riverpod provider for OrderRemoteDataSource
final orderRemoteDataSourceProvider = Provider<OrderRemoteDataSource>((ref) {
  return OrderRemoteDataSource(ref.read(dioProvider));
});
