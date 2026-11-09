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
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/network/dio.dart';
import 'package:swiftroute_app/features/driver/data/models/driver_model.dart';
import 'package:swiftroute_app/features/driver/data/models/driver_earning_model.dart';
import 'package:swiftroute_app/features/driver/data/models/driver_notification_model.dart';

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

  Future<List<DriverModel>> getTenantDrivers() async {
    final response = await _dio.get('/drivers');
    final data = response.data;
    final raw = data is Map && data['data'] is List
        ? data['data'] as List
        : (data is List ? data : <dynamic>[]);
    return raw
        .whereType<Map>()
        .map((item) => DriverModel.fromJson(Map<String, dynamic>.from(item)))
        .toList();
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

  Future<DriverModel> updateMyProfile({
    required String fullName,
    required String phone,
    required String licensePlate,
    required String vehicleType,
    required int capacity,
  }) async {
    final response = await _dio.put(
      '/drivers/me/profile',
      data: {
        'full_name': fullName,
        'phone': phone,
        'license_plate': licensePlate,
        'vehicle_type': vehicleType,
        'capacity': capacity,
        'driver_type': 'DEDICATED',
      },
    );
    final raw = response.data;
    final map = raw is Map && raw['data'] is Map
        ? raw['data'] as Map
        : raw as Map;
    return DriverModel.fromJson(Map<String, dynamic>.from(map));
  }

  Future<DriverModel> submitMyProfile() async {
    final response = await _dio.post('/drivers/me/submit');
    final raw = response.data;
    final map = raw is Map && raw['data'] is Map
        ? raw['data'] as Map
        : raw as Map;
    return DriverModel.fromJson(Map<String, dynamic>.from(map));
  }

  /// Report current authenticated driver's GPS coordinates to the backend.
  Future<DriverModel> updateMyLocation({
    required double lat,
    required double lng,
    required double accuracyM,
    required double speedMps,
    required double bearing,
    required DateTime recordedAt,
    required int sequence,
  }) async {
    final response = await _dio.post(
      '/drivers/me/location',
      data: {
        'lat': lat,
        'lng': lng,
        'accuracy_m': accuracyM,
        'speed_mps': speedMps,
        'bearing': bearing,
        'recorded_at': recordedAt.toUtc().toIso8601String(),
        'sequence': sequence,
      },
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
    } catch (e) {
      debugPrint('[DriverRemoteDataSource] getDriverPublicInfo error: $e');
      rethrow;
    }
  }

  Future<DriverEarningsBundle> getMyEarnings() async {
    final responses = await Future.wait([
      _dio.get('/earnings/me/summary'),
      _dio.get('/earnings/me'),
    ]);
    final summaryRaw = responses[0].data;
    final earningsRaw = responses[1].data;
    final summaryMap = summaryRaw is Map && summaryRaw['data'] is Map
        ? Map<String, dynamic>.from(summaryRaw['data'] as Map)
        : Map<String, dynamic>.from(summaryRaw as Map);
    final rawList = earningsRaw is Map && earningsRaw['data'] is List
        ? earningsRaw['data'] as List
        : earningsRaw as List;
    return DriverEarningsBundle(
      summary: DriverEarningSummary.fromJson(summaryMap),
      earnings: rawList
          .whereType<Map>()
          .map(
            (item) =>
                DriverEarningModel.fromJson(Map<String, dynamic>.from(item)),
          )
          .toList(),
    );
  }

  /// Fetch driver-targeted notifications from /notifications/driver-inbox.
  Future<List<DriverNotificationModel>> getMyNotifications() async {
    final response = await _dio.get('/notifications/driver-inbox');
    final data = response.data;
    final raw = (data is Map && data['data'] is List)
        ? data['data'] as List
        : (data is List ? data : const <dynamic>[]);
    return raw
        .whereType<Map>()
        .map(
          (item) => DriverNotificationModel.fromJson(
            Map<String, dynamic>.from(item),
          ),
        )
        .toList();
  }
}

/// Riverpod provider for DriverRemoteDataSource
final driverRemoteDataSourceProvider = Provider<DriverRemoteDataSource>((ref) {
  return DriverRemoteDataSource(ref.read(dioProvider));
});
