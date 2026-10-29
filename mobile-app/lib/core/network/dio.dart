// core/network/dio_client.dart

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:swiftroute_app/config/app_config.dart';
import 'package:swiftroute_app/core/network/interceptors/auth_interceptor.dart';

const String _tokenKey = 'access_token';
const String _refreshTokenKey = 'refresh_token';

final secureStorageProvider = Provider<FlutterSecureStorage>((ref) {
  return const FlutterSecureStorage();
});

final dioProvider = Provider<Dio>((ref) {
  final storage = ref.read(secureStorageProvider);
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
  dio.interceptors.add(AuthInterceptor(dio: dio, storage: storage));
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
  TokenStorage(this._storage);

  Future<void> saveTokens({
    required String accessToken,
    required String refreshToken,
  }) async {
    await Future.wait([
      _storage.write(key: _tokenKey, value: accessToken),
      _storage.write(key: _refreshTokenKey, value: refreshToken),
    ]);
  }

  Future<String?> getAccessToken() => _storage.read(key: _tokenKey);
  Future<String?> getRefreshToken() => _storage.read(key: _refreshTokenKey);
  Future<void> clearAll() => _storage.deleteAll();
}

final tokenStorageProvider = Provider<TokenStorage>((ref) {
  return TokenStorage(ref.read(secureStorageProvider));
});
