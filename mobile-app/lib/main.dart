// main.dart

import 'package:firebase_core/firebase_core.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:swiftroute_app/core/theme/app_colors.dart';
import 'package:swiftroute_app/features/driver/presentation/screens/driver_main_screen.dart';
import 'package:swiftroute_app/firebase_options.dart';
import 'package:swiftroute_app/presentation/providers/auth_provider.dart';
import 'package:swiftroute_app/presentation/screens/home_screen.dart';
import 'package:swiftroute_app/presentation/screens/login_screen.dart';
import 'package:swiftroute_app/presentation/screens/register_screen.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  try {
    await Firebase.initializeApp(options: DefaultFirebaseOptions.currentPlatform);
  } catch (e) {
    debugPrint('Firebase initialization ignored/failed: $e');
  }
  GoogleFonts.config.allowRuntimeFetching = false;
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
        fontFamily: 'Inter',
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
        '/driver/home': (_) => const DriverMainScreen(),
        '/customer/home': (_) => const HomeScreen(),
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
      _ => const HomeScreen(), // CUSTOMER, OPS_STAFF, SYSTEM_ADMIN
    };
  }
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
