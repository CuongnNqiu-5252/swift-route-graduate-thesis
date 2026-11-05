// features/driver/presentation/screens/driver_cod_handover_tab.dart

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/theme/app_colors.dart';
import 'package:swiftroute_app/core/theme/app_theme.dart';
import 'package:swiftroute_app/core/utils/formatters.dart';
import 'package:swiftroute_app/core/widgets/field_ops_widgets.dart';
import 'package:swiftroute_app/core/widgets/slide_to_confirm_button.dart';
import 'package:swiftroute_app/features/driver/application/driver_provider.dart';
import 'package:swiftroute_app/features/driver/data/models/driver_cod_collection_model.dart';
import 'package:swiftroute_app/features/driver/data/models/package_handover_model.dart';

/// End-of-shift handover tab: COD cash declaration and driver-to-driver
/// package custody transfers. Irreversible confirmations use slide-to-confirm.
class DriverCodHandoverTab extends ConsumerWidget {
  const DriverCodHandoverTab({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final codState = ref.watch(driverCodHandoverProvider);
    final packageState = ref.watch(packageHandoverProvider);

    Future<void> refreshAll() async {
      await Future.wait([
        ref.read(driverCodHandoverProvider.notifier).refresh(),
        ref.read(packageHandoverProvider.notifier).refresh(),
      ]);
    }

    return DefaultTabController(
      length: 2,
      child: Scaffold(
        appBar: AppBar(
          title: const Text('Bàn giao'),
          shape: const Border(),
          actions: [
            IconButton(
              tooltip: 'Làm mới',
              onPressed: refreshAll,
              icon: const Icon(Icons.refresh_rounded),
            ),
            const SizedBox(width: 4),
          ],
          bottom: const TabBar(
            labelColor: AppColors.primary,
            unselectedLabelColor: AppColors.textSecondary,
            indicatorColor: AppColors.primary,
            labelStyle: TextStyle(fontSize: 14.5, fontWeight: FontWeight.w800),
            tabs: [
              Tab(text: 'Tiền COD'),
              Tab(text: 'Kiện hàng'),
            ],
          ),
        ),
        body: TabBarView(
          children: [
            _CodList(state: codState),
            _PackageList(state: packageState),
          ],
        ),
      ),
    );
  }
}

class _CodList extends ConsumerWidget {
  const _CodList({required this.state});

  final AsyncValue<List<DriverCodCollectionModel>> state;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final notifier = ref.read(driverCodHandoverProvider.notifier);
    return state.when(
      loading: () => ListView(
        padding: const EdgeInsets.all(16),
        children: const [
          ShimmerBox(height: 96),
          SizedBox(height: 12),
          ShimmerBox(height: 140),
        ],
      ),
      error: (_, _) => ListView(
        padding: const EdgeInsets.all(16),
        children: [
          EmptyStateCard(
            icon: Icons.cloud_off_rounded,
            title: 'Không tải được tiền COD',
            message: 'Kiểm tra kết nối rồi thử lại.',
            actionLabel: 'Thử lại',
            onAction: notifier.refresh,
          ),
        ],
      ),
      data: (collections) {
        final total = collections.fold<double>(0, (s, c) => s + c.amount);
        return RefreshIndicator(
          onRefresh: notifier.refresh,
          child: ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
            children: [
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: AppColors.warningSoft,
                  borderRadius: BorderRadius.circular(14),
                  border: Border.all(color: AppColors.warningBorder),
                ),
                child: Row(
                  children: [
                    const Icon(
                      Icons.payments_rounded,
                      color: AppColors.warning,
                      size: 26,
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Text(
                            'Tiền mặt đang giữ',
                            style: TextStyle(
                              fontSize: 13.5,
                              fontWeight: FontWeight.w600,
                              color: AppColors.warning,
                            ),
                          ),
                          Text(
                            AppFormatters.currency(total),
                            style: AppTheme.numStyle(
                              fontSize: 24,
                              fontWeight: FontWeight.w800,
                              color: AppColors.textPrimary,
                            ),
                          ),
                        ],
                      ),
                    ),
                    Text(
                      '${collections.length} đơn',
                      style: AppTheme.numStyle(
                        fontSize: 14,
                        color: AppColors.warning,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 14),
              if (collections.isEmpty)
                const EmptyStateCard(
                  icon: Icons.task_alt_rounded,
                  title: 'Không có COD chờ bàn giao',
                  message:
                      'Tiền thu hộ sẽ xuất hiện sau mỗi đơn giao thành công.',
                )
              else
                for (final c in collections) ...[
                  _CodItem(collection: c),
                  const SizedBox(height: 10),
                ],
            ],
          ),
        );
      },
    );
  }
}

class _CodItem extends ConsumerWidget {
  const _CodItem({required this.collection});

  final DriverCodCollectionModel collection;

  Future<void> _declare(BuildContext context, WidgetRef ref) async {
    try {
      await ref.read(driverCodHandoverProvider.notifier).declare(collection);
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            backgroundColor: AppColors.success,
            content: Text('Đã khai báo nộp COD. Chờ điều phối xác minh.'),
          ),
        );
      }
    } catch (_) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Không khai báo được COD. Thử lại.')),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final code =
        collection.orderCode ??
        AppFormatters.shortCode(null, collection.orderId);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    code,
                    style: AppTheme.numStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.w700,
                      color: AppColors.textSecondary,
                    ),
                  ),
                ),
                Text(
                  AppFormatters.currency(collection.amount),
                  style: AppTheme.numStyle(
                    fontSize: 19,
                    fontWeight: FontWeight.w800,
                  ),
                ),
              ],
            ),
            if (collection.notes?.isNotEmpty == true) ...[
              const SizedBox(height: 4),
              Text(
                collection.notes!,
                style: const TextStyle(
                  fontSize: 13,
                  color: AppColors.textSecondary,
                ),
              ),
            ],
            const SizedBox(height: 12),
            SlideToConfirmButton(
              label: 'Vuốt xác nhận đã nộp tiền',
              icon: Icons.payments_rounded,
              onConfirmed: () => _declare(context, ref),
            ),
          ],
        ),
      ),
    );
  }
}

class _PackageList extends ConsumerWidget {
  const _PackageList({required this.state});

  final AsyncValue<List<PackageHandoverModel>> state;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final notifier = ref.read(packageHandoverProvider.notifier);
    return state.when(
      loading: () => ListView(
        padding: const EdgeInsets.all(16),
        children: const [ShimmerBox(height: 140)],
      ),
      error: (_, _) => ListView(
        padding: const EdgeInsets.all(16),
        children: [
          EmptyStateCard(
            icon: Icons.cloud_off_rounded,
            title: 'Không tải được bàn giao kiện',
            message: 'Kiểm tra kết nối rồi thử lại.',
            actionLabel: 'Thử lại',
            onAction: notifier.refresh,
          ),
        ],
      ),
      data: (handovers) => RefreshIndicator(
        onRefresh: notifier.refresh,
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
          children: [
            if (handovers.isEmpty)
              const EmptyStateCard(
                icon: Icons.swap_horiz_rounded,
                title: 'Không có bàn giao kiện đang chờ',
                message:
                    'Khi bạn chuyển kiện cho tài xế khác, hai bên sẽ xác nhận tại đây.',
              )
            else
              for (final h in handovers) ...[
                _PackageItem(handover: h),
                const SizedBox(height: 10),
              ],
          ],
        ),
      ),
    );
  }
}

class _PackageItem extends ConsumerWidget {
  const _PackageItem({required this.handover});

  final PackageHandoverModel handover;

  Future<void> _act(BuildContext context, WidgetRef ref, String action) async {
    try {
      await ref.read(packageHandoverProvider.notifier).act(handover, action);
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            backgroundColor: AppColors.success,
            content: Text('Đã xác nhận bàn giao kiện hàng.'),
          ),
        );
      }
    } catch (_) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Không xác nhận được bàn giao. Tải lại và thử lại.'),
          ),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final me = ref.watch(driverProvider).valueOrNull?.userId;
    final outgoing = me == handover.fromDriverId;
    final canConfirm =
        (outgoing && handover.status == 'REQUESTED') ||
        (!outgoing && handover.status == 'SENDER_CONFIRMED');
    final action = outgoing ? 'sender-confirm' : 'receiver-confirm';

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(
                  outgoing
                      ? Icons.north_east_rounded
                      : Icons.south_west_rounded,
                  color: AppColors.primary,
                  size: 20,
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    outgoing ? 'Bàn giao đi' : 'Nhận bàn giao',
                    style: const TextStyle(
                      fontSize: 15.5,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ),
                StatusPill(
                  label: '${handover.packageCodes.length} kiện',
                  foreground: AppColors.primaryDark,
                  background: AppColors.primarySoft,
                ),
              ],
            ),
            const SizedBox(height: 8),
            Text(
              handover.packageCodes.join(' · '),
              style: AppTheme.numStyle(
                fontSize: 13,
                fontWeight: FontWeight.w600,
                color: AppColors.textSecondary,
              ),
            ),
            if (handover.reason.isNotEmpty) ...[
              const SizedBox(height: 4),
              Text(handover.reason, style: const TextStyle(fontSize: 13.5)),
            ],
            const SizedBox(height: 12),
            if (canConfirm)
              SlideToConfirmButton(
                label: outgoing
                    ? 'Vuốt xác nhận đã giao kiện'
                    : 'Vuốt xác nhận đã nhận kiện',
                icon: Icons.verified_rounded,
                onConfirmed: () => _act(context, ref, action),
              )
            else
              const Text(
                'Đang chờ tài xế bên kia xác nhận.',
                style: TextStyle(fontSize: 13, color: AppColors.textSecondary),
              ),
          ],
        ),
      ),
    );
  }
}
