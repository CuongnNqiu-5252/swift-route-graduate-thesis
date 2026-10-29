// presentation/screens/login_screen.dart

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:font_awesome_flutter/font_awesome_flutter.dart';

import '../../core/theme/app_colors.dart';
import '../providers/auth_provider.dart';

// ─────────────────────────────────────────────────────────────────────────────
// Enums & Providers local to Presentation layer
// ─────────────────────────────────────────────────────────────────────────────

/// Represents the role the user is selecting on the login screen.
/// This is purely a UI concern — the backend may or may not use it directly.
enum UserRole { customer, driver }

/// Local UI state: tracks which role pill is selected on the login screen.
final _selectedRoleProvider = StateProvider<UserRole>((_) => UserRole.customer);

// ─────────────────────────────────────────────────────────────────────────────
// LoginScreen
// ─────────────────────────────────────────────────────────────────────────────

class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen>
    with SingleTickerProviderStateMixin {
  final _formKey = GlobalKey<FormState>();
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();

  bool _obscurePassword = true;
  bool _keepLoggedIn = false;

  late final AnimationController _slideController;
  late final Animation<Offset> _slideAnimation;
  late final Animation<double> _fadeAnimation;

  @override
  void initState() {
    super.initState();

    _slideController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 600),
    );

    _slideAnimation = Tween<Offset>(
      begin: const Offset(0, 0.15),
      end: Offset.zero,
    ).animate(CurvedAnimation(parent: _slideController, curve: Curves.easeOut));

    _fadeAnimation = CurvedAnimation(
      parent: _slideController,
      curve: Curves.easeOut,
    );

    // Delay slightly so the illustration is visible first
    Future.delayed(const Duration(milliseconds: 120), _slideController.forward);
  }

  @override
  void dispose() {
    _slideController.dispose();
    _emailController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  Future<void> _handleLogin() async {
    if (!_formKey.currentState!.validate()) return;

    final success = await ref
        .read(authProvider.notifier)
        .login(_emailController.text.trim(), _passwordController.text);

    if (success && mounted) {
      final user = ref.read(currentUserProvider);
      final route = user?.role == 'DRIVER' ? '/driver/home' : '/customer/home';
      Navigator.of(context).pushReplacementNamed(route);
    }
  }

  @override
  Widget build(BuildContext context) {
    final authState = ref.watch(authProvider);

    // Show SnackBar on auth errors
    ref.listen<AuthState>(authProvider, (prev, next) {
      if (next.errorMessage != null &&
          next.errorMessage != prev?.errorMessage) {
        ScaffoldMessenger.of(context)
          ..hideCurrentSnackBar()
          ..showSnackBar(
            SnackBar(
              content: Text(next.errorMessage!),
              backgroundColor: AppColors.error,
              behavior: SnackBarBehavior.floating,
              action: SnackBarAction(
                label: 'Dismiss',
                textColor: Colors.white,
                onPressed: () => ref.read(authProvider.notifier).clearError(),
              ),
            ),
          );
      }
    });

    return Scaffold(
      backgroundColor: AppColors.primary,
      body: Stack(
        children: [
          // ── Top hero section ─────────────────────────────
          _HeroSection(),

          // ── Sliding form card ─────────────────────────────
          SlideTransition(
            position: _slideAnimation,
            child: FadeTransition(
              opacity: _fadeAnimation,
              child: Align(
                alignment: Alignment.bottomCenter,
                child: _FormCard(
                  formKey: _formKey,
                  emailController: _emailController,
                  passwordController: _passwordController,
                  obscurePassword: _obscurePassword,
                  keepLoggedIn: _keepLoggedIn,
                  isLoading: authState.isLoading,
                  onTogglePassword: () =>
                      setState(() => _obscurePassword = !_obscurePassword),
                  onToggleKeepLoggedIn: (val) =>
                      setState(() => _keepLoggedIn = val ?? false),
                  onLogin: _handleLogin,
                  onForgotPassword: () =>
                      Navigator.pushNamed(context, '/forgot-password'),
                  onRegister: () => Navigator.pushNamed(context, '/register'),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// _HeroSection — top illustration + header bar + floating badges
// ─────────────────────────────────────────────────────────────────────────────

class _HeroSection extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final screenH = MediaQuery.of(context).size.height;
    final selectedRole = ref.watch(_selectedRoleProvider);

    return SizedBox(
      height: screenH < 750 ? screenH * 0.28 : screenH * 0.34,
      child: Stack(
        fit: StackFit.expand,
        children: [
          // Isometric illustration background (gradient fallback until asset is set)
          _IllustrationBackground(),

          // Bottom gradient overlay — fades image into card colour
          Positioned(
            left: 0,
            right: 0,
            bottom: 0,
            height: screenH * 0.46 * 0.35,
            child: DecoratedBox(
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                  colors: [
                    AppColors.primary.withOpacity(0),
                    AppColors.primary.withOpacity(0.82),
                  ],
                ),
              ),
            ),
          ),

          // Top bar: wordmark + role toggle
          SafeArea(
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 4),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  // Wordmark
                  Row(
                    children: [
                      const Icon(
                        Icons.local_shipping_rounded,
                        color: Colors.white,
                        size: 22,
                      ),
                      const SizedBox(width: 8),
                      Text(
                        'SwiftRoute',
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 18,
                          fontWeight: FontWeight.w700,
                          letterSpacing: 0.4,
                        ),
                      ),
                    ],
                  ),

                  // Role toggle pill
                  _RolePillToggle(
                    selected: selectedRole,
                    onChanged: (role) =>
                        ref.read(_selectedRoleProvider.notifier).state = role,
                  ),
                ],
              ),
            ),
          ),

          // Floating badge chips (bottom-left)
          Positioned(
            left: 20,
            bottom: 50,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: const [
                _BadgeChip(emoji: '🚀', label: 'On-time 98%'),
                SizedBox(height: 8),
                _LiveTrackingChip(),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// _IllustrationBackground
// Renders a painted isometric-style city scene as a pure Flutter canvas,
// so no external asset file is required out of the box.
// Replace the child's DecoratedBox with an Image.asset() once the
// real illustration asset is added to assets/images/.
// ─────────────────────────────────────────────────────────────────────────────

class _IllustrationBackground extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Image.asset(
      'assets/images/login_background.png',
      fit: BoxFit.fill,
      errorBuilder: (context, error, stackTrace) {
        return Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.broken_image, color: Colors.orange, size: 40),
            SizedBox(height: 8),
            Text(
              'Hình ảnh asset không tìm thấy.',
              style: TextStyle(color: Colors.grey),
            ),
          ],
        );
      },
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// _RolePillToggle
// ─────────────────────────────────────────────────────────────────────────────

class _RolePillToggle extends StatelessWidget {
  const _RolePillToggle({required this.selected, required this.onChanged});

  final UserRole selected;
  final ValueChanged<UserRole> onChanged;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(3),
      decoration: BoxDecoration(
        color: Colors.white.withOpacity(0.18),
        borderRadius: BorderRadius.circular(100),
        border: Border.all(color: Colors.white.withOpacity(0.3)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          _PillOption(
            label: 'Customer',
            isActive: selected == UserRole.customer,
            onTap: () => onChanged(UserRole.customer),
          ),
          _PillOption(
            label: 'Driver',
            isActive: selected == UserRole.driver,
            onTap: () => onChanged(UserRole.driver),
          ),
        ],
      ),
    );
  }
}

class _PillOption extends StatelessWidget {
  const _PillOption({
    required this.label,
    required this.isActive,
    required this.onTap,
  });

  final String label;
  final bool isActive;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 220),
        curve: Curves.easeInOut,
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
        decoration: BoxDecoration(
          color: isActive ? Colors.white : Colors.transparent,
          borderRadius: BorderRadius.circular(100),
        ),
        child: Text(
          label,
          style: TextStyle(
            fontSize: 13,
            fontWeight: FontWeight.w600,
            color: isActive ? AppColors.primary : Colors.white.withOpacity(0.8),
          ),
        ),
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// _BadgeChip — floating white pill badges in the hero section
// ─────────────────────────────────────────────────────────────────────────────

class _BadgeChip extends StatelessWidget {
  const _BadgeChip({required this.emoji, required this.label});

  final String emoji;
  final String label;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 7),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(100),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.14),
            blurRadius: 8,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(emoji, style: const TextStyle(fontSize: 14)),
          const SizedBox(width: 6),
          Text(
            label,
            style: const TextStyle(
              fontSize: 13,
              fontWeight: FontWeight.w600,
              color: AppColors.textPrimary,
            ),
          ),
        ],
      ),
    );
  }
}

class _LiveTrackingChip extends StatefulWidget {
  const _LiveTrackingChip();

  @override
  State<_LiveTrackingChip> createState() => _LiveTrackingChipState();
}

class _LiveTrackingChipState extends State<_LiveTrackingChip>
    with SingleTickerProviderStateMixin {
  late final AnimationController _pulseController;

  @override
  void initState() {
    super.initState();
    _pulseController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 900),
    )..repeat(reverse: true);
  }

  @override
  void dispose() {
    _pulseController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 7),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(100),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.14),
            blurRadius: 8,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          AnimatedBuilder(
            animation: _pulseController,
            builder: (_, __) => Container(
              width: 9,
              height: 9,
              decoration: BoxDecoration(
                color: Color.lerp(
                  AppColors.accent,
                  const Color(0xFFFF4D4D),
                  _pulseController.value,
                ),
                shape: BoxShape.circle,
              ),
            ),
          ),
          const SizedBox(width: 8),
          const Text(
            'Live tracking',
            style: TextStyle(
              fontSize: 13,
              fontWeight: FontWeight.w600,
              color: AppColors.textPrimary,
            ),
          ),
        ],
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// _FormCard — white rounded bottom sheet with all form content
// ─────────────────────────────────────────────────────────────────────────────

class _FormCard extends StatelessWidget {
  const _FormCard({
    required this.formKey,
    required this.emailController,
    required this.passwordController,
    required this.obscurePassword,
    required this.keepLoggedIn,
    required this.isLoading,
    required this.onTogglePassword,
    required this.onToggleKeepLoggedIn,
    required this.onLogin,
    required this.onForgotPassword,
    required this.onRegister,
  });

  final GlobalKey<FormState> formKey;
  final TextEditingController emailController;
  final TextEditingController passwordController;
  final bool obscurePassword;
  final bool keepLoggedIn;
  final bool isLoading;
  final VoidCallback onTogglePassword;
  final ValueChanged<bool?> onToggleKeepLoggedIn;
  final VoidCallback onLogin;
  final VoidCallback onForgotPassword;
  final VoidCallback onRegister;

  @override
  Widget build(BuildContext context) {
    return Container(
      constraints: BoxConstraints(
        maxHeight: MediaQuery.of(context).size.height * 0.68,
      ),
      decoration: const BoxDecoration(
        color: AppColors.background,
        borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
        boxShadow: [
          BoxShadow(
            color: Color(0x22000000),
            blurRadius: 24,
            offset: Offset(0, -6),
          ),
        ],
      ),
      child: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(24, 32, 24, 32),
        child: Form(
          key: formKey,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Headline
              const Text(
                'Welcome back',
                style: TextStyle(
                  fontSize: 28,
                  fontWeight: FontWeight.w800,
                  color: AppColors.primary,
                  letterSpacing: -0.5,
                ),
              ),
              const SizedBox(height: 6),
              const Text(
                'Log in to track and manage your deliveries',
                style: TextStyle(
                  fontSize: 14,
                  color: AppColors.textSecondary,
                  height: 1.4,
                ),
              ),
              const SizedBox(height: 28),

              // Email / phone field
              _AuthField(
                controller: emailController,
                hint: 'Phone number or email',
                prefixIcon: Icons.alternate_email_rounded,
                keyboardType: TextInputType.emailAddress,
                enabled: !isLoading,
                validator: (v) {
                  if (v == null || v.trim().isEmpty) {
                    return 'Please enter your email or phone number';
                  }
                  return null;
                },
              ),
              const SizedBox(height: 14),

              // Password field
              _AuthField(
                controller: passwordController,
                hint: 'Password',
                prefixIcon: Icons.lock_outline_rounded,
                obscureText: obscurePassword,
                enabled: !isLoading,
                onFieldSubmitted: (_) => onLogin(),
                suffixIcon: IconButton(
                  icon: Icon(
                    obscurePassword
                        ? Icons.visibility_outlined
                        : Icons.visibility_off_outlined,
                    color: AppColors.textHint,
                    size: 20,
                  ),
                  onPressed: onTogglePassword,
                ),
                validator: (v) {
                  if (v == null || v.isEmpty)
                    return 'Please enter your password';
                  if (v.length < 6)
                    return 'Password must be at least 6 characters';
                  return null;
                },
              ),
              const SizedBox(height: 14),

              // Keep me logged in + Forgot password
              Row(
                children: [
                  SizedBox(
                    width: 20,
                    height: 20,
                    child: Checkbox(
                      value: keepLoggedIn,
                      onChanged: onToggleKeepLoggedIn,
                      activeColor: AppColors.primary,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(4),
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  const Text(
                    'Keep me logged in',
                    style: TextStyle(
                      fontSize: 13,
                      color: AppColors.textSecondary,
                    ),
                  ),
                  const Spacer(),
                  GestureDetector(
                    onTap: onForgotPassword,
                    child: const Text(
                      'Forgot password?',
                      style: TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.w600,
                        color: AppColors.accent,
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 24),

              // Primary login button
              _PrimaryButton(
                label: 'Log In',
                isLoading: isLoading,
                onTap: onLogin,
              ),
              const SizedBox(height: 22),

              // OR divider
              const _OrDivider(),
              const SizedBox(height: 18),

              // Google SSO button
              _GoogleButton(isLoading: isLoading),
              const SizedBox(height: 28),

              // Sign up footer
              Center(
                child: GestureDetector(
                  onTap: onRegister,
                  child: RichText(
                    text: const TextSpan(
                      style: TextStyle(
                        fontSize: 14,
                        color: AppColors.textSecondary,
                      ),
                      children: [
                        TextSpan(text: "Don't have an account? "),
                        TextSpan(
                          text: 'Sign up',
                          style: TextStyle(
                            color: AppColors.accent,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// _AuthField — reusable text input for the login form
// ─────────────────────────────────────────────────────────────────────────────

class _AuthField extends StatelessWidget {
  const _AuthField({
    required this.controller,
    required this.hint,
    required this.prefixIcon,
    this.obscureText = false,
    this.enabled = true,
    this.keyboardType,
    this.suffixIcon,
    this.validator,
    this.onFieldSubmitted,
  });

  final TextEditingController controller;
  final String hint;
  final IconData prefixIcon;
  final bool obscureText;
  final bool enabled;
  final TextInputType? keyboardType;
  final Widget? suffixIcon;
  final String? Function(String?)? validator;
  final void Function(String)? onFieldSubmitted;

  @override
  Widget build(BuildContext context) {
    return TextFormField(
      controller: controller,
      obscureText: obscureText,
      enabled: enabled,
      keyboardType: keyboardType,
      textInputAction: onFieldSubmitted != null
          ? TextInputAction.done
          : TextInputAction.next,
      onFieldSubmitted: onFieldSubmitted,
      style: const TextStyle(fontSize: 15, color: AppColors.textPrimary),
      validator: validator,
      decoration: InputDecoration(
        hintText: hint,
        hintStyle: const TextStyle(color: AppColors.textHint, fontSize: 15),
        prefixIcon: Icon(prefixIcon, color: AppColors.textHint, size: 20),
        suffixIcon: suffixIcon,
        filled: true,
        fillColor: AppColors.surface,
        contentPadding: const EdgeInsets.symmetric(
          horizontal: 16,
          vertical: 16,
        ),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: BorderSide.none,
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: BorderSide.none,
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: const BorderSide(color: AppColors.primary, width: 1.5),
        ),
        errorBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: const BorderSide(color: AppColors.error, width: 1),
        ),
        focusedErrorBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: const BorderSide(color: AppColors.error, width: 1.5),
        ),
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// _PrimaryButton
// ─────────────────────────────────────────────────────────────────────────────

class _PrimaryButton extends StatelessWidget {
  const _PrimaryButton({
    required this.label,
    required this.isLoading,
    required this.onTap,
  });

  final String label;
  final bool isLoading;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      height: 52,
      child: ElevatedButton(
        onPressed: isLoading ? null : onTap,
        style: ElevatedButton.styleFrom(
          backgroundColor: AppColors.primary,
          disabledBackgroundColor: AppColors.primary.withOpacity(0.5),
          foregroundColor: Colors.white,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(14),
          ),
          elevation: 0,
        ),
        child: isLoading
            ? const SizedBox(
                width: 22,
                height: 22,
                child: CircularProgressIndicator(
                  strokeWidth: 2.5,
                  color: Colors.white,
                ),
              )
            : Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: const [
                  Text(
                    'Log In',
                    style: TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w700,
                      letterSpacing: 0.2,
                    ),
                  ),
                  SizedBox(width: 8),
                  Icon(Icons.arrow_forward_rounded, size: 18),
                ],
              ),
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// _OrDivider
// ─────────────────────────────────────────────────────────────────────────────

class _OrDivider extends StatelessWidget {
  const _OrDivider();

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        const Expanded(child: Divider(color: AppColors.divider, thickness: 1)),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12),
          child: Text(
            'OR CONTINUE WITH',
            style: TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.w600,
              color: AppColors.textHint,
              letterSpacing: 0.6,
            ),
          ),
        ),
        const Expanded(child: Divider(color: AppColors.divider, thickness: 1)),
      ],
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// _GoogleButton
// ─────────────────────────────────────────────────────────────────────────────

class _GoogleButton extends StatelessWidget {
  const _GoogleButton({required this.isLoading});

  final bool isLoading;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      height: 52,
      child: OutlinedButton(
        onPressed: isLoading
            ? null
            : () {
                /* TODO: Google SSO */
              },
        style: OutlinedButton.styleFrom(
          backgroundColor: AppColors.surface,
          side: const BorderSide(color: AppColors.divider),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(14),
          ),
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            // Google "G" logo drawn via FaIcon
            const FaIcon(
              FontAwesomeIcons.google,
              size: 18,
              color: Color(0xFFEA4335),
            ),
            const SizedBox(width: 10),
            const Text(
              'Continue with Google',
              style: TextStyle(
                fontSize: 15,
                fontWeight: FontWeight.w600,
                color: AppColors.textPrimary,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
