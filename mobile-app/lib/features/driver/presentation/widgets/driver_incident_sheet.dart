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

class _DriverIncidentSheetState extends ConsumerState<DriverIncidentSheet> {
  String _selectedType = 'CUSTOMER_UNREACHABLE';
  String _selectedSeverity = 'MEDIUM';
  final _notesController = TextEditingController();
  var _isSubmitting = false;
  var _contactAttempts = 1;
  var _canContinueDelivery = true;

  Uint8List? _evidenceImageBytes;
  String? _uploadedEvidenceUrl;
  var _isUploadingImage = false;

  final _incidentOptions = const [
    {
      'code': 'CUSTOMER_UNREACHABLE',
      'label': 'Khách không nghe máy / Vắng mặt',
      'icon': Icons.phone_missed_rounded,
      'color': AppColors.warning,
    },
    {
      'code': 'WRONG_ADDRESS',
      'label': 'Sai địa chỉ / Điểm giao đóng cửa',
      'icon': Icons.wrong_location_rounded,
      'color': AppColors.textSecondary,
    },
    {
      'code': 'REFUSED_BY_RECIPIENT',
      'label': 'Khách từ chối nhận hàng',
      'icon': Icons.cancel_outlined,
      'color': AppColors.error,
    },
    {
      'code': 'PACKAGE_DAMAGED',
      'label': 'Hàng hóa bị vỡ / hư hỏng',
      'icon': Icons.broken_image_rounded,
      'color': AppColors.error,
    },
    {
      'code': 'TRAFFIC_CONGESTION',
      'label': 'Kẹt xe nghiêm trọng / Ngập nước',
      'icon': Icons.traffic_rounded,
      'color': AppColors.warning,
    },
    {
      'code': 'VEHICLE_BREAKDOWN',
      'label': 'Hỏng xe / Sự cố phương tiện',
      'icon': Icons.car_crash_rounded,
      'color': AppColors.error,
    },
    {
      'code': 'OTHER',
      'label': 'Lý do sự cố khác',
      'icon': Icons.warning_amber_rounded,
      'color': AppColors.textSecondary,
    },
  ];

  @override
  void dispose() {
    _notesController.dispose();
    super.dispose();
  }

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
          content: Text('Vui lòng nhập chi tiết sự cố khi chọn "Lý do khác".'),
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
            severity: _selectedSeverity,
            notes: notes.isNotEmpty ? notes : null,
            evidenceUrls: evidenceUrls,
            canContinueDelivery: _canContinueDelivery,
            needsImmediateAssistance: _selectedSeverity == 'CRITICAL',
            contactAttempts: _contactAttempts,
          );

      // Refresh driver orders
      await ref.read(driverOrdersProvider.notifier).refreshOrders();

      if (mounted) {
        Navigator.of(context).pop();
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              'Đã báo sự cố đơn ${widget.order.displayCode}. Hệ thống đã ghi nhận!',
            ),
            backgroundColor: AppColors.accent,
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Báo sự cố thất bại: $e'),
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
                        'Báo sự cố giao hàng',
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
                    // Mức độ nghiêm trọng
                    Row(
                      children: [
                        const Text(
                          'Mức độ:',
                          style: TextStyle(
                            fontSize: 13,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: Row(
                            children: [
                              Expanded(
                                child: _severityChip('MEDIUM', 'Thường'),
                              ),
                              const SizedBox(width: 4),
                              Expanded(
                                child: _severityChip('HIGH', 'Nghiêm trọng'),
                              ),
                              const SizedBox(width: 4),
                              Expanded(
                                child: _severityChip('CRITICAL', 'Khẩn cấp'),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 14),

                    const Text(
                      'Chọn nguyên nhân sự cố:',
                      style: TextStyle(
                        fontWeight: FontWeight.w600,
                        fontSize: 14,
                      ),
                    ),
                    const SizedBox(height: 8),

                    ..._incidentOptions.map((opt) {
                      final isSelected = _selectedType == opt['code'];
                      return InkWell(
                        onTap: () => setState(
                          () => _selectedType = opt['code'] as String,
                        ),
                        borderRadius: BorderRadius.circular(12),
                        child: Container(
                          margin: const EdgeInsets.only(bottom: 6),
                          padding: const EdgeInsets.symmetric(
                            horizontal: 12,
                            vertical: 10,
                          ),
                          decoration: BoxDecoration(
                            color: isSelected
                                ? AppColors.error.withValues(alpha: 0.06)
                                : AppColors.card,
                            borderRadius: BorderRadius.circular(12),
                            border: Border.all(
                              color: isSelected
                                  ? AppColors.error
                                  : AppColors.divider,
                              width: isSelected ? 1.5 : 1,
                            ),
                          ),
                          child: Row(
                            children: [
                              Icon(
                                opt['icon'] as IconData,
                                color: opt['color'] as Color,
                                size: 20,
                              ),
                              const SizedBox(width: 10),
                              Expanded(
                                child: Text(
                                  opt['label'] as String,
                                  style: TextStyle(
                                    fontSize: 13,
                                    fontWeight: isSelected
                                        ? FontWeight.bold
                                        : FontWeight.normal,
                                    color: isSelected
                                        ? AppColors.textPrimary
                                        : AppColors.textSecondary,
                                  ),
                                ),
                              ),
                              Icon(
                                isSelected
                                    ? Icons.radio_button_checked_rounded
                                    : Icons.radio_button_off_rounded,
                                color: isSelected
                                    ? AppColors.error
                                    : AppColors.textHint,
                                size: 20,
                              ),
                            ],
                          ),
                        ),
                      );
                    }),

                    const SizedBox(height: 10),

                    // Số lần liên lạc
                    if (_selectedType == 'CUSTOMER_UNREACHABLE') ...[
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          const Expanded(
                            child: Text(
                              'Số lần gọi cho khách:',
                              style: TextStyle(
                                fontSize: 13,
                                fontWeight: FontWeight.w500,
                              ),
                            ),
                          ),
                          Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              IconButton(
                                visualDensity: VisualDensity.compact,
                                icon: const Icon(
                                  Icons.remove_circle_outline,
                                  size: 20,
                                ),
                                onPressed: _contactAttempts > 1
                                    ? () => setState(() => _contactAttempts--)
                                    : null,
                              ),
                              Text(
                                '$_contactAttempts lần',
                                style: const TextStyle(
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                              IconButton(
                                visualDensity: VisualDensity.compact,
                                icon: const Icon(
                                  Icons.add_circle_outline,
                                  size: 20,
                                ),
                                onPressed: () =>
                                    setState(() => _contactAttempts++),
                              ),
                            ],
                          ),
                        ],
                      ),
                      const SizedBox(height: 6),
                    ],

                    // Photo Evidence Picker (Cloudinary)
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
                              'Đang tải ảnh lên Cloudinary...',
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
                              width: 60,
                              height: 60,
                              fit: BoxFit.cover,
                            ),
                          ),
                          const SizedBox(width: 10),
                          const Expanded(
                            child: Text(
                              '✓ Đã gắn ảnh bằng chứng vào báo cáo sự cố',
                              style: TextStyle(
                                fontSize: 12,
                                color: AppColors.success,
                                fontWeight: FontWeight.w500,
                              ),
                            ),
                          ),
                        ],
                      ),
                    ],

                    const SizedBox(height: 8),

                    // Toggles
                    SwitchListTile(
                      contentPadding: EdgeInsets.zero,
                      dense: true,
                      title: const Text(
                        'Vẫn có thể tiếp tục giao các đơn khác',
                        style: TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                      subtitle: const Text(
                        'Bật nếu sự cố chỉ ảnh hưởng đơn này',
                        style: TextStyle(fontSize: 11),
                      ),
                      value: _canContinueDelivery,
                      onChanged: (val) =>
                          setState(() => _canContinueDelivery = val),
                    ),

                    const SizedBox(height: 8),
                    TextField(
                      controller: _notesController,
                      maxLines: 2,
                      decoration: InputDecoration(
                        labelText: 'Ghi chú cho điều phối viên (tuỳ chọn)',
                        hintText: 'VD: Khách hẹn giao sau 18h...',
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
                            'Gửi báo sự cố',
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

  Widget _severityChip(String code, String label) {
    final isSelected = _selectedSeverity == code;
    final activeColor = code == 'CRITICAL'
        ? AppColors.error
        : (code == 'HIGH' ? AppColors.accent : AppColors.primary);

    return InkWell(
      onTap: () => setState(() => _selectedSeverity = code),
      borderRadius: BorderRadius.circular(10),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 150),
        padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 4),
        decoration: BoxDecoration(
          color: isSelected ? activeColor : AppColors.card,
          borderRadius: BorderRadius.circular(10),
          border: Border.all(
            color: isSelected ? activeColor : AppColors.divider,
            width: isSelected ? 1.5 : 1,
          ),
        ),
        alignment: Alignment.center,
        child: Text(
          label,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: TextStyle(
            fontSize: 12,
            fontWeight: isSelected ? FontWeight.w700 : FontWeight.w500,
            color: isSelected ? Colors.white : AppColors.textPrimary,
          ),
        ),
      ),
    );
  }
}
