// config/goong_config.dart

/// Configuration for Goong Map & Goong REST APIs.
/// Keys are provided via `--dart-define-from-file=.env` or environment variables.
abstract final class GoongConfig {
  /// Goong REST API key (used for Place AutoComplete, Geocoding, Directions).
  static const String apiKey = String.fromEnvironment(
    'GOONG_API_KEY',
    defaultValue: '',
  );

  /// Goong Map Key (used for Map Tiles & Vector style).
  static const String mapKey = String.fromEnvironment(
    'GOONG_MAP_KEY',
    defaultValue: '',
  );

  /// Base URL for Goong REST APIs (Search, Geocode, Direction).
  static const String restBaseUrl = 'https://rsapi.goong.io';

  /// Raster tile template for Goong Map (used in FlutterMap or Map views).
  static String get rasterTileUrl =>
      'https://tiles.goong.io/assets/tiles/{z}/{x}/{y}.png?api_key=$mapKey';

  /// Vector style URL for Goong Map (MapLibre / Mapbox compatible).
  static String get vectorStyleUrl =>
      'https://tiles.goong.io/assets/goong_map_web.json?api_key=$mapKey';

  /// Check whether valid Goong keys have been configured.
  static bool get hasValidApiKey =>
      apiKey.isNotEmpty && apiKey != 'your_goong_api_key_here';

  static bool get hasValidMapKey =>
      mapKey.isNotEmpty && mapKey != 'your_goong_map_key_here';
}
