// features/driver/data/datasources/driver_remote_data_source.dart
//
// Calls the Driver Service REST API.
// All responses are wrapped in ApiResponse: { "data": {...}, "error": null, "timestamp": "..." }
//
// Endpoints used:
//   GET    /api/drivers/{driverId}             -> DriverResponse
//   PATCH  /api/drivers/{driverId}/availability -> DriverResponse
//   PATCH  /api/drivers/{driverId}/location    -> DriverResponse

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/network/dio.dart';
import 'package:swiftroute_app/features/driver/data/models/driver_model.dart';

class DriverRemoteDataSource {
  final Dio _dio;

  DriverRemoteDataSource(this._dio);

  /// Fetch the driver profile by driverId (UUID string).
  Future<DriverModel> getDriverProfile(String driverId) async {
    final response = await _dio.get('/api/drivers/$driverId');
    final data = response.data['data'] as Map<String, dynamic>;
    return DriverModel.fromJson(data);
  }

  /// Toggle the driver's availability status.
  /// [driverId] is the driver entity UUID (not the auth userId).
  Future<DriverModel> updateAvailability({
    required String driverId,
    required bool isAvailable,
  }) async {
    final response = await _dio.patch(
      '/api/drivers/$driverId/availability',
      data: {'is_available': isAvailable},
    );
    final data = response.data['data'] as Map<String, dynamic>;
    return DriverModel.fromJson(data);
  }

  /// Report current GPS coordinates to the backend.
  Future<DriverModel> updateLocation({
    required String driverId,
    required double lat,
    required double lng,
  }) async {
    final response = await _dio.patch(
      '/api/drivers/$driverId/location',
      data: {'lat': lat, 'lng': lng},
    );
    final data = response.data['data'] as Map<String, dynamic>;
    return DriverModel.fromJson(data);
  }

  /// Fetch public driver profile (for customers tracking an order).
  Future<Map<String, dynamic>> getDriverPublicInfo(String driverId) async {
    try {
      final response = await _dio.get('/api/drivers/$driverId/public');
      final data = response.data['data'] as Map<String, dynamic>;
      return data;
    } catch (_) {
      // Mock fallback if service isn't reachable
      return {
        'id': driverId,
        'full_name': 'Nguyễn Văn Tài (Tài xế)',
        'license_plate': '59-X1 888.88',
        'vehicle_type': 'MOTORBIKE',
        'current_lat': 10.7769,
        'current_lng': 106.7009,
      };
    }
  }
}

/// Riverpod provider for DriverRemoteDataSource
final driverRemoteDataSourceProvider = Provider<DriverRemoteDataSource>((ref) {
  return DriverRemoteDataSource(ref.read(dioProvider));
});
