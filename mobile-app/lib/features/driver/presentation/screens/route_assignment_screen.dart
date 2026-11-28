import 'package:flutter/material.dart';
import 'package:swiftroute_app/core/theme/app_colors.dart';
import 'package:swiftroute_app/core/theme/app_theme.dart';
import 'package:swiftroute_app/features/driver/data/models/dispatch_plan_model.dart';
import 'package:swiftroute_app/features/order/data/models/order_model.dart';

/// Read-only summary of a route assigned by dispatch.
class RouteAssignmentScreen extends StatelessWidget {
  const RouteAssignmentScreen({
    super.key,
    required this.plan,
    required this.orders,
  });

  final DispatchPlanModel plan;
  final List<OrderModel> orders;

  @override
  Widget build(BuildContext context) {
    final planOrders = plan.orderedStopIds
        .map((id) => orders.where((o) => o.id == id).firstOrNull)
        .whereType<OrderModel>()
        .toList();
    final displayOrders = planOrders.isNotEmpty ? planOrders : orders;
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: Text(
          'Lộ trình #${plan.planId.length > 8 ? plan.planId.substring(0, 8) : plan.planId}',
          style: const TextStyle(
            fontSize: 16,
            fontWeight: FontWeight.bold,
            color: AppColors.textPrimary,
          ),
        ),
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: AppColors.card,
              borderRadius: BorderRadius.circular(14),
              border: Border.all(color: AppColors.divider),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    const Text(
                      'Tóm tắt lộ trình',
                      style: TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w800,
                        color: AppColors.textPrimary,
                      ),
                    ),
                    const Text(
                      'Đã phân công',
                      style: TextStyle(
                        fontSize: 11,
                        fontWeight: FontWeight.bold,
                        color: AppColors.success,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 14),
                Row(
                  children: [
                    Expanded(
                      child: _MetricColumn(
                        label: 'Số điểm giao',
                        value: '${plan.orderCount} điểm',
                      ),
                    ),
                    Expanded(
                      child: _MetricColumn(
                        label: 'Tổng quãng đường',
                        value: '${plan.totalDistanceKm.toStringAsFixed(1)} km',
                      ),
                    ),
                    Expanded(
                      child: _MetricColumn(
                        label: 'Thời gian dự kiến',
                        value: '${plan.estimatedDurationMinutes} phút',
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),
          const Text(
            'Thứ tự điểm giao',
            style: TextStyle(
              fontSize: 16,
              fontWeight: FontWeight.w800,
              color: AppColors.textPrimary,
            ),
          ),
          const SizedBox(height: 8),
          if (displayOrders.isEmpty)
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: AppColors.card,
                borderRadius: BorderRadius.circular(14),
                border: Border.all(color: AppColors.divider),
              ),
              child: Text(
                'Lộ trình gồm ${plan.orderCount} đơn hàng. Danh sách điểm giao chưa đồng bộ.',
                style: const TextStyle(
                  fontSize: 13,
                  color: AppColors.textSecondary,
                ),
              ),
            )
          else
            ...displayOrders.asMap().entries.map((entry) {
              final idx = entry.key + 1;
              final order = entry.value;
              return Container(
                margin: const EdgeInsets.only(bottom: 10),
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: AppColors.card,
                  borderRadius: BorderRadius.circular(14),
                  border: Border.all(color: AppColors.divider),
                ),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Container(
                      width: 26,
                      height: 26,
                      alignment: Alignment.center,
                      decoration: const BoxDecoration(
                        color: AppColors.primary,
                        shape: BoxShape.circle,
                      ),
                      child: Text(
                        '$idx',
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 12,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            order.deliveryAddress,
                            style: const TextStyle(
                              fontSize: 13,
                              fontWeight: FontWeight.w600,
                              color: AppColors.textPrimary,
                            ),
                          ),
                          const SizedBox(height: 4),
                          Text(
                            'Lấy tại: ${order.pickupAddress} · ${order.weightKg.toStringAsFixed(1)} kg',
                            style: const TextStyle(
                              fontSize: 11,
                              color: AppColors.textSecondary,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              );
            }),
        ],
      ),
    );
  }
}

class _MetricColumn extends StatelessWidget {
  const _MetricColumn({required this.label, required this.value});
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label,
          style: const TextStyle(fontSize: 11, color: AppColors.textSecondary),
        ),
        const SizedBox(height: 4),
        Text(
          value,
          style: AppTheme.numStyle(fontSize: 15, fontWeight: FontWeight.w800),
        ),
      ],
    );
  }
}
