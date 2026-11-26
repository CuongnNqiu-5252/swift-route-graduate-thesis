// presentation/providers/auth_provider.dart

import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/network/dio.dart';
import 'package:swiftroute_app/core/offline/sqlite_offline_store.dart';
import 'package:swiftroute_app/data/repositories/auth_repository_impl.dart';
import 'package:swiftroute_app/domain/entities/user.dart';
import 'package:swiftroute_app/domain/repositories/auth_repository.dart';
import 'package:swiftroute_app/domain/usecases/get_current_user_usecase.dart';
import 'package:swiftroute_app/domain/usecases/login_usecase.dart';
import 'package:swiftroute_app/domain/usecases/logout_usecase.dart';

class AuthState {
  final bool isLoading;
  final User? user;
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

final loginUseCaseProvider = Provider<LoginUseCase>((ref) {
  return LoginUseCase(ref.read(authRepositoryProvider));
});

final getCurrentUserUseCaseProvider = Provider<GetCurrentUserUseCase>((ref) {
  return GetCurrentUserUseCase(ref.read(authRepositoryProvider));
});

final logoutUseCaseProvider = Provider<LogoutUseCase>((ref) {
  return LogoutUseCase(ref.read(authRepositoryProvider));
});

final authProvider = StateNotifierProvider<AuthNotifier, AuthState>((ref) {
  final notifier = AuthNotifier(
    loginUseCase: ref.read(loginUseCaseProvider),
    getCurrentUserUseCase: ref.read(getCurrentUserUseCaseProvider),
    logoutUseCase: ref.read(logoutUseCaseProvider),
    authRepository: ref.read(authRepositoryProvider),
  );

  ref.listen<int>(authSessionInvalidatedCounterProvider, (previous, next) {
    if (previous != null && next > previous) {
      unawaited(notifier.handleSessionRevoked());
    }
  });

  return notifier;
});

class AuthNotifier extends StateNotifier<AuthState> {
  final LoginUseCase _loginUseCase;
  final GetCurrentUserUseCase _getCurrentUserUseCase;
  final LogoutUseCase _logoutUseCase;
  final AuthRepository? _authRepository;

  AuthNotifier({
    required LoginUseCase loginUseCase,
    required GetCurrentUserUseCase getCurrentUserUseCase,
    required LogoutUseCase logoutUseCase,
    AuthRepository? authRepository,
  }) : _loginUseCase = loginUseCase,
       _getCurrentUserUseCase = getCurrentUserUseCase,
       _logoutUseCase = logoutUseCase,
       _authRepository = authRepository,
       super(const AuthState()) {
    _checkAuthStatus();
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
        state = const AuthState(isLoading: false);
      }
    } catch (_) {
      state = const AuthState(isLoading: false);
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
        if (user.role == 'DRIVER') {
          unawaited(_authRepository?.syncDeviceActivity(force: true));
        }
      } else {
        state = const AuthState(isLoading: false);
      }
    } catch (_) {
      state = const AuthState(isLoading: false);
    }
  }

  Future<bool> login(String email, String password) async {
    state = state.copyWith(isLoading: true, errorMessage: null);

    try {
      final user = await _loginUseCase.execute(email.trim(), password);
      if (user.role != 'DRIVER' || !user.hasPermission('driver:self')) {
        await _logoutUseCase.execute();
        state = AuthState(
          isLoading: false,
          user: null,
          isAuthenticated: false,
          errorMessage: user.role != 'DRIVER'
              ? 'Tài khoản không thuộc vai trò tài xế (DRIVER).'
              : 'Tài khoản tài xế hiện không có quyền tác nghiệp (driver:self). Vui lòng liên hệ quản trị viên.',
        );
        return false;
      }
      state = state.copyWith(
        isLoading: false,
        user: user,
        isAuthenticated: true,
        errorMessage: null,
      );
      unawaited(_authRepository?.syncDeviceActivity(force: true));
      return true;
    } catch (e) {
      state = state.copyWith(
        isLoading: false,
        errorMessage: e.toString().replaceFirst('Exception: ', ''),
      );
      return false;
    }
  }

  Future<bool> activateWithCode(String activationCode) async {
    final repo = _authRepository;
    if (repo == null) return false;
    state = state.copyWith(isLoading: true, errorMessage: null);

    try {
      final user = await repo.activateWithCode(activationCode);
      if (user.role != 'DRIVER' || !user.hasPermission('driver:self')) {
        await _logoutUseCase.execute();
        state = const AuthState(
          isLoading: false,
          errorMessage:
              'Tài khoản được liên kết không có quyền tài xế. Vui lòng liên hệ quản trị viên.',
        );
        return false;
      }
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

  void onAppResumed() {
    if (state.isAuthenticated && state.user?.role == 'DRIVER') {
      unawaited(_authRepository?.syncDeviceActivity());
    }
  }

  Future<void> handleSessionRevoked() async {
    if (!state.isAuthenticated && state.user == null) {
      return;
    }
    try {
      await SqliteOfflineStore.instance.clearAll();
    } catch (_) {}
    state = const AuthState(
      isLoading: false,
      user: null,
      isAuthenticated: false,
      errorMessage:
          'Phiên ứng dụng đã hết hạn hoặc bị quản trị viên hủy kích hoạt. Vui lòng đăng nhập hoặc nhập mã kích hoạt mới.',
    );
  }

  Future<void> logout() async {
    state = state.copyWith(isLoading: true);
    await _logoutUseCase.execute();
    state = const AuthState();
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
