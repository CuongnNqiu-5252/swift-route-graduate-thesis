import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/theme/app_colors.dart';
import 'package:swiftroute_app/core/theme/app_theme.dart';
import 'package:swiftroute_app/core/utils/formatters.dart';
import 'package:swiftroute_app/features/driver/application/driver_provider.dart';
import 'package:swiftroute_app/features/driver/data/models/driver_earning_model.dart';

class DriverEarningsTab extends ConsumerWidget {
  const DriverEarningsTab({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(driverEarningsProvider);
    return Scaffold(
      appBar: AppBar(
        title: const Text('Thu nhập'),
        actions: [
          IconButton(
            tooltip: 'Làm mới',
            onPressed: () =>
                ref.read(driverEarningsProvider.notifier).refresh(),
            icon: const Icon(Icons.refresh_rounded),
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () => ref.read(driverEarningsProvider.notifier).refresh(),
        child: state.when(
          loading: () => const _EarningsSkeleton(),
          error: (error, _) => _EarningsError(
            onRetry: () => ref.read(driverEarningsProvider.notifier).refresh(),
          ),
          data: (bundle) => _EarningsContent(bundle: bundle),
        ),
      ),
    );
  }
}

class _EarningsContent extends StatelessWidget {
  const _EarningsContent({required this.bundle});
  final DriverEarningsBundle bundle;

  @override
  Widget build(BuildContext context) {
    final summary = bundle.summary;
    return ListView(
      physics: const AlwaysScrollableScrollPhysics(),
      padding: const EdgeInsets.fromLTRB(16, 4, 16, 32),
      children: [
        Container(
          padding: const EdgeInsets.all(20),
          decoration: BoxDecoration(
            color: AppColors.primaryDark,
            borderRadius: BorderRadius.circular(16),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'Tổng thu nhập tháng ${summary.periodStart.month}',
                style: TextStyle(
                  color: Colors.white.withValues(alpha: 0.82),
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                ),
              ),
              const SizedBox(height: 5),
              Text(
                _currency(summary.totalAmount),
                style: AppTheme.numStyle(
                  color: Colors.white,
                  fontSize: 30,
                  height: 1.15,
                  fontWeight: FontWeight.w800,
                ),
              ),
              const SizedBox(height: 6),
              Text(
                '${summary.deliveryCount} nhiệm vụ đã ghi nhận · ${summary.currency}',
                style: TextStyle(
                  color: Colors.white.withValues(alpha: 0.8),
                  fontSize: 12,
                ),
              ),
              const SizedBox(height: 18),
              Row(
                children: [
                  Expanded(
                    child: _SummaryAmount(
                      label: 'Chờ duyệt',
                      amount: summary.pendingAmount,
                    ),
                  ),
                  Container(
                    width: 1,
                    height: 34,
                    color: Colors.white.withValues(alpha: 0.16),
                  ),
                  Expanded(
                    child: _SummaryAmount(
                      label: 'Đã duyệt',
                      amount: summary.approvedAmount,
                    ),
                  ),
                  Container(
                    width: 1,
                    height: 34,
                    color: Colors.white.withValues(alpha: 0.16),
                  ),
                  Expanded(
                    child: _SummaryAmount(
                      label: 'Đã trả',
                      amount: summary.paidAmount,
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
        const SizedBox(height: 22),
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            const Text(
              'Chi tiết nhiệm vụ',
              style: TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.w800,
                color: AppColors.textPrimary,
              ),
            ),
            Text(
              '${bundle.earnings.length} khoản',
              style: const TextStyle(
                fontSize: 12,
                color: AppColors.textSecondary,
              ),
            ),
          ],
        ),
        const SizedBox(height: 10),
        if (bundle.earnings.isEmpty)
          const _EmptyEarnings()
        else
          ...bundle.earnings.map(_EarningRow.new),
      ],
    );
  }
}

class _SummaryAmount extends StatelessWidget {
  const _SummaryAmount({required this.label, required this.amount});
  final String label;
  final double amount;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 9),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            label,
            style: TextStyle(
              color: Colors.white.withValues(alpha: 0.78),
              fontSize: 11.5,
            ),
          ),
          const SizedBox(height: 3),
          Text(
            _compactCurrency(amount),
            style: AppTheme.numStyle(
              color: Colors.white,
              fontSize: 14,
              fontWeight: FontWeight.w800,
            ),
          ),
        ],
      ),
    );
  }
}

class _EarningRow extends StatelessWidget {
  const _EarningRow(this.earning);
  final DriverEarningModel earning;

  @override
  Widget build(BuildContext context) {
    final meta = _statusMeta(earning.status);
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 15),
      decoration: const BoxDecoration(
        border: Border(bottom: BorderSide(color: AppColors.divider)),
      ),
      child: Row(
        children: [
          Container(
            width: 42,
            height: 42,
            decoration: BoxDecoration(
              color: meta.color.withValues(alpha: 0.1),
              borderRadius: BorderRadius.circular(12),
            ),
            child: Icon(meta.icon, color: meta.color, size: 21),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  AppFormatters.shortCode(null, earning.orderId),
                  style: const TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  '${earning.distanceKm.toStringAsFixed(1)} km · ${_date(earning.completedAt)}',
                  style: const TextStyle(
                    fontSize: 12,
                    color: AppColors.textSecondary,
                  ),
                ),
              ],
            ),
          ),
          Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Text(
                _currency(earning.netAmount),
                style: AppTheme.numStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.w800,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                meta.label,
                style: TextStyle(
                  fontSize: 11,
                  fontWeight: FontWeight.w700,
                  color: meta.color,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _EmptyEarnings extends StatelessWidget {
  const _EmptyEarnings();
  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(vertical: 42, horizontal: 24),
    child: const Column(
      children: [
        Icon(Icons.receipt_long_outlined, size: 40, color: AppColors.textHint),
        SizedBox(height: 12),
        Text(
          'Chưa có thu nhập được ghi nhận',
          style: TextStyle(fontWeight: FontWeight.w700),
        ),
        SizedBox(height: 5),
        Text(
          'Khoản thu nhập sẽ xuất hiện sau khi một nhiệm vụ giao hàng được hoàn tất.',
          textAlign: TextAlign.center,
          style: TextStyle(
            fontSize: 12,
            height: 1.4,
            color: AppColors.textSecondary,
          ),
        ),
      ],
    ),
  );
}

class _EarningsSkeleton extends StatelessWidget {
  const _EarningsSkeleton();
  @override
  Widget build(BuildContext context) => ListView(
    padding: const EdgeInsets.all(16),
    children: [
      Container(
        height: 190,
        decoration: BoxDecoration(
          color: AppColors.surfaceVariant,
          borderRadius: BorderRadius.circular(16),
        ),
      ),
      const SizedBox(height: 22),
      for (var i = 0; i < 4; i++) ...[
        Container(
          height: 68,
          decoration: const BoxDecoration(
            border: Border(bottom: BorderSide(color: AppColors.divider)),
          ),
        ),
      ],
    ],
  );
}

class _EarningsError extends StatelessWidget {
  const _EarningsError({required this.onRetry});
  final VoidCallback onRetry;
  @override
  Widget build(BuildContext context) => ListView(
    physics: const AlwaysScrollableScrollPhysics(),
    children: [
      const SizedBox(height: 140),
      const Icon(
        Icons.cloud_off_outlined,
        size: 42,
        color: AppColors.textSecondary,
      ),
      const SizedBox(height: 12),
      const Text(
        'Không tải được dữ liệu thu nhập',
        textAlign: TextAlign.center,
        style: TextStyle(fontWeight: FontWeight.w700),
      ),
      const SizedBox(height: 8),
      Center(
        child: TextButton(onPressed: onRetry, child: const Text('Thử lại')),
      ),
    ],
  );
}

({String label, Color color, IconData icon}) _statusMeta(String status) =>
    switch (status) {
      'APPROVED' => (
        label: 'Đã duyệt',
        color: AppColors.primary,
        icon: Icons.verified_outlined,
      ),
      'PAID' => (
        label: 'Đã trả',
        color: AppColors.success,
        icon: Icons.task_alt_rounded,
      ),
      'CANCELLED' => (
        label: 'Đã hủy',
        color: AppColors.error,
        icon: Icons.block_rounded,
      ),
      _ => (
        label: 'Chờ duyệt',
        color: AppColors.warning,
        icon: Icons.schedule_rounded,
      ),
    };

String _date(DateTime value) =>
    '${value.day.toString().padLeft(2, '0')}/${value.month.toString().padLeft(2, '0')} · ${value.hour.toString().padLeft(2, '0')}:${value.minute.toString().padLeft(2, '0')}';
String _compactCurrency(double value) => value >= 1000000
    ? '${(value / 1000000).toStringAsFixed(1)} tr'
    : '${(value / 1000).round()}k';
String _currency(double value) => AppFormatters.currency(value);
