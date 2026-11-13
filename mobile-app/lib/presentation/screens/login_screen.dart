import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/theme/app_colors.dart';
import '../providers/auth_provider.dart';

class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});
  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  final _form = GlobalKey<FormState>();
  final _email = TextEditingController();
  final _password = TextEditingController();
  bool _obscure = true;
  String? _roleError;

  @override
  void dispose() {
    _email.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _login() async {
    if (!_form.currentState!.validate()) return;
    setState(() => _roleError = null);
    final success = await ref
        .read(authProvider.notifier)
        .login(_email.text, _password.text);
    if (!mounted || !success) return;
    if (Navigator.of(context).canPop()) {
      Navigator.of(context).popUntil((route) => route.isFirst);
    }
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(authProvider);
    ref.listen(authProvider, (previous, next) {
      if (next.errorMessage != null &&
          next.errorMessage != previous?.errorMessage) {
        setState(() => _roleError = next.errorMessage);
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(next.errorMessage!)));
        ref.read(authProvider.notifier).clearError();
      }
    });
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 440),
              child: AutofillGroup(
                child: Form(
                  key: _form,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      const Align(
                        alignment: Alignment.centerLeft,
                        child: Icon(
                          Icons.local_shipping_outlined,
                          size: 48,
                          color: AppColors.primary,
                        ),
                      ),
                      const SizedBox(height: 24),
                      Text(
                        'SwiftRoute Driver',
                        style: Theme.of(context).textTheme.headlineMedium,
                      ),
                      const SizedBox(height: 8),
                      const Text(
                        'Đăng nhập để bắt đầu ca và nhận tuyến giao hàng.',
                      ),
                      const SizedBox(height: 32),
                      TextFormField(
                        controller: _email,
                        enabled: !state.isLoading,
                        autofillHints: const [AutofillHints.username],
                        keyboardType: TextInputType.emailAddress,
                        textInputAction: TextInputAction.next,
                        decoration: const InputDecoration(
                          labelText: 'Email',
                          prefixIcon: Icon(Icons.mail_outline),
                        ),
                        validator: (v) =>
                            v == null ||
                                !RegExp(
                                  r'^[^\s@]+@[^\s@]+\.[^\s@]+$',
                                ).hasMatch(v.trim())
                            ? 'Nhập email hợp lệ'
                            : null,
                      ),
                      const SizedBox(height: 20),
                      TextFormField(
                        controller: _password,
                        enabled: !state.isLoading,
                        obscureText: _obscure,
                        autofillHints: const [AutofillHints.password],
                        onFieldSubmitted: (_) {
                          if (!state.isLoading) _login();
                        },
                        decoration: InputDecoration(
                          labelText: 'Mật khẩu',
                          prefixIcon: const Icon(Icons.lock_outline),
                          suffixIcon: IconButton(
                            tooltip: _obscure ? 'Hiện mật khẩu' : 'Ẩn mật khẩu',
                            onPressed: () =>
                                setState(() => _obscure = !_obscure),
                            icon: Icon(
                              _obscure
                                  ? Icons.visibility_outlined
                                  : Icons.visibility_off_outlined,
                            ),
                          ),
                        ),
                        validator: (v) =>
                            v == null || v.isEmpty ? 'Nhập mật khẩu' : null,
                      ),
                      if (_roleError != null) ...[
                        const SizedBox(height: 16),
                        Semantics(
                          liveRegion: true,
                          child: Text(
                            _roleError!,
                            style: const TextStyle(color: AppColors.error),
                          ),
                        ),
                      ],
                      const SizedBox(height: 24),
                      SizedBox(
                        height: 56,
                        child: FilledButton(
                          onPressed: state.isLoading ? null : _login,
                          child: state.isLoading
                              ? const SizedBox(
                                  width: 24,
                                  height: 24,
                                  child: CircularProgressIndicator(
                                    strokeWidth: 2,
                                  ),
                                )
                              : const Text('Đăng nhập'),
                        ),
                      ),
                      const SizedBox(height: 16),
                      TextButton(
                        onPressed: state.isLoading
                            ? null
                            : () => Navigator.pushNamed(
                                context,
                                '/forgot-password',
                              ),
                        child: const Text('Quên mật khẩu?'),
                      ),
                      const SizedBox(height: 24),
                      const Divider(),
                      const SizedBox(height: 16),
                      const Text('Bạn nhận được lời mời từ doanh nghiệp?'),
                      const SizedBox(height: 12),
                      SizedBox(
                        height: 56,
                        child: OutlinedButton.icon(
                          onPressed: state.isLoading
                              ? null
                              : () => Navigator.pushNamed(
                                  context,
                                  '/driver/activate',
                                ),
                          icon: const Icon(Icons.badge_outlined),
                          label: const Text('Kích hoạt tài khoản tài xế'),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
