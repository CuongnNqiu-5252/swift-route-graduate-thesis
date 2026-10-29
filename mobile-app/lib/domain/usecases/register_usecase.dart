import 'package:swiftroute_app/domain/repositories/auth_repository.dart';

class RegisterUseCase {
  final AuthRepository _repository;

  RegisterUseCase(this._repository);

  Future<void> execute(String email, String password, String username) {
    return _repository.register(email, password, username);
  }
}
