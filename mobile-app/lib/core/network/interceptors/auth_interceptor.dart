import 'dart:async';
import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:swiftroute_app/core/network/dio.dart';

class AuthInterceptor extends Interceptor {
  final Dio dio;
  final FlutterSecureStorage storage;
  final TokenStorage? tokenStorage;
  Completer<bool>? _refreshCompleter;

  AuthInterceptor({
    required this.dio,
    required this.storage,
    this.tokenStorage,
  });

  @override
  Future<void> onRequest(
    RequestOptions options,
    RequestInterceptorHandler handler,
  ) async {
    const publicPaths = [
      '/auth/login',
      '/auth/register',
      '/auth/refresh',
    ];
    if (publicPaths.any((p) => options.path.contains(p))) {
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
    // If the failed endpoint is refresh or auth endpoint, never retry refresh
    if (err.requestOptions.path.contains('/auth/refresh') ||
        err.requestOptions.path.contains('/auth/login')) {
      if (err.response?.statusCode == 401) {
        try {
          if (tokenStorage != null) {
            await tokenStorage!.clearAll();
          } else {
            await storage.deleteAll();
          }
        } catch (_) {}
      }
      return handler.next(err);
    }

    if (err.response?.statusCode == 401) {
      if (_refreshCompleter != null) {
        // Another concurrent request is already refreshing the token -> wait for it
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
          try {
            if (tokenStorage != null) {
              await tokenStorage!.clearAll();
            } else {
              await storage.deleteAll();
            }
          } catch (_) {}
          return handler.next(err);
        }
      } catch (e) {
        _refreshCompleter?.complete(false);
        try {
          if (tokenStorage != null) {
            await tokenStorage!.clearAll();
          } else {
            await storage.deleteAll();
          }
        } catch (_) {}
        return handler.next(err);
      } finally {
        _refreshCompleter = null;
      }
    }
    handler.next(err);
  }

  Future<bool> _refreshToken() async {
    try {
      final refreshToken = tokenStorage != null
          ? await tokenStorage!.getRefreshToken()
          : await storage.read(key: 'refresh_token');
      if (refreshToken == null || refreshToken.isEmpty) return false;

      // Use clean Dio options to avoid interceptor recursion
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

      final newAccessToken = (data['access_token'] ?? data['accessToken']) as String?;
      final newRefreshToken = (data['refresh_token'] ?? data['refreshToken']) as String?;

      if (newAccessToken == null || newAccessToken.isEmpty) return false;

      if (tokenStorage != null) {
        await tokenStorage!.saveTokens(
          accessToken: newAccessToken,
          refreshToken: newRefreshToken ?? refreshToken,
        );
      } else {
        await storage.write(key: 'access_token', value: newAccessToken);
        if (newRefreshToken != null && newRefreshToken.isNotEmpty) {
          await storage.write(key: 'refresh_token', value: newRefreshToken);
        }
      }

      return true;
    } catch (e) {
      return false;
    }
  }
}
