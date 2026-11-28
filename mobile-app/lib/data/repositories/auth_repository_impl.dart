import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/device/device_metadata_collector.dart';
import 'package:swiftroute_app/core/network/dio.dart';
import 'package:swiftroute_app/core/offline/sqlite_offline_store.dart';
import 'package:swiftroute_app/data/models/auth_model.dart';
import 'package:swiftroute_app/domain/entities/user.dart';
import 'package:swiftroute_app/domain/repositories/auth_repository.dart';

class AuthRepositoryImpl implements AuthRepository {
  final Dio _dio;
  final TokenStorage _tokenStorage;
  DateTime? _lastDeviceSyncAt;

  AuthRepositoryImpl({required Dio dio, required TokenStorage tokenStorage})
    : _dio = dio,
      _tokenStorage = tokenStorage;

  @override
  Future<User> activateWithCode(String activationCode) async {
    final normalized = activationCode.replaceAll(RegExp(r'[\s-]+'), '');
    if (!RegExp(r'^\d{12}$').hasMatch(normalized)) {
      throw Exception('Vui lòng nhập đủ 12 chữ số của mã kích hoạt.');
    }

    try {
      final installationId = await _tokenStorage.getOrCreateInstallationId();
      final metadata = DeviceMetadataCollector.collect(installationId);

      final response = await _dio.post(
        '/auth/driver/activate',
        data: metadata.toActivationJson(normalized),
      );

      final user = await _persistAndResolveUser(
        response.data as Map<String, dynamic>,
      );
      await _tokenStorage.saveLastSyncedAppVersion(metadata.appVersion);
      _lastDeviceSyncAt = DateTime.now();
      return user;
    } on DioException catch (e) {
      throw _handleDioError(e);
    }
  }

  Future<User> _persistAndResolveUser(Map<String, dynamic> responseMap) async {
    final tokens = ActivationTokens.fromJson(responseMap);

    final previousToken = await _tokenStorage.getAccessToken();
    final previousUser = previousToken == null
        ? null
        : _decodeUserFromToken(previousToken);
    final nextUser = _decodeUserFromToken(tokens.accessToken);
    if (previousUser == null || previousUser.id != nextUser.id) {
      await SqliteOfflineStore.instance.clearAll();
    }

    await _tokenStorage.saveTokens(
      accessToken: tokens.accessToken,
      refreshToken: tokens.refreshToken,
    );

    final dataPayload = responseMap['data'];
    if (dataPayload is Map<String, dynamic> &&
        dataPayload['user'] is Map<String, dynamic>) {
      final userJson = dataPayload['user'] as Map<String, dynamic>;
      final mePermissions = UserModel.parsePermissions(userJson['permissions']);
      return UserModel(
        id: (userJson['id'] ?? nextUser.id).toString(),
        email: (userJson['email'] ?? nextUser.email).toString(),
        name: (userJson['full_name'] ?? userJson['fullName'] ?? nextUser.name)
            .toString(),
        role: nextUser.role,
        permissions: mePermissions.isNotEmpty
            ? mePermissions
            : nextUser.permissions,
        permissionsVersion: nextUser.permissionsVersion,
      );
    }

    try {
      final meResponse = await _dio.get('/auth/me');
      final meData = meResponse.data['data'] as Map<String, dynamic>;
      final mePermissions = UserModel.parsePermissions(meData['permissions']);
      return UserModel(
        id: meData['id'] as String? ?? '',
        email: meData['email'] as String? ?? nextUser.email,
        name: meData['full_name'] as String? ?? '',
        role: nextUser.role,
        permissions: mePermissions.isNotEmpty
            ? mePermissions
            : nextUser.permissions,
        permissionsVersion: nextUser.permissionsVersion,
      );
    } catch (_) {
      return nextUser;
    }
  }

  @override
  Future<void> syncDeviceActivity({bool force = false}) async {
    try {
      final token = await _tokenStorage.getAccessToken();
      if (token == null || token.isEmpty) return;

      final installationId = await _tokenStorage.getOrCreateInstallationId();
      final metadata = DeviceMetadataCollector.collect(installationId);
      final previousVersion = await _tokenStorage.getLastSyncedAppVersion();
      final now = DateTime.now();

      final versionChanged = previousVersion != metadata.appVersion;
      final intervalElapsed =
          _lastDeviceSyncAt == null ||
          now.difference(_lastDeviceSyncAt!) >= const Duration(minutes: 5);

      if (!force && !versionChanged && !intervalElapsed) {
        return;
      }

      await _dio.patch('/drivers/me/device', data: metadata.toHeartbeatJson());
      await _tokenStorage.saveLastSyncedAppVersion(metadata.appVersion);
      _lastDeviceSyncAt = now;
    } catch (_) {
      // Non-critical telemetry; if session was revoked (401), AuthInterceptor handles invalidation.
    }
  }

  User _decodeUserFromToken(String accessToken) {
    try {
      final parts = accessToken.split('.');
      if (parts.length != 3) throw Exception('Invalid token format');
      final payload = parts[1];
      final padded = payload.padRight((payload.length + 3) & ~3, '=');
      final decoded = utf8.decode(base64Url.decode(padded));
      final claims = jsonDecode(decoded) as Map<String, dynamic>;
      final rawRole =
          claims['tenant_role'] ??
          claims['system_role'] ??
          claims['role'] ??
          claims['roles'] ??
          'USER';
      final role = (rawRole is List)
          ? (rawRole.isNotEmpty ? rawRole.first.toString() : 'USER')
          : rawRole.toString();
      final permissions = UserModel.parsePermissions(claims['permissions']);
      final permissionsVersion = (claims['perm_ver'] as num?)?.toInt() ?? 1;
      return UserModel(
        id: (claims['sub'] ?? '') as String,
        email: (claims['email'] ?? '') as String,
        name: (claims['name'] ?? claims['full_name'] ?? '') as String,
        role: role.toUpperCase(),
        permissions: permissions,
        permissionsVersion: permissionsVersion,
      );
    } catch (_) {
      return const UserModel(id: '', email: '', name: '', role: 'USER');
    }
  }

  @override
  Future<void> logout() async {
    try {
      final refreshToken = await _tokenStorage.getRefreshToken();
      if (refreshToken != null) {
        await _dio.post(
          '/auth/logout',
          queryParameters: {'refreshToken': refreshToken},
        );
      }
    } catch (_) {
      // Ignore network errors on logout — always clear local tokens
    } finally {
      _lastDeviceSyncAt = null;
      await SqliteOfflineStore.instance.clearAll();
      await _tokenStorage.clearAll();
    }
  }

  @override
  Future<User?> getCurrentUser() async {
    try {
      final token = await _tokenStorage.getAccessToken();
      if (token == null || token.isEmpty) return null;

      try {
        final response = await _dio.get('/auth/me');
        final data = response.data['data'] as Map<String, dynamic>;
        final effectiveToken = await _tokenStorage.getAccessToken() ?? token;
        final tokenUser = _decodeUserFromToken(effectiveToken);
        final mePermissions = UserModel.parsePermissions(data['permissions']);
        return UserModel(
          id: data['id'] as String? ?? '',
          email: data['email'] as String? ?? '',
          name: data['full_name'] as String? ?? '',
          role: tokenUser.role,
          permissions: mePermissions.isNotEmpty
              ? mePermissions
              : tokenUser.permissions,
          permissionsVersion: tokenUser.permissionsVersion,
        );
      } on DioException catch (e) {
        final status = e.response?.statusCode;
        final remainingToken = await _tokenStorage.getAccessToken();
        if (status == 401 ||
            status == 403 ||
            remainingToken == null ||
            remainingToken.isEmpty) {
          await SqliteOfflineStore.instance.clearAll();
          await _tokenStorage.clearAll();
          return null;
        }
        return _decodeUserFromToken(remainingToken);
      }
    } catch (_) {
      return null;
    }
  }

  String? _extractServerMessage(DioException e) {
    final data = e.response?.data;
    if (data is Map) {
      final error = data['error'];
      if (error is Map && error['message'] != null) {
        return error['message'].toString();
      }
      if (error is String && error.isNotEmpty) return error;
      if (data['message'] != null) return data['message'].toString();
    }
    return null;
  }

  Exception _handleDioError(DioException e) {
    switch (e.type) {
      case DioExceptionType.connectionTimeout:
      case DioExceptionType.receiveTimeout:
      case DioExceptionType.sendTimeout:
        return Exception('Kết nối mạng quá chậm. Vui lòng thử lại.');

      case DioExceptionType.connectionError:
        return Exception(
          'Không có kết nối mạng. Kiểm tra lại Wi-Fi hoặc dữ liệu di động.',
        );

      case DioExceptionType.badResponse:
        final statusCode = e.response?.statusCode;
        final message = _extractServerMessage(e);

        if (message != null && message.isNotEmpty) {
          return Exception(message);
        }

        return switch (statusCode) {
          400 => Exception(
            'Mã kích hoạt không hợp lệ, đã hết hạn hoặc đã sử dụng.',
          ),
          401 => Exception(
            'Mã kích hoạt không chính xác. Vui lòng kiểm tra lại.',
          ),
          403 => Exception(
            'Hồ sơ tài xế chưa sẵn sàng hoạt động hoặc đã bị tạm ngưng.',
          ),
          404 => Exception('Không tìm thấy tài nguyên (404).'),
          409 => Exception('Tài khoản hoặc dữ liệu đã tồn tại (409).'),
          422 => Exception('Dữ liệu gửi lên không hợp lệ (422).'),
          429 => Exception(
            'Quá nhiều lần thử. Vui lòng đợi ít phút trước khi thử lại.',
          ),
          500 => Exception('Lỗi máy chủ nội bộ (500). Vui lòng thử lại sau.'),
          _ => Exception('Đã có lỗi xảy ra (${statusCode ?? 'unknown'}).'),
        };

      default:
        return Exception('Đã có lỗi xảy ra. Vui lòng thử lại.');
    }
  }
}

final authRepositoryProvider = Provider<AuthRepository>((ref) {
  return AuthRepositoryImpl(
    dio: ref.read(dioProvider),
    tokenStorage: ref.read(tokenStorageProvider),
  );
});
