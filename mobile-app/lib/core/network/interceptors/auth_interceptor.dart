import 'dart:async';
import 'dart:convert';
import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:swiftroute_app/core/network/dio.dart';

class AuthInterceptor extends Interceptor {
  final Dio dio;
  final FlutterSecureStorage storage;
  final TokenStorage? tokenStorage;
  final VoidCallback? onSessionInvalidated;
  Completer<bool>? _refreshCompleter;

  AuthInterceptor({
    required this.dio,
    required this.storage,
    this.tokenStorage,
    this.onSessionInvalidated,
  });

  static const List<String> _publicPaths = [
    '/auth/driver/activate',
    '/auth/refresh',
  ];

  @override
  Future<void> onRequest(
    RequestOptions options,
    RequestInterceptorHandler handler,
  ) async {
    if (_publicPaths.any((p) => options.path.contains(p))) {
      return handler.next(options);
    }

    try {
      String? token;
      if (tokenStorage != null) {
        token = await tokenStorage!.getAccessToken();
      } else {
        token = await storage.read(key: 'access_token');
      }

      if (token != null && token.isNotEmpty) {
        options.headers['Authorization'] = 'Bearer $token';
        if (!options.headers.containsKey('X-Tenant-Id')) {
          final tenantId = _extractClaim(token, 'tenant_id');
          if (tenantId != null && tenantId.isNotEmpty) {
            options.headers['X-Tenant-Id'] = tenantId;
          }
        }
      }
    } catch (e) {
      debugPrint('[AuthInterceptor] Safe-caught storage read error on Web: $e');
    }
    handler.next(options);
  }

  @override
  Future<void> onError(
    DioException err,
    ErrorInterceptorHandler handler,
  ) async {
    if (_publicPaths.any((p) => err.requestOptions.path.contains(p))) {
      if (err.requestOptions.path.contains('/auth/refresh') &&
          err.response?.statusCode == 401) {
        await _clearCredentialsAndNotify();
      }
      return handler.next(err);
    }

    if (err.response?.statusCode == 401) {
      if (_refreshCompleter != null) {
        final refreshed = await _refreshCompleter!.future;
        if (refreshed) {
          final token = tokenStorage != null
              ? await tokenStorage!.getAccessToken()
              : await storage.read(key: 'access_token');
          err.requestOptions.headers['Authorization'] = 'Bearer $token';
          final response = await dio.fetch(err.requestOptions);
          return handler.resolve(response);
        } else {
          return handler.next(err);
        }
      }

      _refreshCompleter = Completer<bool>();
      try {
        final refreshed = await _refreshToken();
        _refreshCompleter?.complete(refreshed);
        if (refreshed) {
          final token = tokenStorage != null
              ? await tokenStorage!.getAccessToken()
              : await storage.read(key: 'access_token');
          err.requestOptions.headers['Authorization'] = 'Bearer $token';
          final response = await dio.fetch(err.requestOptions);
          return handler.resolve(response);
        } else {
          await _clearCredentialsAndNotify();
          return handler.next(err);
        }
      } catch (e) {
        _refreshCompleter?.complete(false);
        await _clearCredentialsAndNotify();
        return handler.next(err);
      } finally {
        _refreshCompleter = null;
      }
    }
    handler.next(err);
  }

  Future<void> _clearCredentialsAndNotify() async {
    try {
      if (tokenStorage != null) {
        await tokenStorage!.clearAll();
      } else {
        await storage.delete(key: 'access_token');
        await storage.delete(key: 'refresh_token');
      }
    } catch (_) {}
    onSessionInvalidated?.call();
  }

  Future<bool> _refreshToken() async {
    try {
      final previousToken = tokenStorage != null
          ? await tokenStorage!.getAccessToken()
          : await storage.read(key: 'access_token');
      final previousTenantId = previousToken != null
          ? _extractClaim(previousToken, 'tenant_id')
          : null;
      final refreshToken = tokenStorage != null
          ? await tokenStorage!.getRefreshToken()
          : await storage.read(key: 'refresh_token');
      if (refreshToken == null || refreshToken.isEmpty) return false;

      final cleanDio = Dio(BaseOptions(baseUrl: dio.options.baseUrl));
      final response = await cleanDio.post(
        '/auth/refresh',
        queryParameters: {'refreshToken': refreshToken},
      );

      final resData = response.data;
      Map<String, dynamic> data;
      if (resData is Map<String, dynamic> &&
          resData.containsKey('data') &&
          resData['data'] is Map<String, dynamic>) {
        data = resData['data'] as Map<String, dynamic>;
      } else if (resData is Map<String, dynamic>) {
        data = resData;
      } else {
        return false;
      }

      final newAccessToken =
          (data['access_token'] ?? data['accessToken']) as String?;
      final newRefreshToken =
          (data['refresh_token'] ?? data['refreshToken']) as String?;

      if (newAccessToken == null || newAccessToken.isEmpty) return false;
      String effectiveAccessToken = newAccessToken;

      if (previousTenantId != null &&
          previousTenantId.isNotEmpty &&
          _extractClaim(effectiveAccessToken, 'tenant_id') == null) {
        try {
          final switchResp = await cleanDio.post(
            '/auth/tenants/$previousTenantId/switch',
            options: Options(
              headers: {'Authorization': 'Bearer $effectiveAccessToken'},
            ),
          );
          final switchBody = switchResp.data;
          final switchData =
              switchBody is Map<String, dynamic> &&
                  switchBody['data'] is Map<String, dynamic>
              ? switchBody['data'] as Map<String, dynamic>
              : (switchBody is Map<String, dynamic> ? switchBody : null);
          final switchedToken =
              (switchData?['access_token'] ?? switchData?['accessToken'])
                  as String?;
          if (switchedToken != null && switchedToken.isNotEmpty) {
            effectiveAccessToken = switchedToken;
          }
        } catch (_) {}
      }

      if (tokenStorage != null) {
        await tokenStorage!.saveTokens(
          accessToken: effectiveAccessToken,
          refreshToken: newRefreshToken ?? refreshToken,
        );
      } else {
        await storage.write(key: 'access_token', value: effectiveAccessToken);
        if (newRefreshToken != null && newRefreshToken.isNotEmpty) {
          await storage.write(key: 'refresh_token', value: newRefreshToken);
        }
      }

      return true;
    } catch (e) {
      return false;
    }
  }

  String? _extractClaim(String jwt, String key) {
    try {
      final parts = jwt.split('.');
      if (parts.length != 3) return null;
      final padded = parts[1].padRight((parts[1].length + 3) & ~3, '=');
      final jsonMap =
          jsonDecode(utf8.decode(base64Url.decode(padded)))
              as Map<String, dynamic>;
      final value = jsonMap[key];
      return value?.toString();
    } catch (_) {
      return null;
    }
  }
}
