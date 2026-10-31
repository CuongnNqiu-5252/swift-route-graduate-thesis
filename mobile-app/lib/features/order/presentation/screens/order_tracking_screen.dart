// features/order/presentation/screens/order_tracking_screen.dart

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/theme/app_colors.dart';
import 'package:swiftroute_app/core/utils/polyline_decoder.dart';
import 'package:swiftroute_app/features/order/application/order_tracking_provider.dart';
import 'package:swiftroute_app/features/order/data/models/order_model.dart';
import 'package:swiftroute_app/features/order/presentation/widgets/goong_map_widget.dart';

/// Screen displaying real-time live tracking of the driver's motorbike on Goong Map,
/// timeline status stepper, and driver details.
/// Automatically connects to the server stream and updates vehicle position in real-time.
class OrderTrackingScreen extends ConsumerStatefulWidget {
  final OrderModel? targetOrder;

  const OrderTrackingScreen({super.key, this.targetOrder});

  @override
  ConsumerState<OrderTrackingScreen> createState() => _OrderTrackingScreenState();
}

class _OrderTrackingScreenState extends ConsumerState<OrderTrackingScreen> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (widget.targetOrder != null) {
        ref.read(orderTrackingProvider.notifier).selectOrderForTracking(widget.targetOrder!);
      }
      final state = ref.read(orderTrackingProvider);
      if (state.order != null && !state.isDelivered && !state.isSseConnected) {
        ref.read(orderTrackingProvider.notifier).startSseTracking();
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final trackingState = ref.watch(orderTrackingProvider);
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
                      ref.read(orderTrackingProvider.notifier).fetchActiveOrder();
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

          // ── 3. Bottom Tracking Sheet ──────────────────────────────────────
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
                        backgroundColor: trackingState.driverInfo != null
                            ? AppColors.primary.withAlpha(30)
                            : Colors.grey.shade200,
                        child: Icon(
                          trackingState.driverInfo != null
                              ? Icons.person
                              : Icons.person_search_rounded,
                          color: trackingState.driverInfo != null
                              ? AppColors.primary
                              : Colors.grey.shade600,
                          size: 26,
                        ),
                      ),
                      const SizedBox(width: 12),

                      // Driver Name & Plate
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              trackingState.driverInfo?['full_name'] ??
                                  'Chưa gán tài xế',
                              style: TextStyle(
                                fontSize: 15,
                                fontWeight: FontWeight.bold,
                                color: trackingState.driverInfo != null
                                    ? AppColors.textPrimary
                                    : Colors.orange.shade800,
                              ),
                            ),
                            const SizedBox(height: 2),
                            Text(
                              trackingState.driverInfo != null
                                  ? '${trackingState.driverInfo!['license_plate'] ?? "Chưa rõ"} • ${trackingState.driverInfo!['vehicle_type'] ?? "Xe máy"}'
                                  : 'Đang chờ điều phối viên phân công',
                              style: const TextStyle(
                                fontSize: 12,
                                color: AppColors.textSecondary,
                              ),
                            ),
                          ],
                        ),
                      ),

                      // Call Button (only available when driver is assigned)
                      if (trackingState.driverInfo != null)
                        IconButton(
                          icon: const CircleAvatar(
                            radius: 18,
                            backgroundColor: AppColors.success,
                            child: Icon(
                              Icons.phone,
                              color: Colors.white,
                              size: 18,
                            ),
                          ),
                          onPressed: () {
                            ScaffoldMessenger.of(context).showSnackBar(
                              const SnackBar(
                                content: Text(
                                  'Đang kết nối cuộc gọi tới tài xế...',
                                ),
                                duration: Duration(seconds: 2),
                              ),
                            );
                          },
                        ),
                    ],
                  ),

                  const SizedBox(height: 16),

                  // ── Real Delivery Status Card ──
                  Container(
                    padding: const EdgeInsets.all(14),
                    decoration: BoxDecoration(
                      color: AppColors.surface,
                      borderRadius: BorderRadius.circular(14),
                      border: Border.all(color: AppColors.divider),
                    ),
                    child: Column(
                      children: [
                        Row(
                          children: [
                            Container(
                              padding: const EdgeInsets.all(8),
                              decoration: BoxDecoration(
                                color: (trackingState.isDelivered
                                        ? AppColors.success
                                        : AppColors.primary)
                                    .withAlpha(25),
                                shape: BoxShape.circle,
                              ),
                              child: Icon(
                                trackingState.isDelivered
                                    ? Icons.check_circle
                                    : Icons.two_wheeler_rounded,
                                color: trackingState.isDelivered
                                    ? AppColors.success
                                    : AppColors.primary,
                                size: 22,
                              ),
                            ),
                            const SizedBox(width: 12),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    trackingState.isDelivered
                                        ? 'Đơn hàng đã được giao thành công'
                                        : 'Tài xế đang giao đơn hàng đến bạn',
                                    style: const TextStyle(
                                      fontSize: 14,
                                      fontWeight: FontWeight.bold,
                                      color: AppColors.textPrimary,
                                    ),
                                  ),
                                  const SizedBox(height: 3),
                                  Text(
                                    trackingState.isDelivered
                                        ? 'Cảm ơn bạn đã đồng hành cùng SwiftRoute!'
                                        : 'Vị trí xe máy được cập nhật trực tiếp theo thời gian thực',
                                    style: const TextStyle(
                                      fontSize: 12,
                                      color: AppColors.textSecondary,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ],
                        ),
                        if (!trackingState.isDelivered) ...[
                          const SizedBox(height: 12),
                          LinearProgressIndicator(
                            value: trackingState.progressPercentage,
                            backgroundColor: Colors.grey.shade200,
                            color: AppColors.primary,
                            minHeight: 5,
                            borderRadius: BorderRadius.circular(3),
                          ),
                        ],
                      ],
                    ),
                  ),

                  if (trackingState.isDelivered) ...[
                    const SizedBox(height: 14),
                    SizedBox(
                      width: double.infinity,
                      child: ElevatedButton.icon(
                        onPressed: () {
                          ref.read(orderTrackingProvider.notifier).fetchActiveOrder();
                          Navigator.of(context).pushNamedAndRemoveUntil(
                            '/customer/home',
                            (route) => false,
                          );
                        },
                        icon: const Icon(Icons.home_rounded, size: 18),
                        label: const Text('Về trang chủ'),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: AppColors.primary,
                          foregroundColor: Colors.white,
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(12),
                          ),
                          padding: const EdgeInsets.symmetric(vertical: 12),
                        ),
                      ),
                    ),
                  ],
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
}
