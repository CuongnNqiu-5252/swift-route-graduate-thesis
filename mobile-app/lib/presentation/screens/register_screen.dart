import 'package:flutter/material.dart';
import 'package:swiftroute_app/presentation/providers/auth_provider.dart';
import 'package:swiftroute_app/presentation/screens/background.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../widget/button.dart';
import 'package:font_awesome_flutter/font_awesome_flutter.dart';

class RegisterScreen extends ConsumerStatefulWidget {
  const RegisterScreen({super.key});

  @override
  ConsumerState<RegisterScreen> createState() => _RegisterScreenState();
}

class _RegisterScreenState extends ConsumerState<RegisterScreen> {
  final _formKey = GlobalKey<FormState>();
  final TextEditingController _emailController = TextEditingController();
  final TextEditingController _passwordController = TextEditingController();
  final TextEditingController _nameController = TextEditingController();
  final TextEditingController _confirmPasswordController =
      TextEditingController();
  bool _obscurePassword = true;
  bool _obscureConfirmPassword = true;
  bool _showCard = false;

  @override
  void initState() {
    super.initState();

    Future.delayed(const Duration(milliseconds: 100), () {
      setState(() {
        _showCard = true;
      });
    });
  }

  @override
  void dispose() {
    _emailController.dispose();
    _passwordController.dispose();
    _nameController.dispose();
    _confirmPasswordController.dispose();
    super.dispose();
  }

  Future<void> _handleRegister() async {
    if (!_formKey.currentState!.validate()) return;
    final success = await ref
        .read(authProvider.notifier)
        .register(
          _emailController.text,
          _passwordController.text,
          _nameController.text,
        );
    if (success) {
      Navigator.pushReplacementNamed(context, '/login');
    }
  }

  @override
  Widget build(BuildContext context) {
    final authState = ref.watch(authProvider);
    final theme = Theme.of(context);
    const isLogin = false;

    ref.listen<AuthState>(authProvider, (previous, next) {
      if (next.errorMessage != null &&
          next.errorMessage != previous?.errorMessage) {
        ScaffoldMessenger.of(context)
          ..hideCurrentSnackBar()
          ..showSnackBar(
            SnackBar(
              content: Text(next.errorMessage!),
              backgroundColor: theme.colorScheme.error,
              behavior: SnackBarBehavior.floating,
              action: SnackBarAction(
                label: 'Đóng',
                textColor: Colors.white,
                onPressed: () {
                  ref.read(authProvider.notifier).clearError();
                },
              ),
            ),
          );
      }
    });

    return Scaffold(
      backgroundColor: const Color(0xFF0D0D1A),
      body: Stack(
        children: [
          BuildBackground(isLogin: isLogin),
          Align(
            alignment: Alignment.bottomCenter,
            child: AnimatedSlide(
              duration: const Duration(milliseconds: 500),
              curve: Curves.easeOut,
              offset: _showCard ? Offset.zero : const Offset(0, 1),
              child: AnimatedOpacity(
                opacity: _showCard ? 1 : 0,
                duration: const Duration(milliseconds: 500),
                child: Container(
                  height: MediaQuery.of(context).size.height * 0.8,
                  padding: EdgeInsets.symmetric(
                    horizontal: MediaQuery.of(context).size.width * 0.05,
                  ),
                  decoration: BoxDecoration(
                    color: const Color(0xFF1A1A2E),
                    borderRadius: const BorderRadius.vertical(
                      top: Radius.circular(32),
                    ),
                    boxShadow: [
                      BoxShadow(
                        color: Colors.black.withOpacity(0.4),
                        blurRadius: 20,
                        offset: const Offset(0, -4),
                      ),
                    ],
                    border: Border(
                      top: BorderSide(
                        color: const Color(0xFF7C4DFF).withOpacity(0.2),
                        width: 1,
                      ),
                    ),
                  ),
                  child: SingleChildScrollView(
                    child: Form(
                      key: _formKey,
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.center,
                        children: [
                          const SizedBox(height: 15),

                          // Logo / Tiêu đề
                          Align(
                            alignment: Alignment.center,
                            child: SizedBox(
                              width: double.infinity,
                              height: 80,
                              child: Column(
                                children: [
                                  SizedBox(height: 8),
                                  Text(
                                    'Tạo tài khoản mới!',
                                    textAlign: TextAlign.left,
                                    style: theme.textTheme.headlineMedium
                                        ?.copyWith(
                                          fontWeight: FontWeight.bold,
                                          color: Colors.white,
                                        ),
                                  ),
                                  const SizedBox(height: 8),
                                  const Text(
                                    'Vui lòng điền thông tin bên dưới',
                                    textAlign: TextAlign.center,
                                    style: TextStyle(color: Color(0xFF8E8EA0)),
                                  ),
                                ],
                              ),
                            ),
                          ),
                          SizedBox(height: 16),

                          // Họ tên field
                          TextFormField(
                            controller: _nameController,
                            textInputAction: TextInputAction.next,
                            enabled: !authState.isLoading,
                            style: const TextStyle(color: Colors.white),
                            decoration: InputDecoration(
                              labelText: 'Họ tên',
                              labelStyle: const TextStyle(
                                color: Color(0xFF8E8EA0),
                              ),
                              hintText: 'Nguyễn Văn A',
                              hintStyle: TextStyle(
                                color: const Color(0xFF8E8EA0).withOpacity(0.5),
                              ),
                              prefixIcon: const Icon(
                                Icons.person_outlined,
                                color: Color(0xFF8E8EA0),
                              ),
                              filled: true,
                              fillColor: const Color(0xFF16213E),
                              contentPadding: const EdgeInsets.symmetric(
                                horizontal: 16,
                                vertical: 14,
                              ),
                              border: const OutlineInputBorder(
                                borderRadius: BorderRadius.all(
                                  Radius.circular(12),
                                ),
                              ),
                              focusedBorder: OutlineInputBorder(
                                borderRadius: BorderRadius.circular(12),
                                borderSide: const BorderSide(
                                  color: Color(0xFF7C4DFF),
                                  width: 1.5,
                                ),
                              ),
                              enabledBorder: OutlineInputBorder(
                                borderRadius: BorderRadius.circular(12),
                                borderSide: const BorderSide(
                                  color: Color(0xFF2A2A4A),
                                ),
                              ),
                            ),
                            validator: (value) {
                              if (value == null || value.trim().isEmpty) {
                                return 'Vui lòng nhập họ tên';
                              }
                              return null;
                            },
                          ),
                          const SizedBox(height: 16),

                          // Email field
                          TextFormField(
                            controller: _emailController,
                            keyboardType: TextInputType.emailAddress,
                            textInputAction: TextInputAction.next,
                            enabled: !authState.isLoading,
                            style: const TextStyle(color: Colors.white),
                            decoration: InputDecoration(
                              labelText: 'Email',
                              labelStyle: const TextStyle(
                                color: Color(0xFF8E8EA0),
                              ),
                              hintText: 'example@email.com',
                              hintStyle: TextStyle(
                                color: const Color(0xFF8E8EA0).withOpacity(0.5),
                              ),
                              prefixIcon: const Icon(
                                Icons.email_outlined,
                                color: Color(0xFF8E8EA0),
                              ),
                              filled: true,
                              fillColor: const Color(0xFF16213E),
                              contentPadding: const EdgeInsets.symmetric(
                                horizontal: 16,
                                vertical: 14,
                              ),
                              border: const OutlineInputBorder(
                                borderRadius: BorderRadius.all(
                                  Radius.circular(12),
                                ),
                              ),
                              focusedBorder: OutlineInputBorder(
                                borderRadius: BorderRadius.circular(12),
                                borderSide: const BorderSide(
                                  color: Color(0xFF7C4DFF),
                                  width: 1.5,
                                ),
                              ),
                              enabledBorder: OutlineInputBorder(
                                borderRadius: BorderRadius.circular(12),
                                borderSide: const BorderSide(
                                  color: Color(0xFF2A2A4A),
                                ),
                              ),
                            ),
                            validator: (value) {
                              if (value == null || value.trim().isEmpty) {
                                return 'Vui lòng nhập email';
                              }
                              final emailRegex = RegExp(
                                r'^[\w-\.]+@([\w-]+\.)+[\w-]{2,4}$',
                              );
                              if (!emailRegex.hasMatch(value.trim())) {
                                return 'Email không đúng định dạng';
                              }
                              return null;
                            },
                          ),
                          const SizedBox(height: 16),

                          // Password field
                          TextFormField(
                            controller: _passwordController,
                            obscureText: _obscurePassword,
                            textInputAction: TextInputAction.next,
                            enabled: !authState.isLoading,
                            style: const TextStyle(color: Colors.white),
                            decoration: InputDecoration(
                              filled: true,
                              fillColor: const Color(0xFF16213E),
                              contentPadding: const EdgeInsets.symmetric(
                                horizontal: 16,
                                vertical: 14,
                              ),
                              labelText: 'Mật khẩu',
                              labelStyle: const TextStyle(
                                color: Color(0xFF8E8EA0),
                              ),
                              hintText: '••••••••',
                              hintStyle: TextStyle(
                                color: const Color(0xFF8E8EA0).withOpacity(0.5),
                              ),
                              prefixIcon: const Icon(
                                Icons.lock_outlined,
                                color: Color(0xFF8E8EA0),
                              ),
                              border: const OutlineInputBorder(
                                borderRadius: BorderRadius.all(
                                  Radius.circular(12),
                                ),
                              ),
                              enabledBorder: OutlineInputBorder(
                                borderRadius: BorderRadius.circular(12),
                                borderSide: const BorderSide(
                                  color: Color(0xFF2A2A4A),
                                ),
                              ),
                              focusedBorder: OutlineInputBorder(
                                borderRadius: BorderRadius.circular(12),
                                borderSide: const BorderSide(
                                  color: Color(0xFF7C4DFF),
                                  width: 1.5,
                                ),
                              ),
                              suffixIcon: IconButton(
                                icon: Icon(
                                  _obscurePassword
                                      ? Icons.visibility_outlined
                                      : Icons.visibility_off_outlined,
                                  color: const Color(0xFF8E8EA0),
                                ),
                                onPressed: () {
                                  setState(
                                    () => _obscurePassword = !_obscurePassword,
                                  );
                                },
                              ),
                            ),
                            validator: (value) {
                              if (value == null || value.isEmpty) {
                                return 'Vui lòng nhập mật khẩu';
                              }
                              if (value.length < 6) {
                                return 'Mật khẩu phải có ít nhất 6 ký tự';
                              }
                              return null;
                            },
                          ),
                          const SizedBox(height: 16),

                          // Confirm password field
                          TextFormField(
                            controller: _confirmPasswordController,
                            obscureText: _obscureConfirmPassword,
                            textInputAction: TextInputAction.done,
                            enabled: !authState.isLoading,
                            onFieldSubmitted: (_) => _handleRegister(),
                            style: const TextStyle(color: Colors.white),
                            decoration: InputDecoration(
                              filled: true,
                              fillColor: const Color(0xFF16213E),
                              contentPadding: const EdgeInsets.symmetric(
                                horizontal: 16,
                                vertical: 14,
                              ),
                              labelText: 'Xác nhận mật khẩu',
                              labelStyle: const TextStyle(
                                color: Color(0xFF8E8EA0),
                              ),
                              hintText: '••••••••',
                              hintStyle: TextStyle(
                                color: const Color(0xFF8E8EA0).withOpacity(0.5),
                              ),
                              prefixIcon: const Icon(
                                Icons.lock_outlined,
                                color: Color(0xFF8E8EA0),
                              ),
                              border: const OutlineInputBorder(
                                borderRadius: BorderRadius.all(
                                  Radius.circular(12),
                                ),
                              ),
                              enabledBorder: OutlineInputBorder(
                                borderRadius: BorderRadius.circular(12),
                                borderSide: const BorderSide(
                                  color: Color(0xFF2A2A4A),
                                ),
                              ),
                              focusedBorder: OutlineInputBorder(
                                borderRadius: BorderRadius.circular(12),
                                borderSide: const BorderSide(
                                  color: Color(0xFF7C4DFF),
                                  width: 1.5,
                                ),
                              ),
                              suffixIcon: IconButton(
                                icon: Icon(
                                  _obscureConfirmPassword
                                      ? Icons.visibility_outlined
                                      : Icons.visibility_off_outlined,
                                  color: const Color(0xFF8E8EA0),
                                ),
                                onPressed: () {
                                  setState(
                                    () => _obscureConfirmPassword =
                                        !_obscureConfirmPassword,
                                  );
                                },
                              ),
                            ),
                            onChanged: (value) =>
                                _formKey.currentState?.validate(),
                            validator: (value) {
                              if (value == null || value.trim().isEmpty) {
                                return 'Vui lòng xác nhận mật khẩu';
                              }
                              if (value.trim() !=
                                  _passwordController.text.trim()) {
                                return 'Mật khẩu xác nhận không khớp';
                              }
                              return null;
                            },
                          ),

                          const SizedBox(height: 24),
                          // Nút đăng ký
                          Material(
                            color: Colors.transparent,
                            child: Ink(
                              decoration: BoxDecoration(
                                gradient: LinearGradient(
                                  colors: authState.isLoading
                                      ? [
                                          Colors.grey.shade800,
                                          Colors.grey.shade800,
                                        ]
                                      : const [
                                          Color(0xFF7C4DFF),
                                          Color(0xFF00E5FF),
                                        ],
                                ),
                                borderRadius: BorderRadius.circular(12),
                                boxShadow: authState.isLoading
                                    ? []
                                    : [
                                        BoxShadow(
                                          color: const Color(
                                            0xFF7C4DFF,
                                          ).withOpacity(0.4),
                                          blurRadius: 12,
                                          offset: const Offset(0, 4),
                                        ),
                                      ],
                              ),
                              child: InkWell(
                                onTap: authState.isLoading
                                    ? null
                                    : _handleRegister,
                                borderRadius: BorderRadius.circular(12),
                                child: const SizedBox(
                                  height: 52,
                                  child: Center(
                                    child: Text(
                                      "Đăng ký",
                                      style: TextStyle(
                                        color: Colors.white,
                                        fontWeight: FontWeight.w600,
                                        fontSize: 16,
                                      ),
                                    ),
                                  ),
                                ),
                              ),
                            ),
                          ),

                          const SizedBox(height: 12),
                          Row(
                            children: [
                              Expanded(
                                child: Divider(
                                  thickness: 1,
                                  color: const Color(
                                    0xFF2A2A4A,
                                  ).withOpacity(0.5),
                                ),
                              ),
                              const Padding(
                                padding: EdgeInsets.symmetric(horizontal: 12),
                                child: Text(
                                  "Hoặc đăng ký với",
                                  style: TextStyle(color: Color(0xFF8E8EA0)),
                                ),
                              ),
                              Expanded(
                                child: Divider(
                                  thickness: 1,
                                  color: const Color(
                                    0xFF2A2A4A,
                                  ).withOpacity(0.5),
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 24),
                          // Social buttons
                          Row(
                            children: [
                              Expanded(
                                child: CustomButton(
                                  text: "Google",
                                  icon: const FaIcon(
                                    FontAwesomeIcons.google,
                                    size: 18,
                                    color: Colors.white,
                                  ),
                                ),
                              ),
                              const SizedBox(width: 12),
                              Expanded(
                                child: CustomButton(
                                  text: "Facebook",
                                  icon: const FaIcon(
                                    FontAwesomeIcons.facebookF,
                                    size: 18,
                                    color: Color(0xFF00E5FF),
                                  ),
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 12),

                          // Link đăng nhập
                          Row(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              const Text(
                                'Đã có tài khoản?',
                                style: TextStyle(color: Color(0xFF8E8EA0)),
                              ),
                              TextButton(
                                onPressed: () {
                                  Navigator.of(
                                    context,
                                  ).pushReplacementNamed('/login');
                                },
                                child: const Text(
                                  'Đăng nhập',
                                  style: TextStyle(color: Color(0xFF7C4DFF)),
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 12),
                        ],
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
