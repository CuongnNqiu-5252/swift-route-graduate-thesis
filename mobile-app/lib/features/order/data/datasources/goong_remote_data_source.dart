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
            'Bến Ninh Kiều, Đường Hai Bà Trưng, Phường Tân An, Quận Ninh Kiều, Cần Thơ',
        mainText: 'Bến Ninh Kiều',
        secondaryText: 'Hai Bà Trưng, Phường Tân An, Quận Ninh Kiều, Cần Thơ',
      ),
      const GoongPrediction(
        placeId: 'mock_2',
        description:
            'Đại học Cần Thơ, Đường 3 Tháng 2, Phường Xuân Khánh, Quận Ninh Kiều, Cần Thơ',
        mainText: 'Đại học Cần Thơ (Khu II)',
        secondaryText: 'Đường 3 Tháng 2, Phường Xuân Khánh, Quận Ninh Kiều, Cần Thơ',
      ),
      const GoongPrediction(
        placeId: 'mock_3',
        description:
            'Vincom Plaza Xuân Khánh, 209 Đường 30 Tháng 4, Phường Xuân Khánh, Ninh Kiều, Cần Thơ',
        mainText: 'Vincom Plaza Xuân Khánh',
        secondaryText: '209 Đường 30 Tháng 4, Xuân Khánh, Ninh Kiều, Cần Thơ',
      ),
      const GoongPrediction(
        placeId: 'mock_4',
        description:
            'Chợ nổi Cái Răng, Phường Lê Bình, Quận Cái Răng, TP. Cần Thơ',
        mainText: 'Chợ nổi Cái Răng',
        secondaryText: 'Phường Lê Bình, Quận Cái Răng, TP. Cần Thơ',
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
          name: 'Bến Ninh Kiều',
          formattedAddress: 'Đường Hai Bà Trưng, Phường Tân An, Quận Ninh Kiều, Cần Thơ',
          lat: 10.0341,
          lng: 105.7876,
        );
      case 'mock_2':
        return const GoongPlaceDetail(
          placeId: 'mock_2',
          name: 'Đại học Cần Thơ (Khu II)',
          formattedAddress:
              'Đường 3 Tháng 2, Phường Xuân Khánh, Quận Ninh Kiều, Cần Thơ',
          lat: 10.0299,
          lng: 105.7706,
        );
      case 'mock_3':
        return const GoongPlaceDetail(
          placeId: 'mock_3',
          name: 'Vincom Plaza Xuân Khánh',
          formattedAddress: '209 Đường 30 Tháng 4, Phường Xuân Khánh, Ninh Kiều, Cần Thơ',
          lat: 10.0279,
          lng: 105.7766,
        );
      case 'mock_4':
        return const GoongPlaceDetail(
          placeId: 'mock_4',
          name: 'Chợ nổi Cái Răng',
          formattedAddress: 'Phường Lê Bình, Quận Cái Răng, TP. Cần Thơ',
          lat: 9.9982,
          lng: 105.7465,
        );
      default:
        return const GoongPlaceDetail(
          placeId: 'mock_default',
          name: 'Điểm giao hàng mẫu Cần Thơ',
          formattedAddress: 'Đại lộ Hoà Bình, Phường Tân An, Quận Ninh Kiều, Cần Thơ',
          lat: 10.0371,
          lng: 105.7828,
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
