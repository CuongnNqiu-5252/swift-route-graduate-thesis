import 'package:flutter/material.dart';

class ForgotPasswordScreen extends StatelessWidget {
  const ForgotPasswordScreen({super.key});

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Quên mật khẩu')),
    body: SafeArea(
      child: Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 440),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const Icon(Icons.lock_reset, size: 48),
                const SizedBox(height: 24),
                Text(
                  'Cần lấy lại quyền truy cập?',
                  style: Theme.of(context).textTheme.headlineSmall,
                ),
                const SizedBox(height: 16),
                const Text(
                  'Liên hệ quản trị viên doanh nghiệp và cung cấp email đã được mời để được hướng dẫn hỗ trợ. Chức năng tự đặt lại mật khẩu qua email chưa khả dụng trong ứng dụng.',
                ),
                const SizedBox(height: 24),
                SizedBox(
                  height: 56,
                  child: FilledButton(
                    onPressed: () => Navigator.of(context).pop(),
                    child: const Text('Trở về đăng nhập'),
                  ),
                ),
                const SizedBox(height: 12),
                TextButton(
                  onPressed: () => Navigator.of(
                    context,
                  ).pushReplacementNamed('/driver/activate'),
                  child: const Text('Tôi có lời mời chưa kích hoạt'),
                ),
              ],
            ),
          ),
        ),
      ),
    ),
  );
}
