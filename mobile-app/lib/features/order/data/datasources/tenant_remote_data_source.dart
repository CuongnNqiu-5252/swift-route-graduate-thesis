// features/order/data/datasources/tenant_remote_data_source.dart

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/network/dio.dart';
import 'package:swiftroute_app/features/order/data/models/tenant_model.dart';

class TenantRemoteDataSource {
  final Dio _dio;

  TenantRemoteDataSource(this._dio);

  /// Fetches approved tenants/shops nearby given coordinates ([lat], [lng]).
  Future<List<TenantModel>> getNearbyTenants({
    required double lat,
    required double lng,
    int limit = 10,
  }) async {
    final response = await _dio.get(
      '/tenants/nearby',
      queryParameters: {'lat': lat, 'lng': lng, 'limit': limit},
    );

    final body = response.data;
    if (body is Map && body['data'] is List) {
      final list = body['data'] as List<dynamic>;
      return list
          .whereType<Map>()
          .map((item) => TenantModel.fromJson(Map<String, dynamic>.from(item)))
          .toList();
    }
    return [];
  }
}

final tenantRemoteDataSourceProvider = Provider<TenantRemoteDataSource>((ref) {
  return TenantRemoteDataSource(ref.watch(dioProvider));
});
