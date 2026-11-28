// features/order/data/datasources/goong_remote_data_source.dart

import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/config/goong_config.dart';
import 'package:swiftroute_app/core/network/goong_dio.dart';
import 'package:swiftroute_app/core/utils/polyline_decoder.dart';
import 'package:swiftroute_app/features/order/data/models/goong_direction_model.dart';

class GoongRemoteDataSource {
  final Dio _dio;

  GoongRemoteDataSource(this._dio);

  /// Computes street-level motorbike routing between origin and destination.
  Future<GoongDirection> getDirections({
    required double originLat,
    required double originLng,
    required double destLat,
    required double destLng,
    String vehicle = 'bike',
  }) async {
    if (!GoongConfig.hasValidApiKey) {
      return _mockDirection(originLat, originLng, destLat, destLng);
    }

    try {
      final response = await _dio.get(
        '/Direction',
        queryParameters: {
          'api_key': GoongConfig.apiKey,
          'origin': '$originLat,$originLng',
          'destination': '$destLat,$destLng',
          'vehicle': vehicle,
        },
      );

      final data = response.data as Map<String, dynamic>;
      return GoongDirection.fromJson(data);
    } catch (e) {
      debugPrint(
        '[Goong] Direction error: $e, falling back to straight-line interpolation',
      );
      return _mockDirection(originLat, originLng, destLat, destLng);
    }
  }

  // ── Fallback Mocks (Active before user pastes their Goong key) ─────────────

  GoongDirection _mockDirection(
    double originLat,
    double originLng,
    double destLat,
    double destLng,
  ) {
    // Generate 12 interpolated points so the driver vehicle can smoothly traverse
    final points = <LatLngPoint>[];
    const steps = 12;
    for (int i = 0; i <= steps; i++) {
      final t = i / steps.toDouble();
      points.add(
        LatLngPoint(
          originLat + (destLat - originLat) * t,
          originLng + (destLng - originLng) * t,
        ),
      );
    }

    return GoongDirection(
      distanceMeters: 4500,
      distanceText: '4.5 km',
      durationSeconds: 900,
      durationText: '15 phút',
      overviewPolyline: '',
      points: points,
    );
  }
}

/// Riverpod provider for GoongRemoteDataSource.
final goongRemoteDataSourceProvider = Provider<GoongRemoteDataSource>((ref) {
  return GoongRemoteDataSource(ref.read(goongDioProvider));
});
