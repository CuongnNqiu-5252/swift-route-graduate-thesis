// core/network/dio_client.dart

import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:swiftroute_app/config/app_config.dart';
import 'package:swiftroute_app/core/network/interceptors/auth_interceptor.dart';

const String _tokenKey = 'access_token';
const String _refreshTokenKey = 'refresh_token';

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

  // Thêm interceptors
  dio.interceptors.add(AuthInterceptor(dio: dio, storage: storage, tokenStorage: tokenStorage));
  dio.interceptors.add(
    LogInterceptor(
      requestBody: true,
      responseBody: true,
      // ignore: avoid_print
      logPrint: (obj) => print(obj), // Dùng logger thực tế trong production
    ),
  );

  return dio;
});

class TokenStorage {
  final FlutterSecureStorage _storage;

  // In-memory cache to guarantee fast access and bypass WebCrypto IndexedDB collisions on Web
  static String? _cachedAccessToken;
  static String? _cachedRefreshToken;

  TokenStorage(this._storage);

  Future<void> saveTokens({
    required String accessToken,
    required String refreshToken,
  }) async {
    _cachedAccessToken = accessToken;
    _cachedRefreshToken = refreshToken;
    try {
      // Execute sequentially to avoid Web IndexedDB race conditions and OperationError
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

  Future<void> clearAll() async {
    _cachedAccessToken = null;
    _cachedRefreshToken = null;
    try {
      await _storage.deleteAll();
    } catch (e) {
      debugPrint('[TokenStorage] Error clearing storage: $e');
    }
  }
}

final tokenStorageProvider = Provider<TokenStorage>((ref) {
  return TokenStorage(ref.read(secureStorageProvider));
});
