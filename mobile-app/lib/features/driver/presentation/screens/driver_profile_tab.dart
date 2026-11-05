import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/theme/app_colors.dart';
import 'package:swiftroute_app/core/theme/app_theme.dart';
import 'package:swiftroute_app/features/driver/application/driver_provider.dart';
import 'package:swiftroute_app/features/driver/data/models/driver_model.dart';
import 'package:swiftroute_app/features/order/data/models/order_model.dart';
import 'package:swiftroute_app/presentation/providers/auth_provider.dart';

/// Driver Profile & Shift Summary Screen (D09)
class DriverProfileTab extends ConsumerWidget {
  const DriverProfileTab({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final driverState = ref.watch(driverProvider);
    final ordersState = ref.watch(driverOrdersProvider);
    final user = ref.watch(currentUserProvider);

    final orders = ordersState.valueOrNull ?? [];
    final total = orders.length;
    final delivered = orders
        .where((o) => o.status == OrderStatus.delivered)
        .length;
    final failed = orders.where((o) => o.status == OrderStatus.failed).length;
    final rate = total > 0 ? (delivered / total * 100).toInt() : 0;

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: const Text(
          'Hồ sơ & Ca làm việc',
          style: TextStyle(
            fontSize: 18,
            fontWeight: FontWeight.bold,
            color: AppColors.textPrimary,
          ),
        ),
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          // 1. Profile Header
          Container(
            padding: const EdgeInsets.all(20),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: AppColors.divider),
            ),
            child: Row(
              children: [
                CircleAvatar(
                  radius: 32,
                  backgroundColor: AppColors.primary,
                  child: Text(
                    (user?.name.isNotEmpty == true ? user!.name[0] : 'T')
                        .toUpperCase(),
                    style: const TextStyle(
                      fontSize: 26,
                      fontWeight: FontWeight.bold,
                      color: Colors.white,
                    ),
                  ),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        user?.name ?? 'Tài xế SwiftRoute',
                        style: const TextStyle(
                          fontSize: 18,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        user?.email ?? 'driver@swiftroute.vn',
                        style: const TextStyle(
                          fontSize: 12,
                          color: AppColors.textSecondary,
                        ),
                      ),
                      const SizedBox(height: 6),
                      Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 8,
                          vertical: 2,
                        ),
                        decoration: BoxDecoration(
                          color: AppColors.success.withValues(alpha: 0.1),
                          borderRadius: BorderRadius.circular(4),
                        ),
                        child: const Text(
                          'Tài xế chính thức (Đã xác minh)',
                          style: TextStyle(
                            fontSize: 11,
                            fontWeight: FontWeight.bold,
                            color: AppColors.success,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),

          driverState.maybeWhen(
            data: (driver) => driver == null
                ? const SizedBox.shrink()
                : _OnboardingPanel(driver: driver),
            orElse: () => const SizedBox.shrink(),
          ),
          const SizedBox(height: 16),

          // 2. Vehicle & Work Details
          driverState.maybeWhen(
            data: (driver) => Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: AppColors.divider),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'Phương tiện và ca làm',
                    style: TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w800,
                      color: AppColors.textPrimary,
                    ),
                  ),
                  const Divider(height: 20, color: AppColors.divider),
                  _DetailRow(
                    label: 'Biển số xe',
                    value: driver?.licensePlate.isNotEmpty == true
                        ? driver!.licensePlate
                        : 'Chưa bổ sung',
                    icon: Icons.pin_rounded,
                  ),
                  _DetailRow(
                    label: 'Loại phương tiện',
                    value: driver?.vehicleTypeLabel ?? 'Chưa bổ sung',
                    icon: Icons.two_wheeler_rounded,
                  ),
                  _DetailRow(
                    label: 'Số điện thoại',
                    value: driver?.phone.isNotEmpty == true
                        ? driver!.phone
                        : 'Chưa bổ sung',
                    icon: Icons.phone_rounded,
                  ),
                  _DetailRow(
                    label: 'Hình thức tài xế',
                    value: driver?.driverTypeLabel ?? 'Chưa bổ sung',
                    icon: Icons.work_outline_rounded,
                  ),
                  _DetailRow(
                    label: 'Khung giờ ca',
                    value: driver?.shiftDisplay ?? 'Chưa xếp ca',
                    icon: Icons.access_time_rounded,
                  ),
                ],
              ),
            ),
            orElse: () => const SizedBox.shrink(),
          ),
          const SizedBox(height: 16),

          // 3. Shift Performance Report (D09)
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: AppColors.divider),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'Hiệu suất ca hôm nay',
                  style: TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w800,
                    color: AppColors.textPrimary,
                  ),
                ),
                const Divider(height: 20, color: AppColors.divider),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    const Text(
                      'Tỷ lệ giao hàng thành công',
                      style: TextStyle(fontWeight: FontWeight.w600),
                    ),
                    Text(
                      '$rate%',
                      style: const TextStyle(
                        fontWeight: FontWeight.bold,
                        color: AppColors.success,
                        fontSize: 16,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                ClipRRect(
                  borderRadius: BorderRadius.circular(4),
                  child: LinearProgressIndicator(
                    value: total > 0 ? (delivered / total) : 0.0,
                    minHeight: 8,
                    backgroundColor: AppColors.surfaceVariant,
                    valueColor: const AlwaysStoppedAnimation<Color>(
                      AppColors.success,
                    ),
                  ),
                ),
                const SizedBox(height: 16),
                Row(
                  children: [
                    _StatBox(
                      label: 'Tổng số điểm',
                      value: '$total',
                      color: AppColors.primary,
                    ),
                    const SizedBox(width: 8),
                    _StatBox(
                      label: 'Đã hoàn tất',
                      value: '$delivered',
                      color: AppColors.success,
                    ),
                    const SizedBox(width: 8),
                    _StatBox(
                      label: 'Thất bại',
                      value: '$failed',
                      color: AppColors.error,
                    ),
                  ],
                ),
                const SizedBox(height: 12),
              ],
            ),
          ),
          const SizedBox(height: 24),

          // 4. Logout Button
          SizedBox(
            width: double.infinity,
            height: 48,
            child: OutlinedButton.icon(
              onPressed: () => _confirmLogout(context, ref),
              icon: const Icon(Icons.logout_rounded, color: AppColors.error),
              label: const Text(
                'Đăng xuất tài khoản',
                style: TextStyle(
                  color: AppColors.error,
                  fontWeight: FontWeight.bold,
                ),
              ),
              style: OutlinedButton.styleFrom(
                side: const BorderSide(color: AppColors.error),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
              ),
            ),
          ),
          const SizedBox(height: 16),
          const Center(
            child: Text(
              'SwiftRoute Driver v1.0.0 (TSP Engine & Multi-Stop)',
              style: TextStyle(fontSize: 11, color: AppColors.textHint),
            ),
          ),
        ],
      ),
    );
  }

  void _confirmLogout(BuildContext context, WidgetRef ref) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Xác nhận đăng xuất'),
        content: const Text(
          'Bạn có chắc chắn muốn kết thúc ca làm và đăng xuất khỏi ứng dụng không?',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(),
            child: const Text(
              'Ở lại',
              style: TextStyle(color: AppColors.textSecondary),
            ),
          ),
          FilledButton(
            onPressed: () async {
              Navigator.of(ctx).pop();
              await ref.read(authProvider.notifier).logout();
              if (context.mounted) {
                Navigator.of(
                  context,
                ).pushNamedAndRemoveUntil('/login', (route) => false);
              }
            },
            style: FilledButton.styleFrom(backgroundColor: AppColors.error),
            child: const Text('Đăng xuất'),
          ),
        ],
      ),
    );
  }
}

class _OnboardingPanel extends ConsumerStatefulWidget {
  const _OnboardingPanel({required this.driver});
  final DriverModel driver;

  @override
  ConsumerState<_OnboardingPanel> createState() => _OnboardingPanelState();
}

class _OnboardingPanelState extends ConsumerState<_OnboardingPanel> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _name;
  late final TextEditingController _phone;
  late final TextEditingController _plate;
  late final TextEditingController _capacity;
  late String _vehicleType;
  var _saving = false;

  @override
  void initState() {
    super.initState();
    _name = TextEditingController(text: widget.driver.fullName);
    _phone = TextEditingController(text: widget.driver.phone);
    _plate = TextEditingController(text: widget.driver.licensePlate);
    _capacity = TextEditingController(
      text: widget.driver.capacity?.toString() ?? '20',
    );
    _vehicleType =
        const {'MOTORCYCLE', 'CAR', 'VAN'}.contains(widget.driver.vehicleType)
        ? widget.driver.vehicleType
        : 'MOTORCYCLE';
  }

  @override
  void dispose() {
    _name.dispose();
    _phone.dispose();
    _plate.dispose();
    _capacity.dispose();
    super.dispose();
  }

  Future<void> _saveAndSubmit() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() => _saving = true);
    final notifier = ref.read(driverProvider.notifier);
    final saved = await notifier.saveProfile(
      fullName: _name.text.trim(),
      phone: _phone.text.trim(),
      licensePlate: _plate.text.trim().toUpperCase(),
      vehicleType: _vehicleType,
      capacity: int.parse(_capacity.text),
    );
    final submitted = saved && await notifier.submitProfile();
    if (mounted) {
      setState(() => _saving = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            submitted
                ? 'Đã gửi hồ sơ cho doanh nghiệp xét duyệt.'
                : 'Chưa thể gửi hồ sơ. Kiểm tra thông tin và thử lại.',
          ),
          backgroundColor: submitted ? AppColors.success : AppColors.error,
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final status = widget.driver.status;
    if (status == 'ACTIVE') {
      return _statusBox(
        'Hồ sơ đã được duyệt',
        'Bạn có thể bật trạng thái sẵn sàng để nhận đơn.',
        AppColors.success,
      );
    }
    if (status == 'PENDING_APPROVAL') {
      return _statusBox(
        'Đang chờ doanh nghiệp duyệt',
        'Bạn sẽ nhận được trạng thái mới sau khi quản trị xử lý hồ sơ.',
        AppColors.primary,
      );
    }
    if (status == 'SUSPENDED') {
      return _statusBox(
        'Tài khoản đang tạm ngưng',
        'Liên hệ quản trị doanh nghiệp để được hỗ trợ.',
        AppColors.error,
      );
    }
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppColors.divider),
      ),
      child: Form(
        key: _formKey,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Hoàn thiện hồ sơ',
              style: TextStyle(fontSize: 17, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 6),
            const Text(
              'Điền đủ thông tin rồi gửi doanh nghiệp xét duyệt trước khi nhận đơn.',
              style: TextStyle(fontSize: 13, color: AppColors.textSecondary),
            ),
            if (widget.driver.reviewNote?.isNotEmpty == true) ...[
              const SizedBox(height: 12),
              Text(
                'Cần bổ sung: ${widget.driver.reviewNote}',
                style: const TextStyle(
                  fontSize: 13,
                  color: AppColors.error,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
            const SizedBox(height: 16),
            TextFormField(
              controller: _name,
              decoration: const InputDecoration(labelText: 'Họ và tên'),
              validator: (value) => value == null || value.trim().length < 2
                  ? 'Nhập họ và tên'
                  : null,
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _phone,
              keyboardType: TextInputType.phone,
              decoration: const InputDecoration(labelText: 'Số điện thoại'),
              validator: (value) =>
                  RegExp(r'^\+?[0-9]{9,15}$').hasMatch(value?.trim() ?? '')
                  ? null
                  : 'Số điện thoại gồm 9–15 chữ số',
            ),
            const SizedBox(height: 12),
            DropdownButtonFormField<String>(
              initialValue: _vehicleType,
              decoration: const InputDecoration(labelText: 'Loại phương tiện'),
              items: const [
                DropdownMenuItem(value: 'MOTORCYCLE', child: Text('Xe máy')),
                DropdownMenuItem(value: 'CAR', child: Text('Ô tô')),
                DropdownMenuItem(value: 'VAN', child: Text('Xe van')),
              ],
              onChanged: (value) =>
                  setState(() => _vehicleType = value ?? 'MOTORCYCLE'),
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _plate,
              textCapitalization: TextCapitalization.characters,
              decoration: const InputDecoration(labelText: 'Biển số xe'),
              validator: (value) => value == null || value.trim().isEmpty
                  ? 'Bổ sung biển số trước khi gửi duyệt'
                  : null,
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _capacity,
              keyboardType: TextInputType.number,
              decoration: const InputDecoration(labelText: 'Sức chứa (đơn)'),
              validator: (value) => (int.tryParse(value ?? '') ?? 0) > 0
                  ? null
                  : 'Sức chứa phải lớn hơn 0',
            ),
            const SizedBox(height: 16),
            SizedBox(
              width: double.infinity,
              child: FilledButton(
                onPressed: _saving ? null : _saveAndSubmit,
                child: Text(_saving ? 'Đang gửi…' : 'Lưu và gửi duyệt'),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _statusBox(String title, String detail, Color color) => Container(
    width: double.infinity,
    padding: const EdgeInsets.all(16),
    decoration: BoxDecoration(
      color: color.withValues(alpha: 0.08),
      borderRadius: BorderRadius.circular(16),
      border: Border.all(color: color.withValues(alpha: 0.25)),
    ),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          title,
          style: TextStyle(fontWeight: FontWeight.bold, color: color),
        ),
        const SizedBox(height: 4),
        Text(
          detail,
          style: const TextStyle(fontSize: 13, color: AppColors.textSecondary),
        ),
      ],
    ),
  );
}

class _DetailRow extends StatelessWidget {
  const _DetailRow({
    required this.label,
    required this.value,
    required this.icon,
  });
  final String label;
  final String value;
  final IconData icon;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Row(
        children: [
          Icon(icon, size: 16, color: AppColors.textSecondary),
          const SizedBox(width: 10),
          Text(
            label,
            style: const TextStyle(
              fontSize: 13,
              color: AppColors.textSecondary,
            ),
          ),
          const Spacer(),
          Text(
            value,
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

class _StatBox extends StatelessWidget {
  const _StatBox({
    required this.label,
    required this.value,
    required this.color,
  });
  final String label;
  final String value;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: Container(
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: AppColors.surface,
          borderRadius: BorderRadius.circular(8),
        ),
        child: Column(
          children: [
            Text(
              value,
              style: AppTheme.numStyle(
                fontSize: 18,
                fontWeight: FontWeight.bold,
                color: color,
              ),
            ),
            const SizedBox(height: 2),
            Text(
              label,
              style: const TextStyle(
                fontSize: 11,
                color: AppColors.textSecondary,
              ),
              textAlign: TextAlign.center,
            ),
          ],
        ),
      ),
    );
  }
}
