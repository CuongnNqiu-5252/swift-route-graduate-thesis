// features/driver/presentation/widgets/driver_delivery_pod_sheet.dart

import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';
import 'package:swiftroute_app/core/theme/app_colors.dart';
import 'package:swiftroute_app/core/theme/app_theme.dart';
import 'package:swiftroute_app/core/utils/formatters.dart';
import 'package:swiftroute_app/features/driver/application/driver_provider.dart';
import 'package:swiftroute_app/features/order/data/datasources/order_remote_data_source.dart';
import 'package:swiftroute_app/features/order/data/models/order_model.dart';

/// Streamlined 1-step Proof-of-Delivery (POD) & COD confirmation bottom sheet.
/// Uploads photo evidence to Cloudinary and completes the delivery in seconds.
class DriverDeliveryPodSheet extends ConsumerStatefulWidget {
  const DriverDeliveryPodSheet({super.key, required this.order});

  final OrderModel order;

  static Future<bool> show(BuildContext context, OrderModel order) async {
    final result = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => DriverDeliveryPodSheet(order: order),
    );
    return result == true;
  }

  @override
  ConsumerState<DriverDeliveryPodSheet> createState() =>
      _DriverDeliveryPodSheetState();
}

class _DriverDeliveryPodSheetState
    extends ConsumerState<DriverDeliveryPodSheet> {
  final ImagePicker _picker = ImagePicker();

  Uint8List? _photoBytes;
  String? _uploadedUrl;
  bool _isUploading = false;
  bool _isSubmitting = false;
  String? _errorText;

  bool get _hasCod => widget.order.codAmount > 0;

  @override
  void dispose() {
    super.dispose();
  }

  Future<void> _capturePhoto(ImageSource source) async {
    try {
      final picked = await _picker.pickImage(
        source: source,
        imageQuality: 75,
        maxWidth: 1600,
      );
      if (picked == null) return;
      final bytes = await picked.readAsBytes();
      if (!mounted) return;
      setState(() {
        _photoBytes = bytes;
        _isUploading = true;
        _errorText = null;
      });
      final url = await ref
          .read(orderRemoteDataSourceProvider)
          .uploadEvidenceImage(bytes: bytes, fileName: picked.name);
      if (!mounted) return;
      setState(() {
        _uploadedUrl = url;
        _isUploading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _isUploading = false;
        _errorText = 'Không tải được ảnh POD lên máy chủ. Vui lòng chụp lại.';
      });
    }
  }

  Future<void> _submitCompletion() async {
    final taskId = widget.order.deliveryTaskId;
    if (taskId == null || taskId.isEmpty) {
      setState(() => _errorText = 'Không tìm thấy mã tác vụ giao hàng.');
      return;
    }
    if (_isUploading) return;

    setState(() {
      _isSubmitting = true;
      _errorText = null;
    });

    try {
      await ref
          .read(driverOrdersProvider.notifier)
          .completeDelivery(
            orderId: widget.order.id,
            taskId: taskId,
            proofUrl: _uploadedUrl,
            codAmount: _hasCod ? widget.order.codAmount : null,
            codOutcome: _hasCod ? 'FULL' : null,
            codReason: null,
          );
      if (!mounted) return;
      Navigator.of(context).pop(true);
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _isSubmitting = false;
        _errorText = 'Chưa hoàn tất được đơn hàng: $e';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final bottomInset =
        MediaQuery.viewInsetsOf(context).bottom +
        MediaQuery.paddingOf(context).bottom;

    return Container(
      decoration: const BoxDecoration(
        color: AppColors.card,
        borderRadius: BorderRadius.vertical(top: Radius.circular(22)),
      ),
      padding: EdgeInsets.fromLTRB(20, 12, 20, 20 + bottomInset),
      child: SingleChildScrollView(
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
                  borderRadius: BorderRadius.circular(99),
                ),
              ),
            ),
            const SizedBox(height: 16),
            Row(
              children: [
                Expanded(
                  child: Text(
                    'Hoàn tất giao đơn ${widget.order.displayCode}',
                    style: const TextStyle(
                      fontSize: 19,
                      fontWeight: FontWeight.w800,
                      color: AppColors.textPrimary,
                    ),
                  ),
                ),
                IconButton(
                  onPressed: () => Navigator.of(context).pop(false),
                  icon: const Icon(Icons.close_rounded),
                ),
              ],
            ),
            Text(
              widget.order.recipientName?.isNotEmpty == true
                  ? '${widget.order.recipientName} · ${widget.order.deliveryAddress}'
                  : widget.order.deliveryAddress,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                fontSize: 13.5,
                color: AppColors.textSecondary,
              ),
            ),
            const SizedBox(height: 18),

            // 1. COD Collection Box (Clean Amber Card)
            if (_hasCod) ...[
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                decoration: BoxDecoration(
                  color: AppColors.warningSoft,
                  borderRadius: BorderRadius.circular(14),
                  border: Border.all(color: AppColors.warningBorder),
                ),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    const Row(
                      children: [
                        Icon(
                          Icons.payments_rounded,
                          color: AppColors.warning,
                          size: 22,
                        ),
                        SizedBox(width: 8),
                        Text(
                          'Thu tiền mặt COD:',
                          style: TextStyle(
                            fontSize: 14,
                            fontWeight: FontWeight.w700,
                            color: AppColors.warning,
                          ),
                        ),
                      ],
                    ),
                    Text(
                      AppFormatters.currency(widget.order.codAmount),
                      style: AppTheme.numStyle(
                        fontSize: 18,
                        fontWeight: FontWeight.w800,
                        color: AppColors.warning,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),
            ],

            // 2. Photo Proof of Delivery (Cloudinary)
            const Text(
              'Ảnh bằng chứng giao hàng (POD)',
              style: TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.w700,
                color: AppColors.textPrimary,
              ),
            ),
            const SizedBox(height: 8),
            if (_photoBytes != null)
              Stack(
                children: [
                  ClipRRect(
                    borderRadius: BorderRadius.circular(14),
                    child: Image.memory(
                      _photoBytes!,
                      height: 168,
                      width: double.infinity,
                      fit: BoxFit.cover,
                    ),
                  ),
                  if (_isUploading)
                    Positioned.fill(
                      child: Container(
                        decoration: BoxDecoration(
                          color: Colors.black.withValues(alpha: 0.48),
                          borderRadius: BorderRadius.circular(14),
                        ),
                        child: const Center(
                          child: Column(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              CircularProgressIndicator(color: Colors.white),
                              SizedBox(height: 8),
                              Text(
                                'Đang tải ảnh lên Cloudinary…',
                                style: TextStyle(
                                  color: Colors.white,
                                  fontSize: 13,
                                  fontWeight: FontWeight.w700,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ),
                  if (!_isUploading && _uploadedUrl != null)
                    Positioned(
                      left: 10,
                      bottom: 10,
                      child: Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 10,
                          vertical: 5,
                        ),
                        decoration: BoxDecoration(
                          color: AppColors.success,
                          borderRadius: BorderRadius.circular(99),
                        ),
                        child: const Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Icon(
                              Icons.check_circle_rounded,
                              size: 14,
                              color: Colors.white,
                            ),
                            SizedBox(width: 5),
                            Text(
                              'Đã lưu ảnh POD',
                              style: TextStyle(
                                color: Colors.white,
                                fontSize: 12,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  Positioned(
                    right: 8,
                    top: 8,
                    child: IconButton.filledTonal(
                      onPressed: _isUploading
                          ? null
                          : () => _capturePhoto(ImageSource.camera),
                      icon: const Icon(Icons.camera_alt_rounded, size: 18),
                      tooltip: 'Chụp lại',
                    ),
                  ),
                ],
              )
            else
              Row(
                children: [
                  Expanded(
                    flex: 3,
                    child: OutlinedButton.icon(
                      onPressed: () => _capturePhoto(ImageSource.camera),
                      style: OutlinedButton.styleFrom(
                        minimumSize: const Size.fromHeight(54),
                        backgroundColor: AppColors.primarySoft,
                        foregroundColor: AppColors.primary,
                        side: const BorderSide(color: AppColors.primaryBorder),
                      ),
                      icon: const Icon(Icons.camera_alt_rounded, size: 20),
                      label: const Text('Chụp ảnh kiện hàng'),
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    flex: 2,
                    child: OutlinedButton.icon(
                      onPressed: () => _capturePhoto(ImageSource.gallery),
                      style: OutlinedButton.styleFrom(
                        minimumSize: const Size.fromHeight(54),
                      ),
                      icon: const Icon(Icons.photo_library_outlined, size: 19),
                      label: const Text('Thư viện'),
                    ),
                  ),
                ],
              ),

            if (_errorText != null) ...[
              const SizedBox(height: 12),
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: AppColors.errorSoft,
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(color: AppColors.errorBorder),
                ),
                child: Row(
                  children: [
                    const Icon(
                      Icons.error_outline_rounded,
                      color: AppColors.error,
                      size: 18,
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        _errorText!,
                        style: const TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.w600,
                          color: AppColors.errorDark,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ],

            const SizedBox(height: 20),
            SizedBox(
              width: double.infinity,
              height: 54,
              child: FilledButton.icon(
                onPressed: (_isSubmitting || _isUploading)
                    ? null
                    : _submitCompletion,
                style: FilledButton.styleFrom(
                  backgroundColor: AppColors.success,
                ),
                icon: _isSubmitting
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(
                          strokeWidth: 2.2,
                          color: Colors.white,
                        ),
                      )
                    : const Icon(Icons.check_circle_rounded, size: 20),
                label: Text(
                  _isSubmitting
                      ? 'Đang hoàn tất…'
                      : (_hasCod
                          ? 'Xác nhận Đã thu tiền & Giao thành công'
                          : 'Xác nhận Giao thành công'),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
