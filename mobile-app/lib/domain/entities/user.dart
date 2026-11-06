class User {
  final String id; // UUID from backend
  final String email;
  final String name;
  final String
  role; // Effective role for the active JWT/workspace (for example DRIVER)
  final String? avatar;

  const User({
    required this.id,
    required this.email,
    required this.name,
    required this.role,
    this.avatar,
  });
}
