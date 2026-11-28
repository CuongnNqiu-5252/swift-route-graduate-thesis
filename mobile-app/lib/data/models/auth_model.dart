// data/models/auth_model.dart
//
// Maps Flutter DTOs to/from the backend API contract.
// Backend wraps all responses in: { "data": {...}, "error": null, "timestamp": "..." }

import 'package:swiftroute_app/domain/entities/user.dart';

// ── Response models ───────────────────────────────────────────────────────────

class ActivationTokens {
  final String accessToken;
  final String refreshToken;

  ActivationTokens({required this.accessToken, required this.refreshToken});

  /// Backend response shape: { "data": { "accessToken": "...", "refreshToken": "..." }, ... }
  /// Jackson SnakeCaseStrategy is applied on the backend, so fields come as snake_case.
  factory ActivationTokens.fromJson(Map<String, dynamic> json) {
    // Unwrap the ApiResponse envelope if present
    final payload =
        json.containsKey('data') && json['data'] is Map<String, dynamic>
        ? json['data'] as Map<String, dynamic>
        : json;

    return ActivationTokens(
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
    super.permissions = const [],
    super.permissionsVersion = 1,
  });

  static List<String> parsePermissions(dynamic raw) {
    if (raw is! List) return const [];
    return raw
        .whereType<String>()
        .map((e) => e.trim().toLowerCase())
        .where((e) => e.isNotEmpty)
        .toSet()
        .toList(growable: false);
  }

  factory UserModel.fromJson(Map<String, dynamic> json) {
    return UserModel(
      id: (json['id'] ?? json['user_id'] ?? '') as String,
      email: (json['email'] ?? '') as String,
      name:
          (json['full_name'] ?? json['fullName'] ?? json['name'] ?? '')
              as String,
      role: (json['role'] ?? 'USER') as String,
      avatar: (json['avatar'] ?? json['avatar_url']) as String?,
      permissions: parsePermissions(json['permissions']),
      permissionsVersion:
          (json['permissions_version'] ??
                  json['perm_ver'] ??
                  json['permissionsVersion'] as num?)
              ?.toInt() ??
          1,
    );
  }

  Map<String, dynamic> toJson() => {
    'id': id,
    'email': email,
    'name': name,
    'role': role,
    'avatar': avatar,
    'permissions': permissions,
    'permissions_version': permissionsVersion,
  };
}
