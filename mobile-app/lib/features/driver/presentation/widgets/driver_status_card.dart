import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/theme/app_colors.dart';
import 'package:swiftroute_app/core/utils/location_helper.dart';
import 'package:swiftroute_app/features/driver/application/driver_provider.dart';
import 'package:swiftroute_app/features/driver/data/models/driver_model.dart';

/// Card managing driver availability (online/offline) and real-time GPS sync (D02)
class DriverStatusCard extends ConsumerStatefulWidget {
  const DriverStatusCard({super.key, required this.driver});

  final DriverModel? driver;

  @override
  ConsumerState<DriverStatusCard> createState() => _DriverStatusCardState();
}

class _DriverStatusCardState extends ConsumerState<DriverStatusCard> {
  var _isSyncingLocation = false;
  DateTime? _lastSyncTime;

  Future<void> _handleGpsSync() async {
    setState(() => _isSyncingLocation = true);
    try {
      final position = await LocationHelper.getCurrentPosition();
      final lat = position?.latitude ?? widget.driver?.currentLat ?? 10.776889;
      final lng = position?.longitude ?? widget.driver?.currentLng ?? 106.700806;

      final success = await ref.read(driverProvider.notifier).reportLocation(lat: lat, lng: lng);
      if (mounted) {
        setState(() => _lastSyncTime = DateTime.now());
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              success
                  ? 'Đã đồng bộ toạ độ GPS: (${lat.toStringAsFixed(4)}, ${lng.toStringAsFixed(4)})'
                  : 'Không thể đồng bộ GPS. Vui lòng thử lại.',
            ),
            duration: const Duration(seconds: 2),
            backgroundColor: success ? AppColors.success : AppColors.error,
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
    final statusColor = isAvailable ? AppColors.success : AppColors.textSecondary;

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppColors.divider),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.04),
            blurRadius: 10,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Availability Toggle Row
          Row(
            children: [
              Container(
                width: 12,
                height: 12,
                decoration: BoxDecoration(
                  color: statusColor,
                  shape: BoxShape.circle,
                  boxShadow: isAvailable
                      ? [
                          BoxShadow(
                            color: AppColors.success.withValues(alpha: 0.4),
                            blurRadius: 8,
                            spreadRadius: 2,
                          ),
                        ]
                      : null,
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      isAvailable ? 'SẴN SÀNG NHẬN ĐƠN' : 'TẠM NGHỈ (OFFLINE)',
                      style: TextStyle(
                        fontSize: 14,
                        fontWeight: FontWeight.bold,
                        color: statusColor,
                        letterSpacing: 0.5,
                      ),
                    ),
                    Text(
                      isAvailable
                          ? 'Đang trực tuyến trên hệ thống điều phối'
                          : 'Bật để nhận điều phối tuyến đường hôm nay',
                      style: const TextStyle(fontSize: 11, color: AppColors.textSecondary),
                    ),
                  ],
                ),
              ),
              Switch(
                value: isAvailable,
                activeThumbColor: AppColors.success,
                activeTrackColor: AppColors.success.withValues(alpha: 0.2),
                inactiveThumbColor: AppColors.textSecondary,
                inactiveTrackColor: AppColors.surfaceVariant,
                onChanged: (val) {
                  ref.read(driverProvider.notifier).toggleAvailability();
                },
              ),
            ],
          ),
          const Divider(height: 24, color: AppColors.divider),
          // Vehicle & GPS info row
          Row(
            children: [
              // Vehicle badge
              Expanded(
                child: Row(
                  children: [
                    Container(
                      padding: const EdgeInsets.all(8),
                      decoration: BoxDecoration(
                        color: AppColors.surface,
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: const Icon(Icons.two_wheeler_rounded, size: 20, color: AppColors.primary),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            driver?.licensePlate.isNotEmpty == true ? driver!.licensePlate : '59-X1 111.11',
                            style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w700),
                          ),
                          Text(
                            '${driver?.vehicleTypeLabel ?? "Xe máy"} · ${driver?.driverTypeLabel ?? "Toàn thời gian"}',
                            style: const TextStyle(fontSize: 11, color: AppColors.textSecondary),
                            overflow: TextOverflow.ellipsis,
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
              // GPS Update action button
              OutlinedButton.icon(
                onPressed: _isSyncingLocation ? null : _handleGpsSync,
                icon: _isSyncingLocation
                    ? const SizedBox(
                        width: 14,
                        height: 14,
                        child: CircularProgressIndicator(strokeWidth: 2, color: AppColors.primary),
                      )
                    : const Icon(Icons.my_location_rounded, size: 16),
                label: Text(
                  _lastSyncTime != null ? 'Đã sync GPS' : 'Gửi GPS',
                  style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w600),
                ),
                style: OutlinedButton.styleFrom(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
                  side: const BorderSide(color: AppColors.divider),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
