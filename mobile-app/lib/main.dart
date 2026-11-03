// main.dart

import 'package:firebase_core/firebase_core.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:swiftroute_app/core/theme/app_colors.dart';
import 'package:swiftroute_app/features/driver/presentation/screens/driver_main_screen.dart';
import 'package:swiftroute_app/firebase_options.dart';
import 'package:swiftroute_app/presentation/providers/auth_provider.dart';
import 'package:swiftroute_app/presentation/screens/login_screen.dart';
import 'package:swiftroute_app/presentation/screens/register_screen.dart';
import 'package:swiftroute_app/presentation/screens/driver_activation_screen.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  try {
    await Firebase.initializeApp(options: DefaultFirebaseOptions.currentPlatform);
  } catch (e) {
    debugPrint('Firebase initialization ignored/failed: $e');
  }
  // Enable runtime fetching so GoogleFonts can safely retrieve full Vietnamese glyph sets
  GoogleFonts.config.allowRuntimeFetching = true;
  runApp(const ProviderScope(child: SwiftRouteApp()));
}

class SwiftRouteApp extends ConsumerWidget {
  const SwiftRouteApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final authState = ref.watch(authProvider);

    return MaterialApp(
      title: 'SwiftRoute',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        useMaterial3: true,
        textTheme: GoogleFonts.robotoTextTheme(ThemeData.light().textTheme),
        colorScheme: ColorScheme.fromSeed(
          seedColor: AppColors.primary,
          brightness: Brightness.light,
          primary: AppColors.primary,
          secondary: AppColors.accent,
          surface: AppColors.surface,
          error: AppColors.error,
        ),
        scaffoldBackgroundColor: AppColors.background,
      ),
      // ── Route table ─────────────────────────────────────────────────────────
      routes: {
        '/login': (_) => const LoginScreen(),
        '/register': (_) => RegisterScreen(),
        '/driver/activate': (_) => const DriverActivationScreen(),
        '/driver/home': (_) => const DriverMainScreen(),
      },
      // ── Auth gate: decide which screen to show on startup ────────────────────
      home: _AuthGate(authState: authState),
    );
  }
}

/// Watches AuthState and redirects to the correct home screen based on role.
class _AuthGate extends StatelessWidget {
  const _AuthGate({required this.authState});

  final AuthState authState;

  @override
  Widget build(BuildContext context) {
    // Show splash while checking stored token
    if (authState.isLoading) {
      return const _SplashScreen();
    }

    if (!authState.isAuthenticated || authState.user == null) {
      return const LoginScreen();
    }

    // Role-based routing
    return switch (authState.user!.role) {
      'DRIVER' => const DriverMainScreen(),
      _ => const _UnsupportedRoleScreen(),
    };
  }
}

class _UnsupportedRoleScreen extends ConsumerWidget {
  const _UnsupportedRoleScreen();
  @override
  Widget build(BuildContext context, WidgetRef ref) => Scaffold(
    body: Center(child: Padding(
      padding: const EdgeInsets.all(24),
      child: Column(mainAxisSize: MainAxisSize.min, children: [
        const Icon(Icons.local_shipping_outlined, size: 52, color: AppColors.primary),
        const SizedBox(height: 18),
        const Text('Ứng dụng này dành cho tài xế.', style: TextStyle(fontSize: 20, fontWeight: FontWeight.w700), textAlign: TextAlign.center),
        const SizedBox(height: 8),
        const Text('Tài khoản hiện tại không có role DRIVER. Đăng xuất rồi kích hoạt hoặc đăng nhập bằng tài khoản tài xế.', textAlign: TextAlign.center),
        const SizedBox(height: 22),
        FilledButton(
          onPressed: () => ref.read(authProvider.notifier).logout(),
          child: const Text('Đăng xuất để đổi tài khoản'),
        ),
      ]),
    )),
  );
}

// ── Splash ───────────────────────────────────────────────────────────────────

class _SplashScreen extends StatelessWidget {
  const _SplashScreen();

  @override
  Widget build(BuildContext context) {
    return const Scaffold(
      backgroundColor: AppColors.primary,
      body: Center(child: CircularProgressIndicator(color: Colors.white)),
    );
  }
}
