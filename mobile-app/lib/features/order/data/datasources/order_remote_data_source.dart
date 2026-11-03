// features/order/data/datasources/order_remote_data_source.dart

import 'dart:async';
import 'dart:convert';
import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/network/dio.dart';
import 'package:swiftroute_app/features/order/data/models/order_model.dart';
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
          headers: {
            'Accept': 'text/event-stream',
            'Cache-Control': 'no-cache',
          },
        ),
      );

      final stream = response.data?.stream;
      if (stream == null) return;

      String? currentEventName;

      await for (final line in stream
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

  /// Lists all orders belonging to the authenticated customer.
  Future<List<OrderModel>> getMyOrders() async {
    try {
      final response = await _dio.get('/orders/me');
      final resData = response.data;
      final rawList = (resData is Map && resData['data'] is List)
          ? resData['data'] as List
          : (resData is List ? resData : null);
      if (rawList == null) return [];
      return rawList
          .whereType<Map>()
          .map((item) => OrderModel.fromJson(Map<String, dynamic>.from(item)))
          .toList();
    } catch (e) {
      debugPrint('[OrderRemoteDataSource] getMyOrders error: $e');
      return [];
    }
  }

  /// Returns the active route stops assigned to the authenticated driver.
  /// The gateway derives the driver's identity from the bearer token.
  Future<List<OrderModel>> getAssignedDriverOrders() async {
    try {
      final tasksResponse = await _dio.get('/delivery-tasks/mine');
      final tasksData = tasksResponse.data;
      final taskList = tasksData is List
          ? tasksData
          : (tasksData is Map && tasksData['data'] is List ? tasksData['data'] as List : <dynamic>[]);
      return taskList.whereType<Map>().map((rawTask) {
        final task = Map<String, dynamic>.from(rawTask);
        final rawOrder = task['order'];
        if (rawOrder is! Map) return null;
        return OrderModel.fromJson(Map<String, dynamic>.from(rawOrder)).copyWith(
          deliveryTaskId: task['task_id'] as String? ?? task['taskId'] as String?,
          deliveryTaskType: task['task_type'] as String? ?? task['taskType'] as String? ?? 'DELIVERY',
          deliveryTaskStatus: DeliveryTaskStatus.fromString(task['status'] as String?),
        );
      }).whereType<OrderModel>().toList();
    } catch (e, stack) {
      debugPrint('[OrderRemoteDataSource] getAssignedDriverOrders error: $e\n$stack');
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
        if (failureReason != null && failureReason.isNotEmpty) 'failure_reason': failureReason,
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
      _ => throw Exception('Trạng thái này không phải thao tác thực thi của tài xế'),
    };
    await _dio.patch(
      '/delivery-tasks/order/$orderId',
      data: {
        'status': taskStatus,
        if (failureReason != null && failureReason.isNotEmpty) 'failure_reason': failureReason,
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

  /// Records the exact COD amount collected for a delivered assigned order.
  Future<void> collectCod({
    required String orderId,
    required double amount,
    String? notes,
  }) async {
    await _dio.post(
      '/operations/orders/$orderId/cod-collections',
      data: {
        'amount': amount,
        if (notes != null && notes.isNotEmpty) 'notes': notes,
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

  Future<List<DriverCodCollectionModel>> getPendingCodHandover() async {
    final response = await _dio.get('/operations/cod-collections/mine');
    final data = response.data;
    final raw = data is Map && data['data'] is List ? data['data'] as List : <dynamic>[];
    return raw.whereType<Map>().map((item) => DriverCodCollectionModel.fromJson(Map<String, dynamic>.from(item))).toList();
  }

  Future<void> declareCodHandover({required String collectionId, required double amount}) async {
    await _dio.patch('/operations/cod-collections/$collectionId/declare', data: {'amount': amount});
  }

  Future<List<PackageHandoverModel>> getMyPackageHandovers() async {
    final response = await _dio.get('/custody/handovers/mine');
    final data = response.data;
    final raw = data is Map && data['data'] is List ? data['data'] as List : (data is List ? data : <dynamic>[]);
    return raw.whereType<Map>().map((item) => PackageHandoverModel.fromJson(Map<String, dynamic>.from(item))).toList();
  }

  Future<void> createPackageHandover({required String orderId, required String toDriverId}) async {
    final response = await _dio.get('/custody/orders/$orderId/packages');
    final data = response.data;
    final raw = data is Map && data['data'] is List ? data['data'] as List : <dynamic>[];
    final packageIds = raw.whereType<Map>().map((item) => item['package_id'] ?? item['packageId']).whereType<String>().toList();
    if (packageIds.isEmpty) throw StateError('Đơn chưa có kiện hàng đang được quản lý');
    await _dio.post('/custody/handovers', data: {
      'to_driver_id': toDriverId,
      'package_ids': packageIds,
      'reason': 'Tài xế bàn giao toàn bộ kiện của đơn',
    });
  }

  Future<void> actOnPackageHandover({required String handoverId, required String action}) async {
    await _dio.patch('/custody/handovers/$handoverId/$action', data: {'notes': 'Xác nhận trên ứng dụng tài xế'});
  }

  /// Confirms the payment and moves an owned order to PAID.
  Future<OrderModel> confirmPayment(OrderModel order) async {
    final response = await _dio.patch('/orders/${order.id}/pay');
    final resData = response.data;
    final map = (resData is Map && resData['data'] is Map)
        ? resData['data'] as Map
        : (resData is Map ? resData : null);
    if (map == null) {
      throw Exception('Máy chủ không trả về thông tin đơn sau khi thanh toán');
    }
    return OrderModel.fromJson(Map<String, dynamic>.from(map));
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
      data: {
        if (reason != null && reason.isNotEmpty) 'reason': reason,
      },
    );
    final resData = response.data;
    final map = (resData is Map && resData['data'] is Map)
        ? resData['data'] as Map
        : (resData is Map ? resData : null);
    if (map == null) throw Exception('Failed to cancel order');
    return OrderModel.fromJson(Map<String, dynamic>.from(map));
  }
}

/// Riverpod provider for OrderRemoteDataSource
final orderRemoteDataSourceProvider = Provider<OrderRemoteDataSource>((ref) {
  return OrderRemoteDataSource(ref.read(dioProvider));
});
