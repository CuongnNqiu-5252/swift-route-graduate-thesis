// core/utils/location_helper.dart

import 'package:flutter/foundation.dart';
import 'package:geolocator/geolocator.dart';

/// Helper to request GPS permissions and retrieve the device's real-time position.
class LocationHelper {
  const LocationHelper._();

  /// Checks and requests location permission.
  /// Returns `true` if permission is granted, `false` otherwise.
  static Future<bool> ensurePermission() async {
    final serviceEnabled = await Geolocator.isLocationServiceEnabled();
    if (!serviceEnabled) {
      debugPrint('[LocationHelper] Location service is disabled on device.');
      return false;
    }

    var permission = await Geolocator.checkPermission();
    if (permission == LocationPermission.denied) {
      permission = await Geolocator.requestPermission();
      if (permission == LocationPermission.denied) {
        debugPrint('[LocationHelper] User denied location permission.');
        return false;
      }
    }

    if (permission == LocationPermission.deniedForever) {
      debugPrint('[LocationHelper] Location permission permanently denied.');
      return false;
    }

    return true;
  }

  /// Retrieves the current GPS position of the device.
  /// Returns `null` if permission was not granted or service was unavailable.
  static Future<Position?> getCurrentPosition() async {
    try {
      final hasPermission = await ensurePermission();
      if (!hasPermission) return null;

      return await Geolocator.getCurrentPosition(
        locationSettings: const LocationSettings(
          accuracy: LocationAccuracy.high,
          timeLimit: Duration(seconds: 10),
        ),
      );
    } catch (e) {
      debugPrint('[LocationHelper] Failed to get location: $e');
      return null;
    }
  }

  /// Calculates straight-line distance in kilometers between two GPS coordinates.
  static double distanceBetweenKm(
    double startLat,
    double startLng,
    double endLat,
    double endLng,
  ) {
    final distanceMeters = Geolocator.distanceBetween(
      startLat,
      startLng,
      endLat,
      endLng,
    );
    return distanceMeters / 1000.0;
  }
}
