// features/driver/presentation/screens/driver_route_tab.dart

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/theme/app_colors.dart';
import 'package:swiftroute_app/core/theme/app_theme.dart';
import 'package:swiftroute_app/core/widgets/field_ops_widgets.dart';
import 'package:swiftroute_app/core/widgets/slide_to_confirm_button.dart';
import 'package:swiftroute_app/features/driver/application/driver_provider.dart';
import 'package:swiftroute_app/features/driver/presentation/screens/driver_navigation_screen.dart';
import 'package:swiftroute_app/features/driver/presentation/widgets/driver_stop_card.dart';
import 'package:swiftroute_app/features/order/data/datasources/order_remote_data_source.dart';
import 'package:swiftroute_app/features/order/data/models/order_model.dart';

enum _RouteFilter { all, delivering, pickup, returns, done }

/// Today's route timeline (D05).
///
/// Layout, top to bottom: shift progress → 1-tap "start trip" banner →
/// grouped depot pickup cards (1 swipe per warehouse) → filter chips → stops.
class DriverRouteTab extends ConsumerStatefulWidget {
  const DriverRouteTab({super.key});

  @override
  ConsumerState<DriverRouteTab> createState() => _DriverRouteTabState();
}

class _DriverRouteTabState extends ConsumerState<DriverRouteTab> {
  _RouteFilter _filter = _RouteFilter.all;
  bool _isAccepting = false;

  Future<void> _refresh() =>
      ref.read(driverOrdersProvider.notifier).refreshOrders();

  Future<void> _acceptAll() async {
    setState(() => _isAccepting = true);
    try {
      await ref.read(driverOrdersProvider.notifier).acceptAllAssigned();
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          backgroundColor: AppColors.success,
          content: Text('Đã nhận chuyến. Di chuyển tới kho để lấy hàng.'),
        ),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          backgroundColor: AppColors.error,
          content: Text('Không nhận được chuyến: $e'),
        ),
      );
    } finally {
      if (mounted) setState(() => _isAccepting = false);
    }
  }

  Future<void> _confirmDepotPickup(List<OrderModel> group) async {
    try {
      await ref
          .read(driverOrdersProvider.notifier)
          .confirmBatchPickup(orderIds: group.map((o) => o.id).toList());
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          backgroundColor: AppColors.success,
          content: Text(
            'Đã lấy ${group.length} đơn tại ${group.first.pickupDisplayName}.',
          ),
        ),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          backgroundColor: AppColors.error,
          content: Text('Không xác nhận được lấy hàng: $e'),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final ordersState = ref.watch(driverOrdersProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Lộ trình hôm nay'),
        actions: [
          IconButton(
            tooltip: 'Báo sự cố toàn tuyến',
            onPressed: () => _RouteIncidentSheet.show(context),
            icon: const Icon(Icons.car_crash_outlined, color: AppColors.error),
          ),
          IconButton(
            tooltip: 'Tải lại lộ trình',
            onPressed: _refresh,
            icon: const Icon(Icons.refresh_rounded),
          ),
          const SizedBox(width: 4),
        ],
      ),
      body: ordersState.when(
        loading: () => ListView(
          padding: const EdgeInsets.all(16),
          children: const [
            ShimmerBox(height: 84),
            SizedBox(height: 12),
            ShimmerBox(height: 150),
            SizedBox(height: 12),
            ShimmerBox(height: 150),
          ],
        ),
        error: (_, _) => ListView(
          padding: const EdgeInsets.all(16),
          children: [
            EmptyStateCard(
              icon: Icons.cloud_off_rounded,
              title: 'Không tải được lộ trình',
              message: 'Kiểm tra kết nối mạng rồi thử lại.',
              actionLabel: 'Thử lại',
              onAction: _refresh,
            ),
          ],
        ),
        data: (allStops) {
          if (allStops.isEmpty) {
            return RefreshIndicator(
              onRefresh: _refresh,
              child: ListView(
                physics: const AlwaysScrollableScrollPhysics(),
                padding: const EdgeInsets.all(16),
                children: const [
                  SizedBox(height: 40),
                  EmptyStateCard(
                    icon: Icons.route_outlined,
                    title: 'Chưa có điểm dừng nào',
                    message:
                        'Khi điều phối phân công đơn, lộ trình sẽ hiển thị tại đây.',
                  ),
                ],
              ),
            );
          }
          return _buildRoute(allStops);
        },
      ),
    );
  }

  Widget _buildRoute(List<OrderModel> allStops) {
    bool isClosed(OrderModel o) =>
        o.deliveryTaskStatus == DeliveryTaskStatus.completed ||
        o.deliveryTaskStatus == DeliveryTaskStatus.failed ||
        o.deliveryTaskStatus == DeliveryTaskStatus.cancelled;

    final pendingPickups = allStops
        .where((o) => o.needsPickup && !isClosed(o))
        .toList();
    final assignedCount = allStops
        .where((o) => o.deliveryTaskStatus == DeliveryTaskStatus.assigned)
        .length;
    final doneCount = allStops
        .where((o) => o.deliveryTaskStatus == DeliveryTaskStatus.completed)
        .length;

    // Group pending pickups by warehouse so the driver confirms once per depot.
    final depotGroups = <String, List<OrderModel>>{};
    for (final order in pendingPickups) {
      depotGroups.putIfAbsent(order.pickupGroupKey, () => []).add(order);
    }

    final counts = {
      _RouteFilter.all: allStops.length,
      _RouteFilter.delivering: allStops
          .where((o) => o.isOutForDelivery && !isClosed(o))
          .length,
      _RouteFilter.pickup: pendingPickups.length,
      _RouteFilter.returns: allStops.where((o) => o.isReturnTask).length,
      _RouteFilter.done: allStops.where(isClosed).length,
    };

    final filtered = allStops.where((o) {
      return switch (_filter) {
        _RouteFilter.all => true,
        _RouteFilter.delivering => o.isOutForDelivery && !isClosed(o),
        _RouteFilter.pickup => o.needsPickup && !isClosed(o),
        _RouteFilter.returns => o.isReturnTask,
        _RouteFilter.done => isClosed(o),
      };
    }).toList();

    return RefreshIndicator(
      onRefresh: _refresh,
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(16, 14, 16, 32),
        children: [
          _ProgressCard(done: doneCount, total: allStops.length),
          if (assignedCount > 0) ...[
            const SizedBox(height: 12),
            _StartTripBanner(
              count: assignedCount,
              isLoading: _isAccepting,
              onStart: _acceptAll,
            ),
          ],
          for (final group in depotGroups.values) ...[
            const SizedBox(height: 12),
            _DepotPickupCard(
              group: group,
              onConfirm: () => _confirmDepotPickup(group),
            ),
          ],
          const SizedBox(height: 16),
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: Row(
              children: [
                for (final f in _RouteFilter.values) ...[
                  ChoiceChip(
                    label: Text('${_filterLabel(f)} (${counts[f]})'),
                    selected: _filter == f,
                    onSelected: (_) => setState(() => _filter = f),
                    showCheckmark: false,
                    selectedColor: AppColors.primary,
                    backgroundColor: AppColors.card,
                    side: BorderSide(
                      color: _filter == f
                          ? AppColors.primary
                          : AppColors.divider,
                    ),
                    labelStyle: AppTheme.numStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.w700,
                      color: _filter == f
                          ? Colors.white
                          : AppColors.textSecondary,
                    ),
                  ),
                  const SizedBox(width: 8),
                ],
              ],
            ),
          ),
          const SizedBox(height: 12),
          if (filtered.isEmpty)
            const EmptyStateCard(
              icon: Icons.filter_alt_off_outlined,
              title: 'Không có đơn ở mục này',
              message: 'Chọn bộ lọc khác để xem các điểm dừng còn lại.',
            )
          else
            for (final stop in filtered) ...[
              DriverStopCard(stop: stop, sequence: allStops.indexOf(stop) + 1),
              const SizedBox(height: 10),
            ],
        ],
      ),
    );
  }

  String _filterLabel(_RouteFilter f) => switch (f) {
    _RouteFilter.all => 'Tất cả',
    _RouteFilter.delivering => 'Đang giao',
    _RouteFilter.pickup => 'Chờ lấy',
    _RouteFilter.returns => 'Hoàn hàng',
    _RouteFilter.done => 'Đã xong',
  };
}

class _ProgressCard extends StatelessWidget {
  const _ProgressCard({required this.done, required this.total});

  final int done;
  final int total;

  @override
  Widget build(BuildContext context) {
    final ratio = total == 0 ? 0.0 : done / total;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Expanded(
                  child: Text(
                    'Tiến độ ca giao',
                    style: TextStyle(fontSize: 15, fontWeight: FontWeight.w800),
                  ),
                ),
                Text(
                  '$done/$total điểm',
                  style: AppTheme.numStyle(
                    fontSize: 15,
                    fontWeight: FontWeight.w800,
                    color: AppColors.primary,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 10),
            ClipRRect(
              borderRadius: BorderRadius.circular(99),
              child: LinearProgressIndicator(
                value: ratio,
                minHeight: 8,
                backgroundColor: AppColors.surfaceVariant,
                color: AppColors.success,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _StartTripBanner extends StatelessWidget {
  const _StartTripBanner({
    required this.count,
    required this.isLoading,
    required this.onStart,
  });

  final int count;
  final bool isLoading;
  final VoidCallback onStart;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppColors.primarySoft,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: AppColors.primaryBorder),
      ),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '$count đơn mới được giao',
                  style: AppTheme.numStyle(
                    fontSize: 15,
                    fontWeight: FontWeight.w800,
                    color: AppColors.primaryDark,
                  ),
                ),
                const SizedBox(height: 2),
                const Text(
                  'Nhận một lần cho cả chuyến.',
                  style: TextStyle(
                    fontSize: 13,
                    color: AppColors.textSecondary,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(width: 10),
          FilledButton.icon(
            onPressed: isLoading ? null : onStart,
            icon: isLoading
                ? const SizedBox(
                    width: 16,
                    height: 16,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      color: Colors.white,
                    ),
                  )
                : const Icon(Icons.play_arrow_rounded, size: 20),
            label: const Text('Bắt đầu chuyến'),
          ),
        ],
      ),
    );
  }
}

/// One card per warehouse: confirming the swipe acquires custody for every
/// order in the group and moves them straight to OUT_FOR_DELIVERY.
class _DepotPickupCard extends StatelessWidget {
  const _DepotPickupCard({required this.group, required this.onConfirm});

  final List<OrderModel> group;
  final Future<void> Function() onConfirm;

  @override
  Widget build(BuildContext context) {
    final first = group.first;
    final packages = group.fold<int>(0, (sum, o) => sum + o.packageCount);
    final weight = group.fold<double>(0, (sum, o) => sum + o.weightKg);

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Container(
                  width: 40,
                  height: 40,
                  decoration: BoxDecoration(
                    color: AppColors.warningSoft,
                    borderRadius: BorderRadius.circular(11),
                  ),
                  child: const Icon(
                    Icons.warehouse_rounded,
                    color: AppColors.warning,
                    size: 22,
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        first.pickupDisplayName,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                      Text(
                        first.pickupAddress,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          fontSize: 13,
                          color: AppColors.textSecondary,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                StatusPill(
                  label:
                      '${group.length} đơn · $packages kiện · ${weight.toStringAsFixed(1)} kg',
                  foreground: AppColors.warning,
                  background: AppColors.warningSoft,
                  borderColor: AppColors.warningBorder,
                  icon: Icons.inventory_2_outlined,
                ),
                const Spacer(),
                PhoneCallButton(phone: first.senderPhone, compact: true),
                const SizedBox(width: 8),
                SizedBox(
                  width: 48,
                  height: 48,
                  child: IconButton.outlined(
                    tooltip: 'Dẫn đường tới kho',
                    onPressed: () => Navigator.of(context).push(
                      MaterialPageRoute(
                        builder: (_) => DriverNavigationScreen(order: first),
                      ),
                    ),
                    style: IconButton.styleFrom(
                      side: const BorderSide(color: AppColors.divider),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(12),
                      ),
                    ),
                    icon: const Icon(
                      Icons.navigation_rounded,
                      size: 20,
                      color: AppColors.primary,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            SlideToConfirmButton(
              label: 'Vuốt đã lấy $packages kiện tại kho này',
              backgroundColor: AppColors.accent,
              icon: Icons.inventory_2_rounded,
              onConfirmed: onConfirm,
            ),
          ],
        ),
      ),
    );
  }
}

/// Route-level emergency report (vehicle breakdown, flooding, health issue).
class _RouteIncidentSheet extends ConsumerStatefulWidget {
  const _RouteIncidentSheet();

  static Future<void> show(BuildContext context) {
    return showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (_) => const _RouteIncidentSheet(),
    );
  }

  @override
  ConsumerState<_RouteIncidentSheet> createState() =>
      _RouteIncidentSheetState();
}

class _RouteIncidentSheetState extends ConsumerState<_RouteIncidentSheet> {
  static const _options = [
    (
      'VEHICLE_BREAKDOWN',
      'Hỏng xe / Không thể di chuyển',
      Icons.car_crash_rounded,
    ),
    ('TRAFFIC_CONGESTION', 'Kẹt xe / Ngập nghiêm trọng', Icons.traffic_rounded),
    (
      'DRIVER_EMERGENCY',
      'Sức khỏe / Khẩn cấp cá nhân',
      Icons.medical_services_outlined,
    ),
    ('OTHER', 'Sự cố khác', Icons.warning_amber_rounded),
  ];

  String _type = 'VEHICLE_BREAKDOWN';
  final _notes = TextEditingController();
  bool _submitting = false;

  @override
  void dispose() {
    _notes.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    setState(() => _submitting = true);
    try {
      await ref
          .read(orderRemoteDataSourceProvider)
          .reportOperationalIncident(
            incidentType: _type,
            severity: 'HIGH',
            notes: _notes.text.trim().isEmpty ? null : _notes.text.trim(),
            canContinueDelivery: false,
            needsImmediateAssistance: true,
          );
      if (!mounted) return;
      Navigator.of(context).pop();
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          backgroundColor: AppColors.error,
          content: Text('Đã gửi báo cáo khẩn cấp tới điều phối.'),
        ),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text('Gửi thất bại: $e')));
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final bottom =
        MediaQuery.viewInsetsOf(context).bottom +
        MediaQuery.paddingOf(context).bottom;
    return Padding(
      padding: EdgeInsets.fromLTRB(20, 16, 20, 20 + bottom),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'Báo sự cố khẩn cấp',
            style: TextStyle(fontSize: 19, fontWeight: FontWeight.w800),
          ),
          const SizedBox(height: 4),
          const Text(
            'Điều phối sẽ được báo động và hỗ trợ cả tuyến.',
            style: TextStyle(fontSize: 13.5, color: AppColors.textSecondary),
          ),
          const SizedBox(height: 14),
          for (final (code, label, icon) in _options)
            Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: _OptionTile(
                label: label,
                icon: icon,
                selected: _type == code,
                onTap: () => setState(() => _type = code),
              ),
            ),
          const SizedBox(height: 4),
          TextField(
            controller: _notes,
            maxLines: 2,
            decoration: const InputDecoration(
              labelText: 'Chi tiết (tuỳ chọn)',
              hintText: 'VD: Nổ lốp tại đường 3/2…',
            ),
          ),
          const SizedBox(height: 16),
          SizedBox(
            width: double.infinity,
            height: 52,
            child: FilledButton.icon(
              onPressed: _submitting ? null : _submit,
              style: FilledButton.styleFrom(backgroundColor: AppColors.error),
              icon: _submitting
                  ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(
                        strokeWidth: 2,
                        color: Colors.white,
                      ),
                    )
                  : const Icon(Icons.campaign_rounded),
              label: const Text('Gửi khẩn cấp'),
            ),
          ),
        ],
      ),
    );
  }
}

class _OptionTile extends StatelessWidget {
  const _OptionTile({
    required this.label,
    required this.icon,
    required this.selected,
    required this.onTap,
  });

  final String label;
  final IconData icon;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(12),
      child: Container(
        constraints: const BoxConstraints(minHeight: 52),
        padding: const EdgeInsets.symmetric(horizontal: 14),
        decoration: BoxDecoration(
          color: selected ? AppColors.errorSoft : AppColors.card,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
            color: selected ? AppColors.error : AppColors.divider,
          ),
        ),
        child: Row(
          children: [
            Icon(
              icon,
              size: 21,
              color: selected ? AppColors.error : AppColors.textSecondary,
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                label,
                style: TextStyle(
                  fontSize: 14,
                  fontWeight: selected ? FontWeight.w700 : FontWeight.w500,
                  color: AppColors.textPrimary,
                ),
              ),
            ),
            Icon(
              selected
                  ? Icons.radio_button_checked_rounded
                  : Icons.radio_button_off_rounded,
              size: 20,
              color: selected ? AppColors.error : AppColors.textHint,
            ),
          ],
        ),
      ),
    );
  }
}
