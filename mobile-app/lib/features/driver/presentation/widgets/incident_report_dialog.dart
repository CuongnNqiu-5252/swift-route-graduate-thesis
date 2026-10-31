import 'package:flutter/material.dart';
import 'package:swiftroute_app/core/theme/app_colors.dart';

/// Predefined incident types as specified in thesis driver design document
enum IncidentType {
  customerUnavailable('Khách không nghe máy / Không liên lạc được', Icons.phone_missed_rounded),
  trafficDelay('Kẹt xe nghiêm trọng / Trễ giờ', Icons.traffic_rounded),
  wrongAddress('Sai địa chỉ giao hàng / Không tìm thấy nhà', Icons.wrong_location_rounded),
  vehicleProblem('Sự cố xe cộ / Hàng hóa gặp sự cố', Icons.car_crash_rounded),
  other('Lý do khác', Icons.report_problem_rounded);

  final String label;
  final IconData icon;
  const IncidentType(this.label, this.icon);
}

/// Dialog allowing drivers to report an issue for a delivery stop (D06 / D07)
class IncidentReportDialog extends StatefulWidget {
  const IncidentReportDialog({
    super.key,
    required this.orderId,
    required this.deliveryAddress,
  });

  final String orderId;
  final String deliveryAddress;

  static Future<({IncidentType type, String note})?> show(
    BuildContext context, {
    required String orderId,
    required String deliveryAddress,
  }) {
    return showDialog<({IncidentType type, String note})>(
      context: context,
      builder: (ctx) => IncidentReportDialog(
        orderId: orderId,
        deliveryAddress: deliveryAddress,
      ),
    );
  }

  @override
  State<IncidentReportDialog> createState() => _IncidentReportDialogState();
}

class _IncidentReportDialogState extends State<IncidentReportDialog> {
  IncidentType _selectedType = IncidentType.customerUnavailable;
  final TextEditingController _noteController = TextEditingController();

  @override
  void dispose() {
    _noteController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      titlePadding: const EdgeInsets.fromLTRB(24, 20, 24, 8),
      contentPadding: const EdgeInsets.fromLTRB(24, 12, 24, 20),
      title: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(8),
            decoration: BoxDecoration(
              color: AppColors.error.withValues(alpha: 0.12),
              shape: BoxShape.circle,
            ),
            child: const Icon(Icons.warning_amber_rounded, color: AppColors.error, size: 24),
          ),
          const SizedBox(width: 12),
          const Expanded(
            child: Text(
              'Báo cáo giao thất bại',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
            ),
          ),
        ],
      ),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Đơn hàng #${widget.orderId.length > 8 ? widget.orderId.substring(0, 8) : widget.orderId}',
              style: const TextStyle(fontWeight: FontWeight.w600, color: AppColors.textPrimary),
            ),
            const SizedBox(height: 4),
            Text(
              widget.deliveryAddress,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(fontSize: 12, color: AppColors.textSecondary),
            ),
            const SizedBox(height: 16),
            const Text(
              'Chọn nguyên nhân sự cố:',
              style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: AppColors.textPrimary),
            ),
            const SizedBox(height: 8),
            ...IncidentType.values.map((type) {
              final isSelected = type == _selectedType;
              return InkWell(
                onTap: () => setState(() => _selectedType = type),
                borderRadius: BorderRadius.circular(8),
                child: Container(
                  margin: const EdgeInsets.only(bottom: 6),
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
                  decoration: BoxDecoration(
                    color: isSelected ? AppColors.error.withValues(alpha: 0.08) : Colors.transparent,
                    border: Border.all(
                      color: isSelected ? AppColors.error : AppColors.divider,
                      width: isSelected ? 1.5 : 1.0,
                    ),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Row(
                    children: [
                      Icon(
                        type.icon,
                        size: 18,
                        color: isSelected ? AppColors.error : AppColors.textSecondary,
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Text(
                          type.label,
                          style: TextStyle(
                            fontSize: 13,
                            fontWeight: isSelected ? FontWeight.w600 : FontWeight.normal,
                            color: isSelected ? AppColors.error : AppColors.textPrimary,
                          ),
                        ),
                      ),
                      if (isSelected)
                        const Icon(Icons.check_circle_rounded, size: 18, color: AppColors.error),
                    ],
                  ),
                ),
              );
            }),
            const SizedBox(height: 12),
            const Text(
              'Ghi chú bổ sung (tuỳ chọn):',
              style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: AppColors.textPrimary),
            ),
            const SizedBox(height: 6),
            TextField(
              controller: _noteController,
              maxLines: 2,
              decoration: InputDecoration(
                hintText: 'Nhập chi tiết tình huống...',
                hintStyle: const TextStyle(fontSize: 12, color: AppColors.textHint),
                border: OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
                contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
              ),
            ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(null),
          child: const Text('Hủy', style: TextStyle(color: AppColors.textSecondary)),
        ),
        FilledButton(
          onPressed: () {
            Navigator.of(context).pop((
              type: _selectedType,
              note: _noteController.text.trim(),
            ));
          },
          style: FilledButton.styleFrom(backgroundColor: AppColors.error),
          child: const Text('Xác nhận báo lỗi'),
        ),
      ],
    );
  }
}

