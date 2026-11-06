import 'package:flutter/material.dart';
import 'package:swiftroute_app/core/theme/app_colors.dart';
import 'package:swiftroute_app/features/driver/data/models/dispatch_plan_model.dart';

/// Non-intrusive route update banner displayed when an active dispatch plan
/// has its remaining stops dynamically reoptimized or a new stop inserted.
class RouteUpdateBanner extends StatelessWidget {
  const RouteUpdateBanner({
    super.key,
    required this.plan,
    required this.onRefreshRoute,
    required this.onDismiss,
  });

  final DispatchPlanModel plan;
  final VoidCallback onRefreshRoute;
  final VoidCallback onDismiss;

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 14),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Colors.amber.shade50,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.amber.shade300),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            padding: const EdgeInsets.all(8),
            decoration: BoxDecoration(
              color: Colors.amber.shade100,
              borderRadius: BorderRadius.circular(8),
            ),
            child: Icon(
              Icons.alt_route_rounded,
              color: Colors.amber.shade900,
              size: 20,
            ),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Lộ trình vừa được cập nhật động',
                  style: TextStyle(
                    fontWeight: FontWeight.bold,
                    fontSize: 13,
                    color: Colors.amber.shade900,
                  ),
                ),
                const SizedBox(height: 3),
                Text(
                  'Thứ tự các chặng còn lại đã được OR-Tools tối ưu lại (${plan.orderCount} điểm, ${plan.totalDistanceKm.toStringAsFixed(1)} km). Chặng hiện tại của bạn được giữ nguyên.',
                  style: TextStyle(
                    fontSize: 11.5,
                    color: Colors.amber.shade900.withValues(alpha: 0.85),
                    height: 1.35,
                  ),
                ),
                const SizedBox(height: 8),
                Row(
                  children: [
                    SizedBox(
                      height: 30,
                      child: FilledButton.icon(
                        style: FilledButton.styleFrom(
                          backgroundColor: AppColors.primary,
                          padding: const EdgeInsets.symmetric(horizontal: 12),
                          textStyle: const TextStyle(
                            fontSize: 11.5,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        onPressed: onRefreshRoute,
                        icon: const Icon(Icons.sync_rounded, size: 14),
                        label: const Text('Đồng bộ lộ trình mới'),
                      ),
                    ),
                    const SizedBox(width: 8),
                    TextButton(
                      style: TextButton.styleFrom(
                        padding: const EdgeInsets.symmetric(horizontal: 8),
                        minimumSize: const Size(0, 30),
                        tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                      ),
                      onPressed: onDismiss,
                      child: Text(
                        'Đã hiểu',
                        style: TextStyle(
                          fontSize: 11.5,
                          fontWeight: FontWeight.w600,
                          color: Colors.amber.shade900,
                        ),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
