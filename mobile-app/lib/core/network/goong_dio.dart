// core/network/goong_dio.dart

import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/config/goong_config.dart';

/// Provider for a clean Dio client dedicated to Goong REST APIs.
/// Does NOT attach JWT Bearer tokens to external Goong servers.
final goongDioProvider = Provider<Dio>((ref) {
  final dio = Dio(
    BaseOptions(
      baseUrl: GoongConfig.restBaseUrl,
      connectTimeout: const Duration(seconds: 10),
      receiveTimeout: const Duration(seconds: 10),
      headers: {'Accept': 'application/json'},
    ),
  );

  if (kDebugMode) {
    dio.interceptors.add(
      LogInterceptor(
        requestHeader: false,
        responseHeader: false,
        requestBody: false,
        responseBody: true,
        // ignore: avoid_print
        logPrint: (obj) => print('[Goong API] $obj'),
      ),
    );
  }

  return dio;
});
