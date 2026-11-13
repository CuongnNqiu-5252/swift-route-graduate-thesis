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
import 'package:swiftroute_app/core/offline/offline_action.dart';
import 'package:swiftroute_app/core/offline/offline_sync_controller.dart';
import 'package:swiftroute_app/features/driver/data/models/driver_model.dart';
import 'package:swiftroute_app/features/driver/data/models/driver_earning_model.dart';
import 'package:swiftroute_app/features/driver/data/models/driver_notification_model.dart';

class DriverRemoteDataSource {
  final Dio _dio;
  final OfflineSyncController _offline;

  DriverRemoteDataSource(this._dio, this._offline);

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
    final body = <String, dynamic>{
      'lat': lat,
      'lng': lng,
      'accuracy_m': accuracyM,
      'speed_mps': speedMps,
      'bearing': bearing,
      'recorded_at': recordedAt.toUtc().toIso8601String(),
      'sequence': sequence,
    };
    Response<dynamic> response;
    try {
      response = await _dio.post('/drivers/me/location', data: body);
    } on DioException catch (error) {
      final status = error.response?.statusCode;
      if (status != null && status < 500 && status != 408 && status != 429) {
        rethrow;
      }
      await _offline.queue(
        OfflineAction.create(
          type: OfflineActionType.locationUpdate,
          method: 'POST',
          path: '/drivers/me/location',
          body: body,
        ),
      );
      throw const OfflineActionQueuedException(
        'Vị trí đã lưu trên máy và sẽ được đồng bộ sau',
      );
    }
    final resData = response.data;
    final map = (resData is Map && resData['data'] is Map)
        ? resData['data'] as Map
        : (resData is Map ? resData : null);
    if (map == null) throw Exception('Failed to update driver location');
    return DriverModel.fromJson(Map<String, dynamic>.from(map));
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
          (item) =>
              DriverNotificationModel.fromJson(Map<String, dynamic>.from(item)),
        )
        .toList();
  }
}

/// Riverpod provider for DriverRemoteDataSource
final driverRemoteDataSourceProvider = Provider<DriverRemoteDataSource>((ref) {
  return DriverRemoteDataSource(
    ref.read(dioProvider),
    ref.read(offlineSyncProvider.notifier),
  );
});
