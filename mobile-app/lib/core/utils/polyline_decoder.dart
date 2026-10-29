// core/utils/polyline_decoder.dart

/// Data holder for a decoded geographic coordinate point.
class LatLngPoint {
  final double latitude;
  final double longitude;

  const LatLngPoint(this.latitude, this.longitude);

  @override
  String toString() => 'LatLngPoint($latitude, $longitude)';

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is LatLngPoint &&
          other.latitude == latitude &&
          other.longitude == longitude;

  @override
  int get hashCode => Object.hash(latitude, longitude);
}

/// Decodes an encoded polyline string (standard Google/Goong polyline format)
/// into a list of geographic [LatLngPoint]s.
class PolylineDecoder {
  const PolylineDecoder._();

  /// Decodes an encoded polyline string into coordinates.
  static List<LatLngPoint> decode(String encoded) {
    final List<LatLngPoint> points = [];
    int index = 0;
    final int len = encoded.length;
    int lat = 0;
    int lng = 0;

    while (index < len) {
      int b;
      int shift = 0;
      int result = 0;
      do {
        b = encoded.codeUnitAt(index++) - 63;
        result |= (b & 0x1f) << shift;
        shift += 5;
      } while (b >= 0x20);

      final int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
      lat += dlat;

      shift = 0;
      result = 0;
      do {
        b = encoded.codeUnitAt(index++) - 63;
        result |= (b & 0x1f) << shift;
        shift += 5;
      } while (b >= 0x20);

      final int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
      lng += dlng;

      points.add(LatLngPoint(lat / 1e5, lng / 1e5));
    }

    return points;
  }
}
