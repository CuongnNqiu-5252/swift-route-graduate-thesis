import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/theme/app_colors.dart';
import 'package:swiftroute_app/features/driver/application/driver_provider.dart';
import 'package:swiftroute_app/features/order/data/models/order_model.dart';

class DriverIncidentSheet extends ConsumerStatefulWidget {
  const DriverIncidentSheet({super.key, required this.order});

  final OrderModel order;

  static Future<void> show(BuildContext context, OrderModel order) {
    return showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => DriverIncidentSheet(order: order),
    );
  }

  @override
  ConsumerState<DriverIncidentSheet> createState() => _DriverIncidentSheetState();
}

class _DriverIncidentSheetState extends ConsumerState<DriverIncidentSheet> {
  String _selectedType = 'CUSTOMER_UNREACHABLE';
  final _notesController = TextEditingController();
  var _isSubmitting = false;

  final _incidentOptions = const [
    {
      'code': 'CUSTOMER_UNREACHABLE',
      'label': 'Khách không nghe máy / Vắng mặt',
      'icon': Icons.phone_missed_rounded,
      'color': Colors.orange,
    },
    {
      'code': 'TRAFFIC_CONGESTION',
      'label': 'Kẹt xe nghiêm trọng / Ngập nước',
      'icon': Icons.traffic_rounded,
      'color': Colors.amber,
    },
    {
      'code': 'VEHICLE_BREAKDOWN',
      'label': 'Hỏng xe / Sự cố phương tiện',
      'icon': Icons.car_crash_rounded,
      'color': Colors.redAccent,
    },
    {
      'code': 'WRONG_ADDRESS',
      'label': 'Sai địa chỉ / Điểm giao đóng cửa',
      'icon': Icons.wrong_location_rounded,
      'color': Colors.purple,
    },
    {
      'code': 'OTHER',
      'label': 'Lý do sự cố khác',
      'icon': Icons.warning_amber_rounded,
      'color': Colors.blueGrey,
    },
  ];

  @override
  void dispose() {
    _notesController.dispose();
    super.dispose();
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
      await ref.read(driverOrdersProvider.notifier).reportIncident(
            orderId: widget.order.id,
            incidentType: _selectedType,
            notes: notes.isNotEmpty ? notes : null,
          );
      if (mounted) {
        Navigator.of(context).pop();
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Đã báo sự cố đơn #${widget.order.id.substring(0, 8)}. Hệ thống đã ghi nhận!'),
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
    return Container(
      padding: EdgeInsets.only(
        bottom: MediaQuery.of(context).viewInsets.bottom + 20,
        top: 20,
        left: 20,
        right: 20,
      ),
      decoration: const BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
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
                  color: Colors.grey.shade300,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
            const SizedBox(height: 16),
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    color: AppColors.error.withValues(alpha: 0.1),
                    shape: BoxShape.circle,
                  ),
                  child: const Icon(Icons.report_problem_rounded, color: AppColors.error, size: 24),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text(
                        'Báo cáo sự cố khi giao',
                        style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: AppColors.textPrimary),
                      ),
                      Text(
                        'Đơn #${widget.order.id.substring(0, 8)} · ${widget.order.deliveryAddress}',
                        style: const TextStyle(fontSize: 12, color: AppColors.textSecondary),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 20),
            const Text(
              'Chọn nguyên nhân sự cố:',
              style: TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
            ),
            const SizedBox(height: 10),
            ..._incidentOptions.map((opt) {
              final isSelected = _selectedType == opt['code'];
              return InkWell(
                onTap: () => setState(() => _selectedType = opt['code'] as String),
                borderRadius: BorderRadius.circular(12),
                child: Container(
                  margin: const EdgeInsets.only(bottom: 8),
                  padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                  decoration: BoxDecoration(
                    color: isSelected ? AppColors.error.withValues(alpha: 0.06) : Colors.grey.shade50,
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(
                      color: isSelected ? AppColors.error : AppColors.divider,
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
                      const SizedBox(width: 12),
                      Expanded(
                        child: Text(
                          opt['label'] as String,
                          style: TextStyle(
                            fontSize: 14,
                            fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                            color: isSelected ? AppColors.textPrimary : AppColors.textSecondary,
                          ),
                        ),
                      ),
                      Radio<String>(
                        value: opt['code'] as String,
                        groupValue: _selectedType,
                        activeColor: AppColors.error,
                        onChanged: (val) {
                          if (val != null) setState(() => _selectedType = val);
                        },
                      ),
                    ],
                  ),
                ),
              );
            }),
            const SizedBox(height: 12),
            TextField(
              controller: _notesController,
              maxLines: 2,
              decoration: InputDecoration(
                labelText: 'Ghi chú thêm cho điều phối viên (tuỳ chọn)',
                hintText: 'VD: Đã gọi khách 3 cuộc nhưng thuê bao...',
                hintStyle: const TextStyle(fontSize: 13, color: AppColors.textSecondary),
                filled: true,
                fillColor: Colors.grey.shade50,
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(12),
                  borderSide: const BorderSide(color: AppColors.divider),
                ),
                contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
              ),
            ),
            const SizedBox(height: 16),
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: Colors.amber.shade50,
                borderRadius: BorderRadius.circular(10),
                border: Border.all(color: Colors.amber.shade200),
              ),
              child: const Row(
                children: [
                  Icon(Icons.info_outline_rounded, color: Colors.amber, size: 20),
                  SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      'Đơn hàng sẽ chuyển sang trạng thái Thất bại. Nhân viên vận hành sẽ hỗ trợ tái điều phối.',
                      style: TextStyle(fontSize: 12, color: Colors.black87),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 20),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed: _isSubmitting ? null : () => Navigator.of(context).pop(),
                    style: OutlinedButton.styleFrom(
                      padding: const EdgeInsets.symmetric(vertical: 14),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                    ),
                    child: const Text('Huỷ bỏ'),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: ElevatedButton(
                    onPressed: _isSubmitting ? null : _submitIncident,
                    style: ElevatedButton.styleFrom(
                      backgroundColor: AppColors.error,
                      padding: const EdgeInsets.symmetric(vertical: 14),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                    ),
                    child: _isSubmitting
                        ? const SizedBox(
                            height: 20,
                            width: 20,
                            child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2),
                          )
                        : const Text(
                            'Gửi báo sự cố',
                            style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold),
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
}
