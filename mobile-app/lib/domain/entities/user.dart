class User {
  final String id; // UUID from backend
  final String email;
  final String name;
  final String role; // CUSTOMER | DRIVER | SYSTEM_ADMIN | OPS_STAFF
  final String? avatar;

  const User({
    required this.id,
    required this.email,
    required this.name,
    required this.role,
    this.avatar,
  });
}
