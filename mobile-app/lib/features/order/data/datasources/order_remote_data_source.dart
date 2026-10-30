// features/order/data/datasources/order_remote_data_source.dart

import 'dart:async';
import 'dart:convert';
import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/network/dio.dart';
import 'package:swiftroute_app/features/order/data/models/order_model.dart';

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
      final response = await _dio.get('/orders/driver/assigned');
      final resData = response.data;
      final rawList = (resData is Map && resData['data'] is List)
          ? resData['data'] as List
          : (resData is List ? resData : null);
      if (rawList == null) return [];
      return rawList
          .whereType<Map>()
          .map((item) => OrderModel.fromJson(Map<String, dynamic>.from(item)))
          .toList();
    } catch (e, stack) {
      debugPrint('[OrderRemoteDataSource] getAssignedDriverOrders error: $e\n$stack');
      rethrow;
    }
  }

  /// Advances a driver's assigned order through the delivery state machine.
  Future<OrderModel> updateOrderStatus({
    required String orderId,
    required OrderStatus status,
  }) async {
    final response = await _dio.patch(
      '/orders/$orderId/status',
      data: '"${status.toServerString()}"',
      options: Options(contentType: Headers.jsonContentType),
    );
    final resData = response.data;
    final map = (resData is Map && resData['data'] is Map)
        ? resData['data'] as Map
        : (resData is Map ? resData : null);
    if (map == null) throw Exception('Failed to update order status');
    return OrderModel.fromJson(Map<String, dynamic>.from(map));
  }

  /// Confirms the payment and moves an owned order to PAID.
  Future<OrderModel> confirmPayment(OrderModel order) async {
    try {
      final response = await _dio.patch('/orders/${order.id}/pay');
      final resData = response.data;
      final map = (resData is Map && resData['data'] is Map)
          ? resData['data'] as Map
          : (resData is Map ? resData : null);
      if (map == null) return order;
      return OrderModel.fromJson(Map<String, dynamic>.from(map));
    } catch (error) {
      debugPrint('[OrderRemoteDataSource] confirmPayment warning: $error');
      // If payment auto-confirmation fails, still return order with current status
      return order;
    }
  }
}

/// Riverpod provider for OrderRemoteDataSource
final orderRemoteDataSourceProvider = Provider<OrderRemoteDataSource>((ref) {
  return OrderRemoteDataSource(ref.read(dioProvider));
});
