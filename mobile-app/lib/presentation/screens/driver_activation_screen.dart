import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/network/dio.dart';
import '../../core/theme/app_colors.dart';

class DriverActivationScreen extends ConsumerStatefulWidget {
  const DriverActivationScreen({super.key});

  @override
  ConsumerState<DriverActivationScreen> createState() => _DriverActivationScreenState();
}

class _DriverActivationScreenState extends ConsumerState<DriverActivationScreen> {
  final _formKey = GlobalKey<FormState>();
  final _tokenController = TextEditingController();
  final _nameController = TextEditingController();
  final _passwordController = TextEditingController();
  bool _isSubmitting = false;
  bool _obscurePassword = true;
  String? _error;

  @override
  void dispose() {
    _tokenController.dispose();
    _nameController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  Future<void> _activate() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() {
      _isSubmitting = true;
      _error = null;
    });

    try {
      await ref.read(dioProvider).post(
        '/auth/invitations/accept',
        data: {
          'token': _tokenController.text.trim(),
          'fullName': _nameController.text.trim(),
          'password': _passwordController.text,
        },
      );
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Kích hoạt thành công. Hãy đăng nhập bằng email được mời.'),
          backgroundColor: AppColors.success,
        ),
      );
      Navigator.of(context).pop();
    } on DioException catch (error) {
      final body = error.response?.data;
      final errorBody = body is Map<String, dynamic> ? body['error'] : null;
      final message = errorBody is Map<String, dynamic>
          ? errorBody['message']?.toString()
          : body is Map<String, dynamic>
              ? body['message']?.toString()
              : null;
      if (mounted) {
        setState(
          () => _error =
              message ?? 'Không thể kích hoạt. Kiểm tra token và thử lại.',
        );
      }
    } catch (_) {
      if (mounted) {
        setState(() => _error = 'Không thể kết nối máy chủ. Hãy thử lại.');
      }
    } finally {
      if (mounted) setState(() => _isSubmitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Kích hoạt tài khoản tài xế'),
        backgroundColor: AppColors.background,
        foregroundColor: AppColors.primary,
      ),
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 520),
              child: Form(
                key: _formKey,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    const Icon(Icons.badge_outlined, size: 48, color: AppColors.primary),
                    const SizedBox(height: 20),
                    const Text(
                      'Nhận quyền truy cập đội giao hàng',
                      textAlign: TextAlign.center,
                      style: TextStyle(fontSize: 24, fontWeight: FontWeight.w800, color: AppColors.textPrimary),
                    ),
                    const SizedBox(height: 8),
                    const Text(
                      'Dán token do quản trị viên gửi. Sau khi kích hoạt, dùng email trong lời mời và mật khẩu bên dưới để đăng nhập.',
                      textAlign: TextAlign.center,
                      style: TextStyle(fontSize: 14, height: 1.5, color: AppColors.textSecondary),
                    ),
                    const SizedBox(height: 28),
                    TextFormField(
                      controller: _tokenController,
                      minLines: 2,
                      maxLines: 3,
                      enabled: !_isSubmitting,
                      decoration: const InputDecoration(labelText: 'Invitation token', border: OutlineInputBorder()),
                      validator: (value) => value == null || value.trim().isEmpty ? 'Nhập token được cấp' : null,
                    ),
                    const SizedBox(height: 16),
                    TextFormField(
                      controller: _nameController,
                      enabled: !_isSubmitting,
                      textInputAction: TextInputAction.next,
                      decoration: const InputDecoration(labelText: 'Họ và tên', border: OutlineInputBorder()),
                      validator: (value) => value == null || value.trim().isEmpty ? 'Nhập họ và tên' : null,
                    ),
                    const SizedBox(height: 16),
                    TextFormField(
                      controller: _passwordController,
                      enabled: !_isSubmitting,
                      obscureText: _obscurePassword,
                      onFieldSubmitted: (_) => _activate(),
                      decoration: InputDecoration(
                        labelText: 'Mật khẩu mới',
                        border: const OutlineInputBorder(),
                        suffixIcon: IconButton(
                          onPressed: () => setState(() => _obscurePassword = !_obscurePassword),
                          icon: Icon(_obscurePassword ? Icons.visibility_outlined : Icons.visibility_off_outlined),
                        ),
                      ),
                      validator: (value) => value == null || value.length < 6 ? 'Mật khẩu cần ít nhất 6 ký tự' : null,
                    ),
                    if (_error != null) ...[
                      const SizedBox(height: 16),
                      Semantics(
                        liveRegion: true,
                        child: Text(_error!, style: const TextStyle(color: AppColors.error, height: 1.4)),
                      ),
                    ],
                    const SizedBox(height: 24),
                    SizedBox(
                      height: 50,
                      child: ElevatedButton.icon(
                        onPressed: _isSubmitting ? null : _activate,
                        icon: _isSubmitting
                            ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                            : const Icon(Icons.verified_user_outlined),
                        label: Text(_isSubmitting ? 'Đang kích hoạt…' : 'Kích hoạt tài khoản'),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: AppColors.primary,
                          foregroundColor: Colors.white,
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
