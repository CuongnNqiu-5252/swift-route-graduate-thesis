// core/network/dio.dart

import 'dart:math';

import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:swiftroute_app/config/app_config.dart';
import 'package:swiftroute_app/core/network/interceptors/auth_interceptor.dart';

const String _tokenKey = 'access_token';
const String _refreshTokenKey = 'refresh_token';
const String _installationIdKey = 'driver_installation_id';
const String _syncedAppVersionKey = 'driver_synced_app_version';

/// Incremented whenever an active session is rejected with 401 and refresh fails (e.g. device deactivated).
final authSessionInvalidatedCounterProvider = StateProvider<int>((ref) => 0);

final secureStorageProvider = Provider<FlutterSecureStorage>((ref) {
  return const FlutterSecureStorage(
    webOptions: WebOptions(
      dbName: 'swiftroute_secure_storage',
      publicKey: 'swiftroute_token_key',
    ),
  );
});

final dioProvider = Provider<Dio>((ref) {
  final storage = ref.read(secureStorageProvider);
  final tokenStorage = ref.read(tokenStorageProvider);
  final dio = Dio(
    BaseOptions(
      baseUrl: AppConfig.apiBaseUrl,
      connectTimeout: Duration(seconds: AppConfig.connectTimeoutSeconds),
      receiveTimeout: Duration(seconds: AppConfig.receiveTimeoutSeconds),
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
      },
    ),
  );

  dio.interceptors.add(
    AuthInterceptor(
      dio: dio,
      storage: storage,
      tokenStorage: tokenStorage,
      onSessionInvalidated: () {
        ref.read(authSessionInvalidatedCounterProvider.notifier).state++;
      },
    ),
  );
  dio.interceptors.add(_ApiLogInterceptor());

  return dio;
});

class _ApiLogInterceptor extends LogInterceptor {
  _ApiLogInterceptor()
    : super(
        requestBody: true,
        responseBody: true,
        logPrint: (value) => debugPrint(value.toString()),
      );

  bool _shouldSkipBodyLogging(RequestOptions options) =>
      options.path.startsWith('/chat/') || options.path.contains('/auth/');

  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) {
    if (_shouldSkipBodyLogging(options)) {
      handler.next(options);
      return;
    }
    super.onRequest(options, handler);
  }

  @override
  void onResponse(
    Response<dynamic> response,
    ResponseInterceptorHandler handler,
  ) {
    if (_shouldSkipBodyLogging(response.requestOptions)) {
      handler.next(response);
      return;
    }
    super.onResponse(response, handler);
  }

  @override
  void onError(DioException error, ErrorInterceptorHandler handler) {
    if (_shouldSkipBodyLogging(error.requestOptions)) {
      handler.next(error);
      return;
    }
    super.onError(error, handler);
  }
}

class TokenStorage {
  final FlutterSecureStorage _storage;

  // In-memory cache to guarantee fast access and bypass WebCrypto IndexedDB collisions on Web
  static String? _cachedAccessToken;
  static String? _cachedRefreshToken;
  static String? _cachedInstallationId;
  static String? _cachedSyncedAppVersion;

  TokenStorage(this._storage);

  Future<void> saveTokens({
    required String accessToken,
    required String refreshToken,
  }) async {
    _cachedAccessToken = accessToken;
    _cachedRefreshToken = refreshToken;
    try {
      await _storage.write(key: _tokenKey, value: accessToken);
      await _storage.write(key: _refreshTokenKey, value: refreshToken);
    } catch (e) {
      debugPrint('[TokenStorage] Error writing tokens to storage: $e');
    }
  }

  Future<String?> getAccessToken() async {
    if (_cachedAccessToken != null && _cachedAccessToken!.isNotEmpty) {
      return _cachedAccessToken;
    }
    try {
      final token = await _storage.read(key: _tokenKey);
      _cachedAccessToken = token;
      return token;
    } catch (e) {
      debugPrint('[TokenStorage] Error reading access token: $e');
      return _cachedAccessToken;
    }
  }

  Future<String?> getRefreshToken() async {
    if (_cachedRefreshToken != null && _cachedRefreshToken!.isNotEmpty) {
      return _cachedRefreshToken;
    }
    try {
      final token = await _storage.read(key: _refreshTokenKey);
      _cachedRefreshToken = token;
      return token;
    } catch (e) {
      debugPrint('[TokenStorage] Error reading refresh token: $e');
      return _cachedRefreshToken;
    }
  }

  /// Returns a persistent UUID v4 representing this app installation (never relies on IMEI or MAC).
  Future<String> getOrCreateInstallationId() async {
    if (_cachedInstallationId != null && _cachedInstallationId!.isNotEmpty) {
      return _cachedInstallationId!;
    }
    try {
      final existing = await _storage.read(key: _installationIdKey);
      if (existing != null && existing.trim().isNotEmpty) {
        _cachedInstallationId = existing.trim();
        return _cachedInstallationId!;
      }
    } catch (_) {}

    final generated = _generateUuidV4();
    _cachedInstallationId = generated;
    try {
      await _storage.write(key: _installationIdKey, value: generated);
    } catch (_) {}
    return generated;
  }

  Future<String?> getLastSyncedAppVersion() async {
    if (_cachedSyncedAppVersion != null) {
      return _cachedSyncedAppVersion;
    }
    try {
      _cachedSyncedAppVersion = await _storage.read(key: _syncedAppVersionKey);
    } catch (_) {}
    return _cachedSyncedAppVersion;
  }

  Future<void> saveLastSyncedAppVersion(String version) async {
    _cachedSyncedAppVersion = version;
    try {
      await _storage.write(key: _syncedAppVersionKey, value: version);
    } catch (_) {}
  }

  Future<void> clearAll() async {
    _cachedAccessToken = null;
    _cachedRefreshToken = null;
    _cachedSyncedAppVersion = null;
    try {
      await _storage.delete(key: _tokenKey);
      await _storage.delete(key: _refreshTokenKey);
      await _storage.delete(key: _syncedAppVersionKey);
    } catch (e) {
      debugPrint('[TokenStorage] Error clearing auth tokens: $e');
    }
  }

  static String _generateUuidV4() {
    final rng = Random.secure();
    final bytes = List<int>.generate(16, (_) => rng.nextInt(256));
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;

    String hex(int byte) => byte.toRadixString(16).padLeft(2, '0');
    final h = bytes.map(hex).toList(growable: false);
    return '${h[0]}${h[1]}${h[2]}${h[3]}-'
        '${h[4]}${h[5]}-'
        '${h[6]}${h[7]}-'
        '${h[8]}${h[9]}-'
        '${h[10]}${h[11]}${h[12]}${h[13]}${h[14]}${h[15]}';
  }
}

final tokenStorageProvider = Provider<TokenStorage>((ref) {
  return TokenStorage(ref.read(secureStorageProvider));
});
