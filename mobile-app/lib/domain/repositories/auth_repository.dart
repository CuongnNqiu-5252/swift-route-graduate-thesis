import 'package:swiftroute_app/domain/entities/user.dart';

abstract class AuthRepository {
  Future<User> login(String identifier, String password);
  Future<User> activateWithCode(String activationCode);
  Future<void> syncDeviceActivity({bool force = false});

  Future<User?> getCurrentUser();
  Future<void> logout();
}
