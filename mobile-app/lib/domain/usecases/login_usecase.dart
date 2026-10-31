import 'package:swiftroute_app/domain/entities/user.dart';
import 'package:swiftroute_app/domain/repositories/auth_repository.dart';

class LoginUseCase {
  final AuthRepository _repository;

  LoginUseCase(this._repository);

  Future<User> execute(String identifier, String password) {
    return _repository.login(identifier, password);
  }
}
