// features/order/presentation/screens/order_tracking_screen.dart

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/theme/app_colors.dart';
import 'package:swiftroute_app/core/utils/polyline_decoder.dart';
import 'package:swiftroute_app/features/order/application/order_tracking_provider.dart';
import 'package:swiftroute_app/features/order/data/models/order_model.dart';
import 'package:swiftroute_app/features/order/presentation/widgets/goong_map_widget.dart';

/// Screen displaying real-time live tracking of the driver's motorbike on Goong Map,
/// timeline status stepper, driver details, and interactive simulation controls for thesis demo.
class OrderTrackingScreen extends ConsumerWidget {
  const OrderTrackingScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final trackingState = ref.watch(orderTrackingProvider);
    final trackingNotifier = ref.read(orderTrackingProvider.notifier);
    final order = trackingState.order;

    final pickupPoint = order != null
        ? LatLngPoint(order.pickupLat, order.pickupLng)
        : null;

    final deliveryPoint = order != null
        ? LatLngPoint(order.deliveryLat, order.deliveryLng)
        : null;

    return Scaffold(
      body: Stack(
        children: [
          // ── 1. Fullscreen Goong Map ───────────────────────────────────────
          Positioned.fill(
            child: GoongMapWidget(
              pickupLocation: pickupPoint,
              deliveryLocation: deliveryPoint,
              driverLocation: trackingState.driverPosition,
              routePoints: trackingState.routePoints,
            ),
          ),

          // ── 2. Top Header Bar ─────────────────────────────────────────────
          Positioned(
            top: MediaQuery.of(context).padding.top + 10,
            left: 16,
            right: 16,
            child: Row(
              children: [
                // Back button
                CircleAvatar(
                  backgroundColor: Colors.white,
                  child: IconButton(
                    tooltip: 'Quay lại',
                    icon: const Icon(Icons.arrow_back, color: AppColors.textPrimary),
                    onPressed: () {
                      if (Navigator.of(context).canPop()) {
                        Navigator.of(context).pop();
                      } else {
                        Navigator.of(context).pushNamedAndRemoveUntil(
                          '/customer/home',
                          (route) => false,
                        );
                      }
                    },
                  ),
                ),
                const SizedBox(width: 10),

                // Order Code & Status Badge
                Expanded(
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(25),
                      boxShadow: [
                        BoxShadow(
                          color: Colors.black.withAlpha(25),
                          blurRadius: 8,
                          offset: const Offset(0, 2),
                        ),
                      ],
                    ),
                    child: Row(
                      children: [
                        const Icon(Icons.receipt_long, color: AppColors.primary, size: 18),
                        const SizedBox(width: 8),
                        Expanded(
                          child: Text(
                            'Đơn: #${order?.id.substring(0, 8) ?? "SWIFT"}',
                            style: const TextStyle(
                              fontWeight: FontWeight.bold,
                              fontSize: 13,
                              color: AppColors.textPrimary,
                            ),
                          ),
                        ),
                        _buildStatusBadge(trackingState.status),
                      ],
                    ),
                  ),
                ),
                const SizedBox(width: 10),

                // Home button to quickly navigate back to home screen
                CircleAvatar(
                  backgroundColor: Colors.white,
                  child: IconButton(
                    tooltip: 'Về trang chủ',
                    icon: const Icon(Icons.home_rounded, color: AppColors.primary),
                    onPressed: () {
                      Navigator.of(context).pushNamedAndRemoveUntil(
                        '/customer/home',
                        (route) => false,
                      );
                    },
                  ),
                ),
              ],
            ),
          ),

          // ── 3. Bottom Tracking Control Panel ──────────────────────────────
          Positioned(
            left: 0,
            right: 0,
            bottom: 0,
            child: Container(
              padding: const EdgeInsets.all(20),
              decoration: const BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black12,
                    blurRadius: 15,
                    offset: Offset(0, -4),
                  ),
                ],
              ),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  // Timeline 4-step Stepper
                  _buildTimelineStepper(trackingState.status),

                  const Divider(height: 24, color: AppColors.divider),

                  // Driver Info Card
                  Row(
                    children: [
                      // Driver Avatar
                      CircleAvatar(
                        radius: 24,
                        backgroundColor: AppColors.primary.withAlpha(30),
                        child: const Icon(
                          Icons.person,
                          color: AppColors.primary,
                          size: 28,
                        ),
                      ),
                      const SizedBox(width: 12),

                      // Driver Name & Plate
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              trackingState.driverInfo?['full_name'] ?? 'Tài xế nhận đơn',
                              style: const TextStyle(
                                fontSize: 15,
                                fontWeight: FontWeight.bold,
                                color: AppColors.textPrimary,
                              ),
                            ),
                            const SizedBox(height: 2),
                            Text(
                              '${trackingState.driverInfo?['license_plate'] ?? "59-X1 888.88"} • ${trackingState.driverInfo?['vehicle_type'] ?? "Xe máy"}',
                              style: const TextStyle(
                                fontSize: 12,
                                color: AppColors.textSecondary,
                              ),
                            ),
                          ],
                        ),
                      ),

                      // Call Button
                      IconButton(
                        icon: const CircleAvatar(
                          radius: 18,
                          backgroundColor: AppColors.success,
                          child: Icon(Icons.phone, color: Colors.white, size: 18),
                        ),
                        onPressed: () {
                          ScaffoldMessenger.of(context).showSnackBar(
                            const SnackBar(
                              content: Text('Đang kết nối cuộc gọi tới tài xế...'),
                              duration: Duration(seconds: 2),
                            ),
                          );
                        },
                      ),
                    ],
                  ),

                  const SizedBox(height: 16),

                  // ── Simulation Controls (Dedicated for Thesis Defense Demo) ──
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                    decoration: BoxDecoration(
                      color: AppColors.surface,
                      borderRadius: BorderRadius.circular(14),
                      border: Border.all(color: AppColors.divider),
                    ),
                    child: Column(
                      children: [
                        Row(
                          children: [
                            const Icon(
                              Icons.play_circle_fill,
                              color: AppColors.accent,
                              size: 20,
                            ),
                            const SizedBox(width: 8),
                            const Expanded(
                              child: Text(
                                'Mô phỏng xe chạy (Dành cho Demo):',
                                style: TextStyle(
                                  fontSize: 12,
                                  fontWeight: FontWeight.w600,
                                  color: AppColors.textPrimary,
                                ),
                              ),
                            ),
                            // Speed toggles
                            _speedChip(ref, 1, trackingState.simulationSpeed),
                            const SizedBox(width: 4),
                            _speedChip(ref, 2, trackingState.simulationSpeed),
                            const SizedBox(width: 4),
                            _speedChip(ref, 4, trackingState.simulationSpeed),
                          ],
                        ),
                        const SizedBox(height: 8),
                        LinearProgressIndicator(
                          value: trackingState.progressPercentage,
                          backgroundColor: Colors.grey.shade300,
                          color: trackingState.isDelivered
                              ? AppColors.success
                              : AppColors.accent,
                          minHeight: 6,
                          borderRadius: BorderRadius.circular(3),
                        ),
                        const SizedBox(height: 10),
                        Row(
                          children: [
                            Expanded(
                              child: ElevatedButton.icon(
                                onPressed: trackingState.isDelivered
                                    ? () => trackingNotifier.resetSimulation()
                                    : trackingState.isSimulating
                                        ? () => trackingNotifier.pauseSimulation()
                                        : () => trackingNotifier.startSimulation(),
                                icon: Icon(
                                  trackingState.isDelivered
                                      ? Icons.replay
                                      : trackingState.isSimulating
                                          ? Icons.pause
                                          : Icons.play_arrow,
                                  size: 18,
                                ),
                                label: Text(
                                  trackingState.isDelivered
                                      ? 'Chạy lại mô phỏng'
                                      : trackingState.isSimulating
                                          ? 'Tạm dừng xe'
                                          : 'Bắt đầu mô phỏng giao hàng',
                                  style: const TextStyle(
                                    fontSize: 13,
                                    fontWeight: FontWeight.bold,
                                  ),
                                ),
                                style: ElevatedButton.styleFrom(
                                  backgroundColor: trackingState.isDelivered
                                      ? AppColors.primary
                                      : AppColors.accent,
                                  foregroundColor: Colors.white,
                                  shape: RoundedRectangleBorder(
                                    borderRadius: BorderRadius.circular(10),
                                  ),
                                  padding: const EdgeInsets.symmetric(vertical: 10),
                                ),
                              ),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),

                  // Completed Banner
                  if (trackingState.isDelivered)
                    Container(
                      margin: const EdgeInsets.only(top: 12),
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: AppColors.success.withAlpha(30),
                        borderRadius: BorderRadius.circular(10),
                        border: Border.all(color: AppColors.success),
                      ),
                      child: const Row(
                        children: [
                          Icon(Icons.check_circle, color: AppColors.success),
                          SizedBox(width: 10),
                          Expanded(
                            child: Text(
                              'Đơn hàng đã được giao thành công tới người nhận!',
                              style: TextStyle(
                                color: AppColors.success,
                                fontWeight: FontWeight.bold,
                                fontSize: 13,
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildStatusBadge(OrderStatus status) {
    Color color;
    String text;

    switch (status) {
      case OrderStatus.planned:
        color = AppColors.primary;
        text = 'Đã gán tài xế';
        break;
      case OrderStatus.inTransit:
        color = AppColors.accent;
        text = 'Đang giao hàng';
        break;
      case OrderStatus.delivered:
        color = AppColors.success;
        text = 'Giao thành công';
        break;
      default:
        color = Colors.grey;
        text = 'Đang xử lý';
    }

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: color.withAlpha(25),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: color.withAlpha(80)),
      ),
      child: Text(
        text,
        style: TextStyle(
          color: color,
          fontSize: 11,
          fontWeight: FontWeight.bold,
        ),
      ),
    );
  }

  Widget _buildTimelineStepper(OrderStatus currentStatus) {
    int currentStep = 1;
    if (currentStatus == OrderStatus.inTransit) currentStep = 2;
    if (currentStatus == OrderStatus.delivered) currentStep = 3;

    final steps = ['Tiếp nhận', 'Đã gán xế', 'Đang giao', 'Thành công'];

    return Row(
      children: List.generate(steps.length * 2 - 1, (index) {
        if (index.isOdd) {
          final stepIndex = index ~/ 2;
          final isPassed = stepIndex < currentStep;
          return Expanded(
            child: Container(
              height: 2,
              color: isPassed ? AppColors.success : AppColors.divider,
            ),
          );
        } else {
          final stepIndex = index ~/ 2;
          final isPassed = stepIndex <= currentStep;
          return Column(
            children: [
              CircleAvatar(
                radius: 10,
                backgroundColor: isPassed ? AppColors.success : AppColors.divider,
                child: isPassed
                    ? const Icon(Icons.check, size: 12, color: Colors.white)
                    : Text(
                        '${stepIndex + 1}',
                        style: const TextStyle(fontSize: 10, color: Colors.white),
                      ),
              ),
              const SizedBox(height: 4),
              Text(
                steps[stepIndex],
                style: TextStyle(
                  fontSize: 10,
                  fontWeight: isPassed ? FontWeight.bold : FontWeight.normal,
                  color: isPassed ? AppColors.textPrimary : AppColors.textSecondary,
                ),
              ),
            ],
          );
        }
      }),
    );
  }

  Widget _speedChip(WidgetRef ref, int speed, int currentSpeed) {
    final isSelected = speed == currentSpeed;
    return InkWell(
      onTap: () =>
          ref.read(orderTrackingProvider.notifier).setSimulationSpeed(speed),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
        decoration: BoxDecoration(
          color: isSelected ? AppColors.accent : Colors.white,
          borderRadius: BorderRadius.circular(6),
          border: Border.all(
            color: isSelected ? AppColors.accent : AppColors.divider,
          ),
        ),
        child: Text(
          '${speed}x',
          style: TextStyle(
            fontSize: 10,
            fontWeight: FontWeight.bold,
            color: isSelected ? Colors.white : AppColors.textSecondary,
          ),
        ),
      ),
    );
  }
}
