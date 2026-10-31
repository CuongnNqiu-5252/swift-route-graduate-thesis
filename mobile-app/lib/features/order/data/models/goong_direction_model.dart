// features/order/data/models/goong_direction_model.dart

import 'package:swiftroute_app/core/utils/polyline_decoder.dart';

/// Represents routing and direction results returned from Goong Direction API.
class GoongDirection {
  final double distanceMeters;
  final String distanceText;
  final int durationSeconds;
  final String durationText;
  final String overviewPolyline;
  final List<LatLngPoint> points;

  const GoongDirection({
    required this.distanceMeters,
    required this.distanceText,
    required this.durationSeconds,
    required this.durationText,
    required this.overviewPolyline,
    required this.points,
  });

  double get distanceKm => distanceMeters / 1000.0;
  int get durationMinutes => (durationSeconds / 60.0).ceil();

  factory GoongDirection.fromJson(Map<String, dynamic> json) {
    final routes = json['routes'] as List<dynamic>? ?? [];
    if (routes.isEmpty) {
      return const GoongDirection(
        distanceMeters: 0,
        distanceText: '0 km',
        durationSeconds: 0,
        durationText: '0 phút',
        overviewPolyline: '',
        points: [],
      );
    }

    final firstRoute = routes[0] as Map<String, dynamic>;
    final legs = firstRoute['legs'] as List<dynamic>? ?? [];
    final firstLeg = legs.isNotEmpty ? legs[0] as Map<String, dynamic> : null;

    final distanceObj = firstLeg?['distance'] as Map<String, dynamic>?;
    final durationObj = firstLeg?['duration'] as Map<String, dynamic>?;
    final polylineObj = firstRoute['overview_polyline'] as Map<String, dynamic>?;

    final polylineStr = polylineObj?['points'] as String? ?? '';
    final decodedPoints = polylineStr.isNotEmpty
        ? PolylineDecoder.decode(polylineStr)
        : <LatLngPoint>[];

    return GoongDirection(
      distanceMeters: (distanceObj?['value'] as num?)?.toDouble() ?? 0.0,
      distanceText: distanceObj?['text'] as String? ?? '0 km',
      durationSeconds: (durationObj?['value'] as num?)?.toInt() ?? 0,
      durationText: durationObj?['text'] as String? ?? '0 phút',
      overviewPolyline: polylineStr,
      points: decodedPoints,
    );
  }
}
