class User {
  final String id; // UUID from backend
  final String email;
  final String name;
  final String
  role; // Effective role for the active JWT/workspace (for example DRIVER)
  final String? avatar;
  final List<String> permissions;
  final int permissionsVersion;

  const User({
    required this.id,
    required this.email,
    required this.name,
    required this.role,
    this.avatar,
    this.permissions = const [],
    this.permissionsVersion = 1,
  });

  bool hasPermission(String code) {
    final upperRole = role.toUpperCase();
    if (upperRole == 'OWNER' || upperRole == 'SYSTEM_ADMIN') {
      return true;
    }
    if (permissions.isEmpty) {
      return true;
    }
    return permissions.contains(code.trim().toLowerCase());
  }
}
