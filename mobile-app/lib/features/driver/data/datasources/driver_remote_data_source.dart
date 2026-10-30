// features/driver/data/datasources/driver_remote_data_source.dart
//
// Calls the Driver Service REST API.
// All responses are wrapped in ApiResponse: { "data": {...}, "error": null, "timestamp": "..." }
//
// Endpoints used:
//   GET    /drivers/{driverId}             -> DriverResponse
//   PATCH  /drivers/{driverId}/availability -> DriverResponse
//   PATCH  /drivers/{driverId}/location    -> DriverResponse

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/network/dio.dart';
import 'package:swiftroute_app/features/driver/data/models/driver_model.dart';

class DriverRemoteDataSource {
  final Dio _dio;

  DriverRemoteDataSource(this._dio);

  /// Fetch the current authenticated driver's profile from /drivers/me.
  Future<DriverModel> getMyProfile() async {
    final response = await _dio.get('/drivers/me');
    final resData = response.data;
    final map = (resData is Map && resData['data'] is Map)
        ? resData['data'] as Map
        : (resData is Map ? resData : null);
    if (map == null) throw Exception('No driver profile data found');
    return DriverModel.fromJson(Map<String, dynamic>.from(map));
  }

  /// Toggle the authenticated driver's availability status.
  Future<DriverModel> updateMyAvailability({required bool isAvailable}) async {
    final response = await _dio.patch(
      '/drivers/me/availability',
      data: {'is_available': isAvailable},
    );
    final resData = response.data;
    final map = (resData is Map && resData['data'] is Map)
        ? resData['data'] as Map
        : (resData is Map ? resData : null);
    if (map == null) throw Exception('Failed to update driver availability');
    return DriverModel.fromJson(Map<String, dynamic>.from(map));
  }

  /// Report current authenticated driver's GPS coordinates to the backend.
  Future<DriverModel> updateMyLocation({
    required double lat,
    required double lng,
  }) async {
    final response = await _dio.patch(
      '/drivers/me/location',
      data: {'lat': lat, 'lng': lng},
    );
    final resData = response.data;
    final map = (resData is Map && resData['data'] is Map)
        ? resData['data'] as Map
        : (resData is Map ? resData : null);
    if (map == null) throw Exception('Failed to update driver location');
    return DriverModel.fromJson(Map<String, dynamic>.from(map));
  }

  /// Fetch the driver profile by driverId (UUID string).
  Future<DriverModel> getDriverProfile(String driverId) async {
    final response = await _dio.get('/drivers/$driverId');
    final resData = response.data;
    final map = (resData is Map && resData['data'] is Map)
        ? resData['data'] as Map
        : (resData is Map ? resData : null);
    if (map == null) throw Exception('Driver profile not found');
    return DriverModel.fromJson(Map<String, dynamic>.from(map));
  }

  /// Toggle the driver's availability status.
  /// [driverId] is the driver entity UUID (not the auth userId).
  Future<DriverModel> updateAvailability({
    required String driverId,
    required bool isAvailable,
  }) async {
    final response = await _dio.patch(
      '/drivers/$driverId/availability',
      data: {'is_available': isAvailable},
    );
    final resData = response.data;
    final map = (resData is Map && resData['data'] is Map)
        ? resData['data'] as Map
        : (resData is Map ? resData : null);
    if (map == null) throw Exception('Failed to update availability');
    return DriverModel.fromJson(Map<String, dynamic>.from(map));
  }

  /// Report current GPS coordinates to the backend.
  Future<DriverModel> updateLocation({
    required String driverId,
    required double lat,
    required double lng,
  }) async {
    final response = await _dio.patch(
      '/drivers/$driverId/location',
      data: {'lat': lat, 'lng': lng},
    );
    final resData = response.data;
    final map = (resData is Map && resData['data'] is Map)
        ? resData['data'] as Map
        : (resData is Map ? resData : null);
    if (map == null) throw Exception('Failed to update location');
    return DriverModel.fromJson(Map<String, dynamic>.from(map));
  }

  /// Fetch public driver profile (for customers tracking an order).
  Future<Map<String, dynamic>> getDriverPublicInfo(String driverId) async {
    try {
      final response = await _dio.get('/drivers/$driverId/public');
      final resData = response.data;
      if (resData is Map && resData['data'] is Map) {
        return Map<String, dynamic>.from(resData['data'] as Map);
      }
      return {};
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
