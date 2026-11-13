import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/network/dio.dart';
import 'package:swiftroute_app/core/offline/sqlite_offline_store.dart';
import 'package:swiftroute_app/data/models/auth_model.dart';
import 'package:swiftroute_app/domain/entities/user.dart';
import 'package:swiftroute_app/domain/repositories/auth_repository.dart';

class AuthRepositoryImpl implements AuthRepository {
  final Dio _dio;
  final TokenStorage _tokenStorage;

  AuthRepositoryImpl({required Dio dio, required TokenStorage tokenStorage})
    : _dio = dio,
      _tokenStorage = tokenStorage;

  @override
  Future<void> register(
    String email,
    String password,
    String username, {
    String phoneNumber = '',
    String address = '',
    String city = '',
    String state = '',
    String zipCode = '',
    String country = 'VN',
  }) async {
    try {
      final request = RegisterRequest(
        email: email,
        password: password,
        fullName: username,
        phoneNumber: phoneNumber.trim(),
        address: address.trim(),
        city: city.trim(),
        state: state.trim(),
        zipCode: zipCode.trim(),
        country: country.trim(),
      );
      // POST /api/auth/register → 201 { data: null }
      await _dio.post('/auth/register', data: request.toJson());
    } on DioException catch (e) {
      throw _handleDioError(e);
    }
  }

  @override
  Future<User> login(String identifier, String password) async {
    try {
      // Backend LoginRequestDTO uses 'username' field
      final request = LoginRequest(username: identifier, password: password);
      final response = await _dio.post('/auth/login', data: request.toJson());

      // Backend wraps response in ApiResponse: { "data": { accessToken, refreshToken } }
      final loginResponse = LoginResponse.fromJson(
        response.data as Map<String, dynamic>,
      );

      final previousToken = await _tokenStorage.getAccessToken();
      final previousUser = previousToken == null
          ? null
          : _decodeUserFromToken(previousToken);
      final nextUser = _decodeUserFromToken(loginResponse.accessToken);
      if (previousUser == null || previousUser.id != nextUser.id) {
        await SqliteOfflineStore.instance.clearAll();
      }

      await _tokenStorage.saveTokens(
        accessToken: loginResponse.accessToken,
        refreshToken: loginResponse.refreshToken,
      );

      // Attempt to load full user profile directly from /auth/me
      try {
        final meResponse = await _dio.get('/auth/me');
        final meData = meResponse.data['data'] as Map<String, dynamic>;
        final tokenUser = _decodeUserFromToken(loginResponse.accessToken);
        return UserModel(
          id: meData['id'] as String? ?? '',
          email: meData['email'] as String? ?? identifier,
          name: meData['full_name'] as String? ?? '',
          // The signed access token carries the active tenant membership role.
          // account_role from /auth/me is platform-scoped and must not drive the Driver app.
          role: tokenUser.role,
        );
      } catch (_) {
        // Fallback: decode user info from the access token payload (JWT)
        return _decodeUserFromToken(loginResponse.accessToken);
      }
    } on DioException catch (e) {
      throw _handleDioError(e);
    }
  }

  /// Decodes basic user fields from a JWT payload without verifying the signature.
  /// The mobile client does NOT need to verify — the API Gateway handles that.
  User _decodeUserFromToken(String accessToken) {
    try {
      final parts = accessToken.split('.');
      if (parts.length != 3) throw Exception('Invalid token format');
      final payload = parts[1];
      // Base64url decode (pad to multiple of 4)
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
      return UserModel(
        id: (claims['sub'] ?? '') as String,
        email: (claims['email'] ?? '') as String,
        name: (claims['name'] ?? claims['full_name'] ?? '') as String,
        role: role.toUpperCase(),
      );
    } catch (_) {
      // Fallback: return placeholder until /auth/me is called
      return const UserModel(id: '', email: '', name: '', role: 'USER');
    }
  }

  @override
  Future<void> logout() async {
    try {
      final refreshToken = await _tokenStorage.getRefreshToken();
      if (refreshToken != null) {
        // Backend: POST /api/auth/logout?refreshToken=<token>
        await _dio.post(
          '/auth/logout',
          queryParameters: {'refreshToken': refreshToken},
        );
      }
    } catch (_) {
      // Ignore network errors on logout — always clear local tokens
    } finally {
      await SqliteOfflineStore.instance.clearAll();
      await _tokenStorage.clearAll();
    }
  }

  @override
  Future<User?> getCurrentUser() async {
    try {
      final token = await _tokenStorage.getAccessToken();
      if (token == null) return null;

      try {
        final response = await _dio.get('/auth/me');
        final data = response.data['data'] as Map<String, dynamic>;
        final effectiveToken = await _tokenStorage.getAccessToken() ?? token;
        final tokenUser = _decodeUserFromToken(effectiveToken);
        return UserModel(
          id: data['id'] as String? ?? '',
          email: data['email'] as String? ?? '',
          name: data['full_name'] as String? ?? '',
          role: tokenUser.role,
        );
      } catch (_) {
        return _decodeUserFromToken(token);
      }
    } catch (_) {
      return null;
    }
  }

  /// Extracts a human-readable message from the ApiResponse error envelope.
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

  /// Converts a DioException into a user-friendly message.
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
          400 => Exception('Thông tin không hợp lệ (400).'),
          401 => Exception('Email hoặc mật khẩu không chính xác (401).'),
          403 => Exception('Tài khoản không có quyền truy cập (403).'),
          404 => Exception('Không tìm thấy tài nguyên (404).'),
          409 => Exception('Tài khoản hoặc dữ liệu đã tồn tại (409).'),
          422 => Exception('Dữ liệu gửi lên không hợp lệ (422).'),
          429 => Exception('Quá nhiều yêu cầu. Vui lòng thử lại sau ít phút.'),
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
