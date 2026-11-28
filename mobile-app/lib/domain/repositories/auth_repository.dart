import 'package:swiftroute_app/domain/entities/user.dart';

abstract class AuthRepository {
  Future<User> activateWithCode(String activationCode);
  Future<void> syncDeviceActivity({bool force = false});

  Future<User?> getCurrentUser();
  Future<void> logout();
}
