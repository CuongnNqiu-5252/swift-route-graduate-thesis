// features/driver/presentation/widgets/driver_status_card.dart

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/theme/app_colors.dart';
import 'package:swiftroute_app/core/utils/formatters.dart';
import 'package:swiftroute_app/core/utils/location_helper.dart';
import 'package:swiftroute_app/features/driver/application/driver_provider.dart';
import 'package:swiftroute_app/features/driver/data/models/driver_model.dart';

/// Compact, high-contrast Shift Availability & GPS Sync card for outdoor one-handed toggling.
class DriverStatusCard extends ConsumerStatefulWidget {
  const DriverStatusCard({super.key, required this.driver});

  final DriverModel? driver;

  @override
  ConsumerState<DriverStatusCard> createState() => _DriverStatusCardState();
}

class _DriverStatusCardState extends ConsumerState<DriverStatusCard> {
  bool _isSyncingLocation = false;
  DateTime? _lastSyncTime;

  Future<void> _handleGpsSync() async {
    setState(() => _isSyncingLocation = true);
    try {
      final position = await LocationHelper.getCurrentPosition();
      if (position == null) throw StateError('Không lấy được vị trí GPS');
      final ok = await ref
          .read(driverProvider.notifier)
          .reportLocation(
            lat: position.latitude,
            lng: position.longitude,
            accuracyM: position.accuracy < 0 ? 0 : position.accuracy,
            speedMps: position.speed < 0 ? 0 : position.speed,
            bearing: position.heading < 0 ? 0 : position.heading,
            recordedAt: position.timestamp,
            sequence: DateTime.now().millisecondsSinceEpoch,
          );
      if (mounted) {
        if (ok) setState(() => _lastSyncTime = DateTime.now());
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            backgroundColor: ok ? AppColors.success : AppColors.error,
            content: Text(
              ok
                  ? 'Đã đồng bộ vị trí GPS lúc ${AppFormatters.time(DateTime.now())}'
                  : 'Không đồng bộ được GPS. Kiểm tra mạng và thử lại.',
            ),
          ),
        );
      }
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            backgroundColor: AppColors.error,
            content: Text('Chưa cấp quyền vị trí GPS cho ứng dụng.'),
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _isSyncingLocation = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final driver = widget.driver;
    final isAvailable = driver?.isAvailable ?? false;
    final isApproved = driver?.status == 'ACTIVE';

    final surfaceColor = isAvailable
        ? AppColors.successSoft
        : (isApproved ? AppColors.card : AppColors.warningSoft);
    final borderColor = isAvailable
        ? AppColors.successBorder
        : (isApproved ? AppColors.divider : AppColors.warningBorder);
    final titleColor = isAvailable
        ? AppColors.success
        : (isApproved ? AppColors.textPrimary : AppColors.warning);

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: surfaceColor,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: borderColor),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 42,
                height: 42,
                decoration: BoxDecoration(
                  color: isAvailable
                      ? AppColors.success
                      : (isApproved
                            ? AppColors.surface
                            : AppColors.warning.withValues(alpha: 0.14)),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Icon(
                  isAvailable
                      ? Icons.sensors_rounded
                      : (isApproved
                            ? Icons.pause_circle_outline_rounded
                            : Icons.verified_user_outlined),
                  color: isAvailable
                      ? Colors.white
                      : (isApproved
                            ? AppColors.textSecondary
                            : AppColors.warning),
                  size: 22,
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      isAvailable
                          ? 'Đang trực tuyến nhận chuyến'
                          : (isApproved
                                ? 'Đang nghỉ ca (Offline)'
                                : 'Hồ sơ chờ doanh nghiệp duyệt'),
                      style: TextStyle(
                        fontSize: 15.5,
                        fontWeight: FontWeight.w800,
                        color: titleColor,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      isAvailable
                          ? (_lastSyncTime != null
                                ? 'GPS cập nhật lúc ${AppFormatters.time(_lastSyncTime!)}'
                                : 'Tự động gửi vị trí về trung tâm điều phối')
                          : (isApproved
                                ? 'Gạt công tắc để bắt đầu ca giao hôm nay'
                                : 'Hoàn thiện biển số xe và hồ sơ ở tab Tài khoản'),
                      style: const TextStyle(
                        fontSize: 12.5,
                        color: AppColors.textSecondary,
                      ),
                    ),
                  ],
                ),
              ),
              Switch(
                value: isAvailable,
                activeThumbColor: Colors.white,
                activeTrackColor: AppColors.success,
                onChanged: !isApproved
                    ? null
                    : (_) => ref
                          .read(driverProvider.notifier)
                          .toggleAvailability(),
              ),
            ],
          ),
          if (isAvailable) ...[
            const SizedBox(height: 12),
            Row(
              children: [
                if (driver?.licensePlate != null &&
                    driver!.licensePlate.isNotEmpty) ...[
                  Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 9,
                      vertical: 5,
                    ),
                    decoration: BoxDecoration(
                      color: AppColors.card,
                      borderRadius: BorderRadius.circular(8),
                      border: Border.all(color: AppColors.successBorder),
                    ),
                    child: Text(
                      driver.licensePlate,
                      style: const TextStyle(
                        fontSize: 12,
                        fontWeight: FontWeight.w800,
                        color: AppColors.textPrimary,
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                ],
                const Spacer(),
                TextButton.icon(
                  onPressed: _isSyncingLocation ? null : _handleGpsSync,
                  style: TextButton.styleFrom(
                    foregroundColor: AppColors.success,
                    minimumSize: const Size(64, 38),
                    padding: const EdgeInsets.symmetric(horizontal: 10),
                  ),
                  icon: _isSyncingLocation
                      ? const SizedBox(
                          width: 14,
                          height: 14,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Icon(Icons.my_location_rounded, size: 16),
                  label: Text(
                    _isSyncingLocation ? 'Đang gửi…' : 'Cập nhật GPS ngay',
                    style: const TextStyle(
                      fontSize: 12.5,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }
}
