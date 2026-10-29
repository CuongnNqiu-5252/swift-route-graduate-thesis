import 'package:swiftroute_app/domain/entities/user.dart';

abstract class AuthRepository {
  Future<User> login(String identifier, String password);

  Future<void> register(
    String email,
    String password,
    String username, {
    String phoneNumber,
    String address,
    String city,
    String state,
    String zipCode,
    String country,
  });

  Future<User?> getCurrentUser();
  Future<void> logout();
}
