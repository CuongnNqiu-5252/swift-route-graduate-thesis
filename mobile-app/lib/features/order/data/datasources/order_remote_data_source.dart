// features/order/data/datasources/order_remote_data_source.dart

import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/network/dio.dart';
import 'package:swiftroute_app/features/order/data/models/order_model.dart';

class OrderRemoteDataSource {
  final Dio _dio;

  OrderRemoteDataSource(this._dio);

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
    if (tenantId != null && tenantId.isNotEmpty) {
      headers['X-Tenant-Id'] = tenantId;
    }

    try {
      final response = await _dio.post(
        '/orders',
        data: body,
        options: Options(headers: headers),
      );

      final data = response.data['data'] as Map<String, dynamic>;
      return OrderModel.fromJson(data);
    } catch (e) {
      debugPrint('[OrderRemoteDataSource] createOrder network error: $e, using mock fallback.');
      return _mockCreatedOrder(
        pickupLat: pickupLat,
        pickupLng: pickupLng,
        pickupAddress: pickupAddress,
        deliveryLat: deliveryLat,
        deliveryLng: deliveryLng,
        deliveryAddress: deliveryAddress,
        weightKg: weightKg,
        priority: priority,
      );
    }
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
      final dataList = response.data['data'] as List<dynamic>? ?? [];
      return dataList
          .map((item) => OrderModel.fromJson(item as Map<String, dynamic>))
          .toList();
    } catch (e) {
      debugPrint('[OrderRemoteDataSource] getMyOrders error: $e');
      return [];
    }
  }

  /// Returns the active route stops assigned to the authenticated driver.
  /// The gateway derives the driver's identity from the bearer token.
  Future<List<OrderModel>> getAssignedDriverOrders() async {
    final response = await _dio.get('/orders/driver/assigned');
    final dataList = response.data['data'] as List<dynamic>? ?? [];
    return dataList
        .map((item) => OrderModel.fromJson(item as Map<String, dynamic>))
        .toList();
  }

  /// Advances a driver's assigned order through the delivery state machine.
  Future<OrderModel> updateOrderStatus({
    required String orderId,
    required OrderStatus status,
  }) async {
    final response = await _dio.patch(
      '/orders/$orderId/status',
      data: status.toServerString(),
      options: Options(contentType: Headers.jsonContentType),
    );
    final data = response.data['data'] as Map<String, dynamic>;
    return OrderModel.fromJson(data);
  }

  /// Confirms the demo payment and moves an owned order to PAID.
  Future<OrderModel> confirmPayment(OrderModel order) async {
    try {
      final response = await _dio.patch('/orders/${order.id}/pay');
      final data = response.data['data'] as Map<String, dynamic>;
      return OrderModel.fromJson(data);
    } catch (error) {
      // Keep the offline demo flow usable when the API is intentionally mocked.
      if (order.id.startsWith('mock-order-')) {
        return order.copyWith(status: OrderStatus.paid);
      }
      debugPrint('[OrderRemoteDataSource] confirmPayment error: $error');
      rethrow;
    }
  }

  // ── Fallback Mock ─────────────────────────────────────────────────────────

  OrderModel _mockCreatedOrder({
    required double pickupLat,
    required double pickupLng,
    required String pickupAddress,
    required double deliveryLat,
    required double deliveryLng,
    required String deliveryAddress,
    required double weightKg,
    required String priority,
  }) {
    return OrderModel(
      id: 'mock-order-${DateTime.now().millisecondsSinceEpoch}',
      tenantId: '00000000-0000-0000-0000-000000000001',
      tenantName: 'SwiftRoute Express',
      customerId: '00000000-0000-0000-0000-000000000002',
      pickupLat: pickupLat,
      pickupLng: pickupLng,
      pickupAddress: pickupAddress,
      deliveryLat: deliveryLat,
      deliveryLng: deliveryLng,
      deliveryAddress: deliveryAddress,
      timeWindowStart: DateTime.now().toUtc(),
      timeWindowEnd: DateTime.now().toUtc().add(const Duration(hours: 4)),
      weightKg: weightKg,
      priority: priority,
      status: OrderStatus.paid,
      assignedDriverId: 'mock-driver-uuid',
      createdAt: DateTime.now(),
    );
  }
}

/// Riverpod provider for OrderRemoteDataSource
final orderRemoteDataSourceProvider = Provider<OrderRemoteDataSource>((ref) {
  return OrderRemoteDataSource(ref.read(dioProvider));
});
