// presentation/providers/auth_provider.dart

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/data/repositories/auth_repository_impl.dart';
import 'package:swiftroute_app/domain/entities/user.dart';
import 'package:swiftroute_app/domain/usecases/get_current_user_usecase.dart';
import 'package:swiftroute_app/domain/usecases/login_usecase.dart';
import 'package:swiftroute_app/domain/usecases/logout_usecase.dart';
import 'package:swiftroute_app/domain/usecases/register_usecase.dart';

/// State của màn hình xác thực (nằm ở Presentation layer)
class AuthState {
  final bool isLoading;
  final User? user; // Sử dụng Domain Entity User
  final String? errorMessage;
  final bool isAuthenticated;

  const AuthState({
    this.isLoading = false,
    this.user,
    this.errorMessage,
    this.isAuthenticated = false,
  });

  AuthState copyWith({
    bool? isLoading,
    User? user,
    String? errorMessage,
    bool? isAuthenticated,
  }) {
    return AuthState(
      isLoading: isLoading ?? this.isLoading,
      user: user ?? this.user,
      errorMessage: errorMessage,
      isAuthenticated: isAuthenticated ?? this.isAuthenticated,
    );
  }
}

/// Providers cho các UseCases từ Domain layer
final loginUseCaseProvider = Provider<LoginUseCase>((ref) {
  return LoginUseCase(ref.read(authRepositoryProvider));
});

final registerUseCaseProvider = Provider<RegisterUseCase>((ref) {
  return RegisterUseCase(ref.read(authRepositoryProvider));
});

final getCurrentUserUseCaseProvider = Provider<GetCurrentUserUseCase>((ref) {
  return GetCurrentUserUseCase(ref.read(authRepositoryProvider));
});

final logoutUseCaseProvider = Provider<LogoutUseCase>((ref) {
  return LogoutUseCase(ref.read(authRepositoryProvider));
});

/// Provider chính quản lý trạng thái xác thực
final authProvider = StateNotifierProvider<AuthNotifier, AuthState>((ref) {
  return AuthNotifier(
    loginUseCase: ref.read(loginUseCaseProvider),
    registerUseCase: ref.read(registerUseCaseProvider),
    getCurrentUserUseCase: ref.read(getCurrentUserUseCaseProvider),
    logoutUseCase: ref.read(logoutUseCaseProvider),
  );
});

class AuthNotifier extends StateNotifier<AuthState> {
  final LoginUseCase _loginUseCase;
  final RegisterUseCase _registerUseCase;
  final GetCurrentUserUseCase _getCurrentUserUseCase;
  final LogoutUseCase _logoutUseCase;

  AuthNotifier({
    required LoginUseCase loginUseCase,
    required RegisterUseCase registerUseCase,
    required GetCurrentUserUseCase getCurrentUserUseCase,
    required LogoutUseCase logoutUseCase,
  }) : _loginUseCase = loginUseCase,
       _registerUseCase = registerUseCase,
       _getCurrentUserUseCase = getCurrentUserUseCase,
       _logoutUseCase = logoutUseCase,
       super(const AuthState()) {
    // Kiểm tra phiên đăng nhập khi khởi động app
    _checkAuthStatus();
    getCurrentUser();
  }

  Future<void> getCurrentUser() async {
    try {
      final user = await _getCurrentUserUseCase.execute();
      if (user != null) {
        state = state.copyWith(
          isLoading: false,
          user: user,
          isAuthenticated: true,
        );
      } else {
        state = state.copyWith(isLoading: false);
      }
    } catch (_) {
      state = state.copyWith(isLoading: false);
    }
  }

  Future<void> _checkAuthStatus() async {
    state = state.copyWith(isLoading: true);
    try {
      final user = await _getCurrentUserUseCase.execute();
      if (user != null) {
        state = state.copyWith(
          isLoading: false,
          user: user,
          isAuthenticated: true,
        );
      } else {
        state = state.copyWith(isLoading: false);
      }
    } catch (_) {
      state = state.copyWith(isLoading: false);
    }
  }

  Future<bool> register(String email, String password, String name) async {
    state = state.copyWith(isLoading: true, errorMessage: null);

    try {
      await _registerUseCase.execute(email.trim(), password, name);
      state = state.copyWith(isLoading: false, errorMessage: null);
      return true;
    } catch (e) {
      state = state.copyWith(
        isLoading: false,
        errorMessage: e.toString().replaceFirst('Exception: ', ''),
      );
      return false;
    }
  }

  Future<bool> login(String email, String password) async {
    state = state.copyWith(isLoading: true, errorMessage: null);

    try {
      final user = await _loginUseCase.execute(email.trim(), password);
      state = state.copyWith(
        isLoading: false,
        user: user,
        isAuthenticated: true,
        errorMessage: null,
      );
      return true;
    } catch (e) {
      state = state.copyWith(
        isLoading: false,
        errorMessage: e.toString().replaceFirst('Exception: ', ''),
      );
      return false;
    }
  }

  Future<void> logout() async {
    state = state.copyWith(isLoading: true);
    await _logoutUseCase.execute();
    state = const AuthState(); // Reset về trạng thái ban đầu
  }

  void clearError() {
    state = state.copyWith(errorMessage: null);
  }
}

final isAuthenticatedProvider = Provider<bool>(
  (ref) => ref.watch(authProvider).isAuthenticated,
);

final currentUserProvider = Provider<User?>(
  (ref) => ref.watch(authProvider).user,
);

final authLoadingProvider = Provider<bool>(
  (ref) => ref.watch(authProvider).isLoading,
);
