import 'package:swiftroute_app/domain/entities/user.dart';
import 'package:swiftroute_app/domain/repositories/auth_repository.dart';

class GetCurrentUserUseCase {
  final AuthRepository _repository;

  GetCurrentUserUseCase(this._repository);

  Future<User?> execute() {
    return _repository.getCurrentUser();
  }
}
