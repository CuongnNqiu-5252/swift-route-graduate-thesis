// core/utils/polyline_decoder.dart

import 'package:flutter_polyline_points/flutter_polyline_points.dart';
import 'package:maplibre_gl/maplibre_gl.dart';

export 'package:maplibre_gl/maplibre_gl.dart' show LatLng;

/// Type alias for backward compatibility across existing files.
typedef LatLngPoint = LatLng;

/// Decodes an encoded polyline string (standard Google/Goong polyline format)
/// into a list of geographic [LatLng]s using `flutter_polyline_points`.
class PolylineDecoder {
  const PolylineDecoder._();

  /// Decodes an encoded polyline string into coordinates.
  static List<LatLng> decode(String encodedPolyline) {
    if (encodedPolyline.isEmpty) return const [];
    try {
      final points = PolylinePoints.decodePolyline(encodedPolyline);

      return points
          .map((point) => LatLng(point.latitude, point.longitude))
          .toList();
    } catch (_) {
      return const [];
    }
  }
}
