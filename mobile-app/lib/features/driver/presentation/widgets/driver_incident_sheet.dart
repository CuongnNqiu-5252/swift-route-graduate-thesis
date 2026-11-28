import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';
import 'package:swiftroute_app/core/theme/app_colors.dart';
import 'package:swiftroute_app/features/driver/application/driver_provider.dart';
import 'package:swiftroute_app/features/order/data/datasources/order_remote_data_source.dart';
import 'package:swiftroute_app/features/order/data/models/order_model.dart';

class DriverIncidentSheet extends ConsumerStatefulWidget {
  const DriverIncidentSheet({super.key, required this.order});

  final OrderModel order;

  static Future<void> show(BuildContext context, OrderModel order) {
    return showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => DriverIncidentSheet(order: order),
    );
  }

  @override
  ConsumerState<DriverIncidentSheet> createState() =>
      _DriverIncidentSheetState();
}

class _FailureReasonOption {
  const _FailureReasonOption({
    required this.code,
    required this.label,
    required this.icon,
    required this.color,
    this.subtitle,
  });

  final String code;
  final String label;
  final String? subtitle;
  final IconData icon;
  final Color color;
}

class _DriverIncidentSheetState extends ConsumerState<DriverIncidentSheet> {
  String _selectedType = 'CUSTOMER_UNREACHABLE';
  final _notesController = TextEditingController();
  bool _isSubmitting = false;

  Uint8List? _evidenceImageBytes;
  String? _uploadedEvidenceUrl;
  bool _isUploadingImage = false;

  static const _failureOptions = [
    _FailureReasonOption(
      code: 'CUSTOMER_UNREACHABLE',
      label: 'Khách không nghe máy / Vắng mặt',
      icon: Icons.phone_missed_rounded,
      color: AppColors.warning,
    ),
    _FailureReasonOption(
      code: 'WRONG_ADDRESS',
      label: 'Sai địa chỉ / Điểm giao đóng cửa',
      icon: Icons.wrong_location_rounded,
      color: AppColors.textSecondary,
    ),
    _FailureReasonOption(
      code: 'REFUSED_BY_RECIPIENT',
      label: 'Khách từ chối nhận hàng',
      subtitle: 'Đơn tự động chuyển sang hoàn về kho',
      icon: Icons.cancel_outlined,
      color: AppColors.error,
    ),
    _FailureReasonOption(
      code: 'PACKAGE_DAMAGED',
      label: 'Hàng hóa bị hư hỏng',
      subtitle: 'Đơn tự động chuyển sang hoàn về kho',
      icon: Icons.broken_image_rounded,
      color: AppColors.error,
    ),
    _FailureReasonOption(
      code: 'OTHER',
      label: 'Lý do khác',
      subtitle: 'Bắt buộc nhập ghi chú chi tiết',
      icon: Icons.warning_amber_rounded,
      color: AppColors.textSecondary,
    ),
  ];

  @override
  void dispose() {
    _notesController.dispose();
    super.dispose();
  }

  bool get _isImmediateReturn =>
      _selectedType == 'REFUSED_BY_RECIPIENT' ||
      _selectedType == 'PACKAGE_DAMAGED';

  Future<void> _pickAndUploadImage(ImageSource source) async {
    try {
      final picker = ImagePicker();
      final picked = await picker.pickImage(source: source, imageQuality: 80);
      if (picked == null) return;

      final bytes = await picked.readAsBytes();
      setState(() {
        _evidenceImageBytes = bytes;
        _isUploadingImage = true;
      });

      final url = await ref
          .read(orderRemoteDataSourceProvider)
          .uploadEvidenceImage(
            bytes: bytes,
            fileName: picked.name,
            folder: 'incidents',
          );

      if (mounted) {
        setState(() {
          _uploadedEvidenceUrl = url;
          _isUploadingImage = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() => _isUploadingImage = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Tải ảnh thất bại: $e'),
            backgroundColor: AppColors.error,
          ),
        );
      }
    }
  }

  Future<void> _submitIncident() async {
    final notes = _notesController.text.trim();
    if (_selectedType == 'OTHER' && notes.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Vui lòng nhập chi tiết khi chọn "Lý do khác".'),
          backgroundColor: AppColors.error,
        ),
      );
      return;
    }

    setState(() => _isSubmitting = true);
    try {
      final evidenceUrls = _uploadedEvidenceUrl != null
          ? [_uploadedEvidenceUrl!]
          : <String>[];

      await ref
          .read(orderRemoteDataSourceProvider)
          .reportOperationalIncident(
            orderId: widget.order.id,
            incidentType: _selectedType,
            notes: notes.isNotEmpty ? notes : null,
            evidenceUrls: evidenceUrls,
          );

      await ref.read(driverOrdersProvider.notifier).refreshOrders();

      if (mounted) {
        Navigator.of(context).pop();
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              _isImmediateReturn
                  ? 'Đơn ${widget.order.displayCode} đã chuyển sang chờ hoàn về kho.'
                  : 'Đã ghi nhận giao chưa thành công đơn ${widget.order.displayCode}.',
            ),
            backgroundColor: AppColors.accent,
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Gửi báo cáo thất bại: $e'),
            backgroundColor: AppColors.error,
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _isSubmitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final bottomInset = MediaQuery.of(context).viewInsets.bottom;
    return Container(
      constraints: BoxConstraints(
        maxHeight: MediaQuery.of(context).size.height * 0.88,
      ),
      padding: EdgeInsets.only(
        bottom: bottomInset > 0 ? bottomInset + 12 : 16,
        top: 14,
        left: 20,
        right: 20,
      ),
      decoration: const BoxDecoration(
        color: AppColors.card,
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      child: SafeArea(
        top: false,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Center(
              child: Container(
                width: 40,
                height: 4,
                decoration: BoxDecoration(
                  color: AppColors.surfaceVariant,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
            const SizedBox(height: 14),
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    color: AppColors.error.withValues(alpha: 0.1),
                    shape: BoxShape.circle,
                  ),
                  child: const Icon(
                    Icons.report_problem_rounded,
                    color: AppColors.error,
                    size: 24,
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text(
                        'Giao không thành công',
                        style: TextStyle(
                          fontSize: 18,
                          fontWeight: FontWeight.bold,
                          color: AppColors.textPrimary,
                        ),
                      ),
                      Text(
                        '${widget.order.displayCode} · ${widget.order.deliveryAddress}',
                        style: const TextStyle(
                          fontSize: 12,
                          color: AppColors.textSecondary,
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 14),
            Flexible(
              child: SingleChildScrollView(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      'Chọn lý do không giao được:',
                      style: TextStyle(
                        fontWeight: FontWeight.w600,
                        fontSize: 14,
                      ),
                    ),
                    const SizedBox(height: 8),
                    for (final opt in _failureOptions)
                      _buildReasonTile(opt),
                    const SizedBox(height: 10),
                    Row(
                      children: [
                        Expanded(
                          child: OutlinedButton.icon(
                            onPressed: _isUploadingImage
                                ? null
                                : () => _pickAndUploadImage(ImageSource.camera),
                            icon: const Icon(
                              Icons.camera_alt_outlined,
                              size: 18,
                            ),
                            label: const Text(
                              'Chụp ảnh bằng chứng',
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: TextStyle(fontSize: 12),
                            ),
                            style: OutlinedButton.styleFrom(
                              padding: const EdgeInsets.symmetric(
                                vertical: 10,
                                horizontal: 8,
                              ),
                              shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(10),
                              ),
                            ),
                          ),
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: OutlinedButton.icon(
                            onPressed: _isUploadingImage
                                ? null
                                : () =>
                                      _pickAndUploadImage(ImageSource.gallery),
                            icon: const Icon(
                              Icons.photo_library_outlined,
                              size: 18,
                            ),
                            label: const Text(
                              'Chọn từ thư viện',
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: TextStyle(fontSize: 12),
                            ),
                            style: OutlinedButton.styleFrom(
                              padding: const EdgeInsets.symmetric(
                                vertical: 10,
                                horizontal: 8,
                              ),
                              shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(10),
                              ),
                            ),
                          ),
                        ),
                      ],
                    ),
                    if (_isUploadingImage) ...[
                      const SizedBox(height: 8),
                      const Center(
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            SizedBox(
                              width: 16,
                              height: 16,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            ),
                            SizedBox(width: 8),
                            Text(
                              'Đang tải ảnh lên...',
                              style: TextStyle(
                                fontSize: 12,
                                color: AppColors.textSecondary,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ] else if (_evidenceImageBytes != null) ...[
                      const SizedBox(height: 8),
                      Row(
                        children: [
                          ClipRRect(
                            borderRadius: BorderRadius.circular(8),
                            child: Image.memory(
                              _evidenceImageBytes!,
                              width: 56,
                              height: 56,
                              fit: BoxFit.cover,
                            ),
                          ),
                          const SizedBox(width: 10),
                          const Icon(
                            Icons.check_circle_rounded,
                            size: 16,
                            color: AppColors.success,
                          ),
                          const SizedBox(width: 6),
                          const Expanded(
                            child: Text(
                              'Đã đính kèm ảnh bằng chứng',
                              style: TextStyle(
                                fontSize: 12,
                                color: AppColors.success,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                          ),
                        ],
                      ),
                    ],
                    const SizedBox(height: 10),
                    TextField(
                      controller: _notesController,
                      maxLines: 2,
                      decoration: InputDecoration(
                        labelText: _selectedType == 'OTHER'
                            ? 'Ghi chú chi tiết (bắt buộc)'
                            : 'Ghi chú cho điều phối (tuỳ chọn)',
                        hintText: 'VD: Khách hẹn giao lại sau 18h...',
                        hintStyle: const TextStyle(
                          fontSize: 12,
                          color: AppColors.textSecondary,
                        ),
                        filled: true,
                        fillColor: AppColors.card,
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(12),
                          borderSide: const BorderSide(
                            color: AppColors.divider,
                          ),
                        ),
                        contentPadding: const EdgeInsets.symmetric(
                          horizontal: 14,
                          vertical: 10,
                        ),
                      ),
                    ),
                    const SizedBox(height: 8),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed: _isSubmitting
                        ? null
                        : () => Navigator.of(context).pop(),
                    style: OutlinedButton.styleFrom(
                      padding: const EdgeInsets.symmetric(vertical: 14),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(12),
                      ),
                    ),
                    child: const Text('Huỷ bỏ'),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: FilledButton(
                    onPressed: _isSubmitting ? null : _submitIncident,
                    style: FilledButton.styleFrom(
                      backgroundColor: AppColors.error,
                      padding: const EdgeInsets.symmetric(vertical: 14),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(12),
                      ),
                    ),
                    child: _isSubmitting
                        ? const SizedBox(
                            height: 20,
                            width: 20,
                            child: CircularProgressIndicator(
                              color: Colors.white,
                              strokeWidth: 2,
                            ),
                          )
                        : const Text(
                            'Xác nhận',
                            style: TextStyle(
                              color: Colors.white,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildReasonTile(_FailureReasonOption opt) {
    final isSelected = _selectedType == opt.code;
    return InkWell(
      onTap: () => setState(() => _selectedType = opt.code),
      borderRadius: BorderRadius.circular(12),
      child: Container(
        margin: const EdgeInsets.only(bottom: 6),
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
        decoration: BoxDecoration(
          color: isSelected
              ? AppColors.error.withValues(alpha: 0.06)
              : AppColors.card,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
            color: isSelected ? AppColors.error : AppColors.divider,
            width: isSelected ? 1.5 : 1,
          ),
        ),
        child: Row(
          children: [
            Icon(opt.icon, color: opt.color, size: 20),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    opt.label,
                    style: TextStyle(
                      fontSize: 13.5,
                      fontWeight: isSelected ? FontWeight.w700 : FontWeight.w500,
                      color: isSelected
                          ? AppColors.textPrimary
                          : AppColors.textSecondary,
                    ),
                  ),
                  if (opt.subtitle != null) ...[
                    const SizedBox(height: 2),
                    Text(
                      opt.subtitle!,
                      style: const TextStyle(
                        fontSize: 11.5,
                        color: AppColors.textSecondary,
                      ),
                    ),
                  ],
                ],
              ),
            ),
            Icon(
              isSelected
                  ? Icons.radio_button_checked_rounded
                  : Icons.radio_button_off_rounded,
              color: isSelected ? AppColors.error : AppColors.textHint,
              size: 20,
            ),
          ],
        ),
      ),
    );
  }
}
