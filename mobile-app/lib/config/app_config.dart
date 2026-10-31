class AppConfig {
  /// Base API URL for backend services.
  /// Can be overridden via `--dart-define=API_BASE_URL=...` or `--dart-define-from-file=.env`
  static const String apiBaseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://localhost:8000/api',
  );

  /// Connection timeout in seconds.
  static const int connectTimeoutSeconds = int.fromEnvironment(
    'CONNECT_TIMEOUT_SECONDS',
    defaultValue: 15,
  );

  /// Receive timeout in seconds.
  static const int receiveTimeoutSeconds = int.fromEnvironment(
    'RECEIVE_TIMEOUT_SECONDS',
    defaultValue: 15,
  );
}
