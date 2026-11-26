import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/theme/app_colors.dart';
import '../providers/auth_provider.dart';

/// Formats raw numeric input into `XXXX - XXXX - XXXX` (up to 12 digits) while typing or pasting.
class _TwelveDigitActivationFormatter extends TextInputFormatter {
  const _TwelveDigitActivationFormatter();

  static String formatDigits(String raw) {
    final digitsOnly = raw.replaceAll(RegExp(r'\D'), '');
    final capped = digitsOnly.length > 12
        ? digitsOnly.substring(0, 12)
        : digitsOnly;
    final buffer = StringBuffer();
    for (var i = 0; i < capped.length; i++) {
      if (i > 0 && i % 4 == 0) {
        buffer.write(' - ');
      }
      buffer.write(capped[i]);
    }
    return buffer.toString();
  }

  @override
  TextEditingValue formatEditUpdate(
    TextEditingValue oldValue,
    TextEditingValue newValue,
  ) {
    final formatted = formatDigits(newValue.text);
    return TextEditingValue(
      text: formatted,
      selection: TextSelection.collapsed(offset: formatted.length),
    );
  }
}

class DriverActivationScreen extends ConsumerStatefulWidget {
  const DriverActivationScreen({super.key});

  @override
  ConsumerState<DriverActivationScreen> createState() =>
      _DriverActivationScreenState();
}

class _DriverActivationScreenState
    extends ConsumerState<DriverActivationScreen> {
  final _formKey = GlobalKey<FormState>();
  final _codeController = TextEditingController();
  bool _isSubmitting = false;
  String? _error;

  @override
  void dispose() {
    _codeController.dispose();
    super.dispose();
  }

  String _rawDigits(String value) => value.replaceAll(RegExp(r'\D'), '');

  Future<void> _pasteFromClipboard() async {
    if (_isSubmitting) return;
    final clip = await Clipboard.getData(Clipboard.kTextPlain);
    final raw = clip?.text;
    if (raw == null || raw.trim().isEmpty) return;
    final formatted = _TwelveDigitActivationFormatter.formatDigits(raw);
    if (formatted.isEmpty) {
      setState(() {
        _error = 'Nội dung trong bộ nhớ tạm không chứa chữ số hợp lệ.';
      });
      return;
    }
    setState(() {
      _error = null;
      _codeController.value = TextEditingValue(
        text: formatted,
        selection: TextSelection.collapsed(offset: formatted.length),
      );
    });
  }

  Future<void> _submitActivation() async {
    if (_isSubmitting) return;
    if (!_formKey.currentState!.validate()) return;

    final digits = _rawDigits(_codeController.text);
    setState(() {
      _isSubmitting = true;
      _error = null;
    });

    try {
      final success = await ref
          .read(authProvider.notifier)
          .activateWithCode(digits);
      if (!mounted) return;
      if (success) {
        ref.read(authProvider.notifier).clearError();
        Navigator.of(context).popUntil((route) => route.isFirst);
      } else {
        final message = ref.read(authProvider).errorMessage;
        ref.read(authProvider.notifier).clearError();
        setState(() {
          _error =
              message ??
              'Không thể kích hoạt ứng dụng. Vui lòng kiểm tra mã kích hoạt và thử lại.';
        });
      }
    } finally {
      if (mounted) {
        setState(() => _isSubmitting = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Kích hoạt bằng mã'),
        leading: IconButton(
          tooltip: 'Quay lại đăng nhập',
          icon: const Icon(Icons.arrow_back_rounded),
          onPressed: _isSubmitting ? null : () => Navigator.of(context).pop(),
        ),
      ),
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 460),
              child: Form(
                key: _formKey,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Align(
                      alignment: Alignment.center,
                      child: Container(
                        width: 64,
                        height: 64,
                        decoration: BoxDecoration(
                          color: AppColors.primarySoft,
                          borderRadius: BorderRadius.circular(18),
                        ),
                        child: const Icon(
                          Icons.route_rounded,
                          size: 34,
                          color: AppColors.primary,
                        ),
                      ),
                    ),
                    const SizedBox(height: 20),
                    const Text(
                      'SwiftRoute Driver',
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        fontSize: 24,
                        fontWeight: FontWeight.w800,
                        letterSpacing: -0.3,
                        color: AppColors.textPrimary,
                      ),
                    ),
                    const SizedBox(height: 8),
                    const Text(
                      'Nhập hoặc dán mã kích hoạt 12 chữ số do quản trị viên doanh nghiệp cấp để đăng nhập ngay không cần mật khẩu.',
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        fontSize: 14,
                        height: 1.5,
                        color: AppColors.textSecondary,
                      ),
                    ),
                    const SizedBox(height: 28),
                    TextFormField(
                      controller: _codeController,
                      enabled: !_isSubmitting,
                      keyboardType: TextInputType.number,
                      textInputAction: TextInputAction.done,
                      textAlign: TextAlign.center,
                      inputFormatters: const [
                        _TwelveDigitActivationFormatter(),
                      ],
                      onChanged: (_) {
                        if (_error != null) {
                          setState(() => _error = null);
                        }
                      },
                      onFieldSubmitted: (_) => _submitActivation(),
                      style: const TextStyle(
                        fontSize: 20,
                        fontWeight: FontWeight.w700,
                        letterSpacing: 1.2,
                        fontFeatures: [FontFeature.tabularFigures()],
                        color: AppColors.textPrimary,
                      ),
                      decoration: InputDecoration(
                        labelText: 'Mã kích hoạt 12 chữ số',
                        hintText: '0000 - 0000 - 0000',
                        hintStyle: const TextStyle(
                          fontSize: 18,
                          fontWeight: FontWeight.w500,
                          letterSpacing: 1.0,
                          color: AppColors.textSecondary,
                        ),
                        prefixIcon: const Icon(Icons.key_rounded),
                        suffixIcon: IconButton(
                          tooltip: 'Dán từ bộ nhớ tạm',
                          onPressed: _isSubmitting ? null : _pasteFromClipboard,
                          icon: const Icon(Icons.content_paste_rounded),
                        ),
                      ),
                      validator: (value) {
                        final digits = _rawDigits(value ?? '');
                        if (digits.isEmpty) {
                          return 'Vui lòng nhập mã kích hoạt được cấp';
                        }
                        if (digits.length != 12) {
                          return 'Mã kích hoạt phải gồm đúng 12 chữ số';
                        }
                        return null;
                      },
                    ),
                    const SizedBox(height: 10),
                    const Text(
                      'Mã kích hoạt chỉ sử dụng một lần và mặc định hết hạn sau 24 giờ.',
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        fontSize: 12,
                        color: AppColors.textSecondary,
                      ),
                    ),
                    if (_error != null) ...[
                      const SizedBox(height: 18),
                      Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 14,
                          vertical: 12,
                        ),
                        decoration: BoxDecoration(
                          color: AppColors.errorSoft,
                          borderRadius: BorderRadius.circular(10),
                          border: Border.all(color: AppColors.errorBorder),
                        ),
                        child: Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Icon(
                              Icons.error_outline_rounded,
                              size: 20,
                              color: AppColors.error,
                            ),
                            const SizedBox(width: 10),
                            Expanded(
                              child: Semantics(
                                liveRegion: true,
                                child: Text(
                                  _error!,
                                  style: const TextStyle(
                                    color: AppColors.error,
                                    fontSize: 13,
                                    height: 1.4,
                                  ),
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                    const SizedBox(height: 24),
                    SizedBox(
                      height: 56,
                      child: FilledButton.icon(
                        onPressed: _isSubmitting ? null : _submitActivation,
                        icon: _isSubmitting
                            ? const SizedBox(
                                width: 18,
                                height: 18,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                  color: Colors.white,
                                ),
                              )
                            : const Icon(Icons.verified_user_outlined),
                        label: Text(
                          _isSubmitting
                              ? 'Đang kích hoạt…'
                              : 'Kích hoạt ứng dụng',
                        ),
                        style: FilledButton.styleFrom(
                          backgroundColor: AppColors.primary,
                          foregroundColor: Colors.white,
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(12),
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(height: 12),
                    SizedBox(
                      height: 48,
                      child: TextButton.icon(
                        onPressed: _isSubmitting
                            ? null
                            : () => Navigator.of(context).pop(),
                        icon: const Icon(Icons.arrow_back_rounded, size: 18),
                        label: const Text('Quay lại đăng nhập email'),
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
