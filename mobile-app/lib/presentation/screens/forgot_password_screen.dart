import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/network/dio.dart';

class ForgotPasswordScreen extends ConsumerStatefulWidget {
  const ForgotPasswordScreen({super.key});

  @override
  ConsumerState<ForgotPasswordScreen> createState() =>
      _ForgotPasswordScreenState();
}

class _ForgotPasswordScreenState extends ConsumerState<ForgotPasswordScreen> {
  final _email = TextEditingController();
  final _formKey = GlobalKey<FormState>();
  bool _submitting = false;
  bool _sent = false;
  String? _submittedEmail;
  String? _error;

  @override
  void dispose() {
    _email.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_submitting) return;
    if (!_formKey.currentState!.validate()) return;
    final submittedEmail = _email.text.trim().toLowerCase();
    setState(() {
      _submitting = true;
      _error = null;
    });
    try {
      await ref
          .read(dioProvider)
          .post(
            '/auth/password/forgot',
            data: {'email': submittedEmail},
          );
      if (mounted) {
        setState(() {
          _submittedEmail = submittedEmail;
          _sent = true;
        });
      }
    } on DioException catch (error) {
      if (!mounted) return;
      setState(() {
        _error = error.response?.statusCode == 429
            ? 'Bạn vừa yêu cầu email. Vui lòng chờ một phút rồi thử lại.'
            : 'Chưa thể gửi yêu cầu. Kiểm tra kết nối và thử lại.';
      });
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Quên mật khẩu')),
    body: SafeArea(
      child: Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 440),
            child: _sent ? _success(context) : _form(context),
          ),
        ),
      ),
    ),
  );

  Widget _form(BuildContext context) => Form(
    key: _formKey,
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const Icon(Icons.lock_reset_rounded, size: 52),
        const SizedBox(height: 24),
        Text(
          'Lấy lại quyền truy cập',
          style: Theme.of(context).textTheme.headlineSmall,
          textAlign: TextAlign.center,
        ),
        const SizedBox(height: 10),
        Text(
          'Nhập email tài khoản tài xế. Nếu email tồn tại, SwiftRoute sẽ gửi liên kết đặt lại mật khẩu có hiệu lực trong 15 phút.',
          style: Theme.of(context).textTheme.bodyMedium,
          textAlign: TextAlign.center,
        ),
        const SizedBox(height: 28),
        TextFormField(
          controller: _email,
          enabled: !_submitting,
          keyboardType: TextInputType.emailAddress,
          autofillHints: const [AutofillHints.email],
          textInputAction: TextInputAction.done,
          onFieldSubmitted: (_) {
            if (!_submitting) _submit();
          },
          decoration: const InputDecoration(
            labelText: 'Email tài khoản',
            hintText: 'taixe@doanhnghiep.vn',
            prefixIcon: Icon(Icons.mail_outline_rounded),
          ),
          validator: (value) {
            final email = value?.trim() ?? '';
            if (email.isEmpty || !email.contains('@') || !email.contains('.')) {
              return 'Nhập một địa chỉ email hợp lệ';
            }
            return null;
          },
        ),
        if (_error != null) ...[
          const SizedBox(height: 12),
          Semantics(
            liveRegion: true,
            child: Text(
              _error!,
              style: TextStyle(color: Theme.of(context).colorScheme.error),
            ),
          ),
        ],
        const SizedBox(height: 20),
        SizedBox(
          height: 56,
          child: FilledButton.icon(
            onPressed: _submitting ? null : _submit,
            icon: _submitting
                ? const SizedBox.square(
                    dimension: 20,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.send_rounded),
            label: Text(_submitting ? 'Đang gửi…' : 'Gửi liên kết đặt lại'),
          ),
        ),
        const SizedBox(height: 12),
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: const Text('Trở về đăng nhập'),
        ),
      ],
    ),
  );

  Widget _success(BuildContext context) => Semantics(
    container: true,
    liveRegion: true,
    label: 'Yêu cầu đặt lại mật khẩu đã được tiếp nhận',
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
      Icon(
        Icons.mark_email_read_rounded,
        size: 56,
        color: Theme.of(context).colorScheme.primary,
      ),
      const SizedBox(height: 24),
      Text(
        'Kiểm tra hộp thư',
        style: Theme.of(context).textTheme.headlineSmall,
        textAlign: TextAlign.center,
      ),
      const SizedBox(height: 12),
      Text(
        'Nếu $_submittedEmail thuộc một tài khoản SwiftRoute, email đặt lại mật khẩu đã được gửi. Kiểm tra cả thư rác.',
        textAlign: TextAlign.center,
      ),
      const SizedBox(height: 28),
      SizedBox(
        height: 56,
        child: FilledButton(
          onPressed: () => Navigator.of(context).pop(),
          child: const Text('Trở về đăng nhập'),
        ),
      ),
      const SizedBox(height: 12),
      TextButton(
        onPressed: () => setState(() {
          _sent = false;
          _submittedEmail = null;
        }),
        child: const Text('Gửi lại bằng email khác'),
      ),
      ],
    ),
  );
}
