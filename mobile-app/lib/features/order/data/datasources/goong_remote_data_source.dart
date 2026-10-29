// features/order/data/datasources/goong_remote_data_source.dart

import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/config/goong_config.dart';
import 'package:swiftroute_app/core/network/goong_dio.dart';
import 'package:swiftroute_app/core/utils/polyline_decoder.dart';
import 'package:swiftroute_app/features/order/data/models/goong_direction_model.dart';
import 'package:swiftroute_app/features/order/data/models/goong_place_model.dart';

class GoongRemoteDataSource {
  final Dio _dio;

  GoongRemoteDataSource(this._dio);

  /// Searches for places/addresses matching [input] using Goong Place AutoComplete.
  Future<List<GoongPrediction>> searchPlaces(
    String input, {
    double? proximityLat = 10.032234,
    double? proximityLng = 105.786802,
  }) async {
    if (input.trim().isEmpty) return [];

    if (!GoongConfig.hasValidApiKey) {
      debugPrint(
        '[Goong] Using mock predictions (No valid GOONG_API_KEY set).',
      );
      return _mockPredictions(input);
    }

    try {
      final queryParams = <String, dynamic>{
        'api_key': GoongConfig.apiKey,
        'input': input,
        'limit': 8,
      };

      if (proximityLat != null && proximityLng != null) {
        queryParams['location'] = '$proximityLat,$proximityLng';
      }

      final response = await _dio.get(
        '/Place/AutoComplete',
        queryParameters: queryParams,
      );

      final data = response.data as Map<String, dynamic>;
      final predictions = data['predictions'] as List<dynamic>? ?? [];

      return predictions
          .map((item) => GoongPrediction.fromJson(item as Map<String, dynamic>))
          .toList();
    } catch (e) {
      debugPrint('[Goong] Place AutoComplete error: $e');
      return _mockPredictions(input);
    }
  }

  /// Fetches coordinate details for a specific [placeId].
  Future<GoongPlaceDetail?> getPlaceDetail(String placeId) async {
    if (!GoongConfig.hasValidApiKey) {
      return _mockPlaceDetail(placeId);
    }

    try {
      final response = await _dio.get(
        '/Place/Detail',
        queryParameters: {'api_key': GoongConfig.apiKey, 'place_id': placeId},
      );

      final data = response.data as Map<String, dynamic>;
      return GoongPlaceDetail.fromJson(data);
    } catch (e) {
      debugPrint('[Goong] Place Detail error: $e');
      return _mockPlaceDetail(placeId);
    }
  }

  /// Converts GPS coordinates ([lat], [lng]) into a formatted Vietnamese address.
  Future<String?> reverseGeocode(double lat, double lng) async {
    if (!GoongConfig.hasValidApiKey) {
      return 'Vị trí hiện tại ($lat, $lng)';
    }

    try {
      final response = await _dio.get(
        '/Geocode',
        queryParameters: {'api_key': GoongConfig.apiKey, 'latlng': '$lat,$lng'},
      );

      final data = response.data as Map<String, dynamic>;
      final results = data['results'] as List<dynamic>? ?? [];
      if (results.isNotEmpty) {
        final first = results[0] as Map<String, dynamic>;
        return first['formatted_address'] as String? ?? 'Vị trí xác định';
      }
      return 'Vị trí hiện tại ($lat, $lng)';
    } catch (e) {
      debugPrint('[Goong] Reverse Geocode error: $e');
      return 'Vị trí hiện tại ($lat, $lng)';
    }
  }

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

  List<GoongPrediction> _mockPredictions(String query) {
    final lower = query.toLowerCase();
    final allMocks = [
      const GoongPrediction(
        placeId: 'mock_1',
        description:
            'Chợ Bến Thành, Lê Lợi, Phường Bến Thành, Quận 1, TP. Hồ Chí Minh',
        mainText: 'Chợ Bến Thành',
        secondaryText: 'Lê Lợi, Phường Bến Thành, Quận 1, TP. Hồ Chí Minh',
      ),
      const GoongPrediction(
        placeId: 'mock_2',
        description:
            'Landmark 81, 720A Điện Biên Phủ, Phường 22, Bình Thạnh, TP. Hồ Chí Minh',
        mainText: 'Landmark 81',
        secondaryText:
            '720A Điện Biên Phủ, Phường 22, Bình Thạnh, TP. Hồ Chí Minh',
      ),
      const GoongPrediction(
        placeId: 'mock_3',
        description:
            'Đại học Bách Khoa TP.HCM, 268 Lý Thường Kiệt, Phường 14, Quận 10',
        mainText: 'Đại học Bách Khoa TP.HCM',
        secondaryText: '268 Lý Thường Kiệt, Phường 14, Quận 10',
      ),
      const GoongPrediction(
        placeId: 'mock_4',
        description: 'Hồ Hoàn Kiếm, Hàng Trống, Hoàn Kiếm, Hà Nội',
        mainText: 'Hồ Hoàn Kiếm',
        secondaryText: 'Hàng Trống, Hoàn Kiếm, Hà Nội',
      ),
    ];

    return allMocks
        .where(
          (p) =>
              p.description.toLowerCase().contains(lower) ||
              p.mainText.toLowerCase().contains(lower),
        )
        .toList();
  }

  GoongPlaceDetail _mockPlaceDetail(String placeId) {
    switch (placeId) {
      case 'mock_1':
        return const GoongPlaceDetail(
          placeId: 'mock_1',
          name: 'Chợ Bến Thành',
          formattedAddress: 'Lê Lợi, Phường Bến Thành, Quận 1, TP. Hồ Chí Minh',
          lat: 10.7725,
          lng: 106.6980,
        );
      case 'mock_2':
        return const GoongPlaceDetail(
          placeId: 'mock_2',
          name: 'Landmark 81',
          formattedAddress:
              '720A Điện Biên Phủ, Phường 22, Bình Thạnh, TP. Hồ Chí Minh',
          lat: 10.7950,
          lng: 106.7219,
        );
      case 'mock_3':
        return const GoongPlaceDetail(
          placeId: 'mock_3',
          name: 'Đại học Bách Khoa TP.HCM',
          formattedAddress: '268 Lý Thường Kiệt, Phường 14, Quận 10',
          lat: 10.7721,
          lng: 106.6579,
        );
      default:
        return const GoongPlaceDetail(
          placeId: 'mock_default',
          name: 'Điểm giao hàng mẫu',
          formattedAddress: 'Quận 1, TP. Hồ Chí Minh',
          lat: 10.7769,
          lng: 106.7009,
        );
    }
  }

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
