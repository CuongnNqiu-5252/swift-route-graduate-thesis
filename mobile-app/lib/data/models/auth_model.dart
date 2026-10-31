// data/models/auth_model.dart
//
// Maps Flutter DTOs to/from the backend API contract.
// Backend wraps all responses in: { "data": {...}, "error": null, "timestamp": "..." }
// Login:    POST /api/auth/login       { username, password }
//           -> { data: { accessToken, refreshToken } }
// Register: POST /api/auth/register    { email, password, fullName, phoneNumber,
//                                        address, city, state, zipCode, country }
//           -> 201 { data: null }

import 'package:swiftroute_app/domain/entities/user.dart';

// ── Request models ────────────────────────────────────────────────────────────

class LoginRequest {
  final String username;
  final String password;

  LoginRequest({required this.username, required this.password});

  Map<String, dynamic> toJson() => {'username': username, 'password': password};
}

class RegisterRequest {
  final String email;
  final String password;
  final String fullName;
  final String phoneNumber;
  final String address;
  final String city;
  final String state;
  final String zipCode;
  final String country;

  RegisterRequest({
    required this.email,
    required this.password,
    required this.fullName,
    required this.phoneNumber,
    required this.address,
    required this.city,
    required this.state,
    required this.zipCode,
    required this.country,
  });

  Map<String, dynamic> toJson() => {
    'email': email,
    'password': password,
    'full_name': fullName,
    'phone_number': phoneNumber,
    'address': address,
    'city': city,
    'state': state,
    'zip_code': zipCode,
    'country': country,
  };
}

// ── Response models ───────────────────────────────────────────────────────────

class LoginResponse {
  final String accessToken;
  final String refreshToken;

  LoginResponse({required this.accessToken, required this.refreshToken});

  /// Backend response shape: { "data": { "accessToken": "...", "refreshToken": "..." }, ... }
  /// Jackson SnakeCaseStrategy is applied on the backend, so fields come as snake_case.
  factory LoginResponse.fromJson(Map<String, dynamic> json) {
    // Unwrap the ApiResponse envelope if present
    final payload =
        json.containsKey('data') && json['data'] is Map<String, dynamic>
            ? json['data'] as Map<String, dynamic>
            : json;

    return LoginResponse(
      accessToken:
          (payload['access_token'] ?? payload['accessToken']) as String,
      refreshToken:
          (payload['refresh_token'] ?? payload['refreshToken']) as String,
    );
  }
}

// ── User model (Data layer) ───────────────────────────────────────────────────

class UserModel extends User {
  const UserModel({
    required super.id,
    required super.email,
    required super.name,
    required super.role,
    super.avatar,
  });

  factory UserModel.fromJson(Map<String, dynamic> json) {
    return UserModel(
      id: (json['id'] ?? json['user_id'] ?? '') as String,
      email: (json['email'] ?? '') as String,
      name:
          (json['full_name'] ?? json['fullName'] ?? json['name'] ?? '') as String,
      role: (json['role'] ?? 'CUSTOMER') as String,
      avatar: (json['avatar'] ?? json['avatar_url']) as String?,
    );
  }

  Map<String, dynamic> toJson() => {
    'id': id,
    'email': email,
    'name': name,
    'role': role,
    'avatar': avatar,
  };
}
