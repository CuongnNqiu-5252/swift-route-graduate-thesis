import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/network/dio.dart';
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
        phoneNumber: phoneNumber,
        address: address,
        city: city,
        state: state,
        zipCode: zipCode,
        country: country,
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

      await _tokenStorage.saveTokens(
        accessToken: loginResponse.accessToken,
        refreshToken: loginResponse.refreshToken,
      );

      // Decode user info from the access token payload (JWT)
      return _decodeUserFromToken(loginResponse.accessToken);
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
      final decoded = String.fromCharCodes(base64Url.decode(padded));
      final claims = jsonDecode(decoded) as Map<String, dynamic>;
      return UserModel(
        id: (claims['sub'] ?? '') as String,
        email: (claims['email'] ?? '') as String,
        name: (claims['name'] ?? claims['full_name'] ?? '') as String,
        role: (claims['role'] ?? 'CUSTOMER') as String,
      );
    } catch (_) {
      // Fallback: return placeholder until /auth/me is called
      return const UserModel(id: '', email: '', name: '', role: 'CUSTOMER');
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
      await _tokenStorage.clearAll();
    }
  }

  @override
  Future<User?> getCurrentUser() async {
    try {
      final token = await _tokenStorage.getAccessToken();
      if (token == null) return null;
      // Re-decode from the stored token without a network call
      return _decodeUserFromToken(token);
    } catch (_) {
      return null;
    }
  }

  /// Extracts a human-readable message from the ApiResponse error envelope.
  String? _extractServerMessage(DioException e) {
    final data = e.response?.data;
    if (data is Map<String, dynamic>) {
      // { "error": { "message": "..." } }
      final error = data['error'];
      if (error is Map) return error['message'] as String?;
      return data['message'] as String?;
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

        return switch (statusCode) {
          400 => Exception(message ?? 'Thông tin không hợp lệ.'),
          401 => Exception(message ?? 'Email hoặc mật khẩu không đúng.'),
          403 => Exception('Tài khoản của bạn bị hạn chế.'),
          404 => Exception('Không tìm thấy tài nguyên.'),
          409 => Exception(message ?? 'Email đã được đăng ký.'),
          422 => Exception(message ?? 'Dữ liệu không hợp lệ.'),
          429 => Exception('Quá nhiều yêu cầu. Vui lòng chờ một lúc.'),
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
