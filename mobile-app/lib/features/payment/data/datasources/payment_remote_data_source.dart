import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/network/dio.dart';
import 'package:swiftroute_app/features/payment/data/models/payment_model.dart';

class PaymentRemoteDataSource {
  const PaymentRemoteDataSource(this._dio);
  final Dio _dio;

  Future<PaymentModel> createMockPayment({
    required String orderId,
    required double amount,
  }) async {
    final response = await _dio.post('/payments/create', data: {
      'order_id': orderId,
      'amount': amount,
      'gateway': 'MOCK',
      'mode': 'MOCK',
    });
    return PaymentModel.fromJson(response.data['data'] as Map<String, dynamic>);
  }

  Future<PaymentModel> confirmMockPayment(String paymentId) async {
    final response = await _dio.post('/payments/$paymentId/confirm-mock');
    return PaymentModel.fromJson(response.data['data'] as Map<String, dynamic>);
  }
}

final paymentRemoteDataSourceProvider = Provider<PaymentRemoteDataSource>((ref) {
  return PaymentRemoteDataSource(ref.read(dioProvider));
});
