// main.dart

import 'package:firebase_core/firebase_core.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:swiftroute_app/core/theme/app_colors.dart';
import 'package:swiftroute_app/core/theme/app_theme.dart';
import 'package:swiftroute_app/features/driver/presentation/screens/driver_main_screen.dart';
import 'package:swiftroute_app/firebase_options.dart';
import 'package:swiftroute_app/presentation/providers/auth_provider.dart';
import 'package:swiftroute_app/presentation/screens/driver_activation_screen.dart';
import 'package:swiftroute_app/presentation/screens/login_screen.dart';
import 'package:swiftroute_app/presentation/screens/forgot_password_screen.dart';
import 'package:swiftroute_app/presentation/screens/register_screen.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  try {
    await Firebase.initializeApp(
      options: DefaultFirebaseOptions.currentPlatform,
    );
  } catch (e) {
    debugPrint('Firebase initialization ignored/failed: $e');
  }
  GoogleFonts.config.allowRuntimeFetching = true;
  runApp(const ProviderScope(child: SwiftRouteApp()));
}

class SwiftRouteApp extends ConsumerWidget {
  const SwiftRouteApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final authState = ref.watch(authProvider);

    return MaterialApp(
      title: 'SwiftRoute Driver',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.light(),
      routes: {
        '/login': (_) => const LoginScreen(),
        '/forgot-password': (_) => const ForgotPasswordScreen(),
        '/register': (_) => RegisterScreen(),
        '/driver/activate': (_) => const DriverActivationScreen(),
        '/driver/home': (_) => const DriverMainScreen(),
      },
      home: _AuthGate(authState: authState),
    );
  }
}

/// Watches AuthState and routes drivers directly to the operational shell.
class _AuthGate extends StatelessWidget {
  const _AuthGate({required this.authState});

  final AuthState authState;

  @override
  Widget build(BuildContext context) {
    if (authState.isLoading) {
      return const _SplashScreen();
    }

    if (!authState.isAuthenticated || authState.user == null) {
      return const LoginScreen();
    }

    return switch (authState.user!.role) {
      'DRIVER' => const DriverMainScreen(),
      _ => const _UnsupportedRoleScreen(),
    };
  }
}

class _UnsupportedRoleScreen extends ConsumerWidget {
  const _UnsupportedRoleScreen();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Scaffold(
      backgroundColor: AppColors.background,
      body: SafeArea(
        child: Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Container(
              padding: const EdgeInsets.all(24),
              decoration: BoxDecoration(
                color: AppColors.card,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: AppColors.divider),
              ),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Container(
                    width: 56,
                    height: 56,
                    decoration: BoxDecoration(
                      color: AppColors.primarySoft,
                      borderRadius: BorderRadius.circular(14),
                    ),
                    child: const Icon(
                      Icons.two_wheeler_rounded,
                      size: 30,
                      color: AppColors.primary,
                    ),
                  ),
                  const SizedBox(height: 16),
                  const Text(
                    'Ứng dụng dành riêng cho Tài xế',
                    style: TextStyle(
                      fontSize: 19,
                      fontWeight: FontWeight.w800,
                      color: AppColors.textPrimary,
                    ),
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 8),
                  const Text(
                    'Tài khoản hiện tại không thuộc vai trò tài xế (DRIVER). Vui lòng đăng xuất và đăng nhập bằng tài khoản tài xế được cấp.',
                    style: TextStyle(
                      fontSize: 14,
                      height: 1.45,
                      color: AppColors.textSecondary,
                    ),
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 20),
                  SizedBox(
                    width: double.infinity,
                    child: FilledButton.icon(
                      onPressed: () => ref.read(authProvider.notifier).logout(),
                      icon: const Icon(Icons.logout_rounded, size: 18),
                      label: const Text('Đăng xuất để đổi tài khoản'),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _SplashScreen extends StatelessWidget {
  const _SplashScreen();

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.primaryDark,
      body: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 64,
              height: 64,
              decoration: BoxDecoration(
                color: Colors.white.withValues(alpha: 0.14),
                borderRadius: BorderRadius.circular(18),
              ),
              child: const Icon(
                Icons.route_rounded,
                color: Colors.white,
                size: 34,
              ),
            ),
            const SizedBox(height: 16),
            const Text(
              'SwiftRoute Driver',
              style: TextStyle(
                color: Colors.white,
                fontSize: 22,
                fontWeight: FontWeight.w800,
                letterSpacing: -0.4,
              ),
            ),
            const SizedBox(height: 20),
            const SizedBox(
              width: 24,
              height: 24,
              child: CircularProgressIndicator(
                color: Colors.white,
                strokeWidth: 2.5,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
