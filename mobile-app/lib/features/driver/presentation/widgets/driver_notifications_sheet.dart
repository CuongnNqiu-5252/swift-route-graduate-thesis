// features/driver/presentation/widgets/driver_notifications_sheet.dart

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/theme/app_colors.dart';
import 'package:swiftroute_app/features/driver/application/driver_provider.dart';
import 'package:swiftroute_app/features/driver/data/models/driver_notification_model.dart';

class DriverNotificationsSheet extends ConsumerStatefulWidget {
  const DriverNotificationsSheet({
    super.key,
    required this.onNavigateToRoute,
    this.onNavigateToHandover,
    this.onNavigateToEarnings,
    this.onNavigateToProfile,
  });

  final VoidCallback onNavigateToRoute;
  final VoidCallback? onNavigateToHandover;
  final VoidCallback? onNavigateToEarnings;
  final VoidCallback? onNavigateToProfile;

  @override
  ConsumerState<DriverNotificationsSheet> createState() =>
      _DriverNotificationsSheetState();
}

class _DriverNotificationsSheetState
    extends ConsumerState<DriverNotificationsSheet> {
  String _filter = 'ALL';

  String _formatRelativeTime(DateTime time) {
    final diff = DateTime.now().difference(time);
    if (diff.inSeconds < 60) return 'Vừa xong';
    if (diff.inMinutes < 60) return '${diff.inMinutes} phút trước';
    if (diff.inHours < 24) return '${diff.inHours} giờ trước';
    final d = time.toLocal();
    final dd = d.day.toString().padLeft(2, '0');
    final mm = d.month.toString().padLeft(2, '0');
    final hh = d.hour.toString().padLeft(2, '0');
    final min = d.minute.toString().padLeft(2, '0');
    return '$hh:$min · $dd/$mm';
  }

  _NotificationPresentation _presentationFor(DriverNotificationModel item) {
    switch (item.category) {
      case 'DISPATCH':
        return _NotificationPresentation(
          label: item.templateCode == 'plan.cancelled'
              ? 'Huỷ lộ trình'
              : 'Lộ trình',
          icon: Icons.alt_route_rounded,
          color: item.templateCode == 'plan.cancelled'
              ? AppColors.warning
              : AppColors.primary,
          actionLabel: 'Mở lộ trình',
          onTap: () {
            Navigator.of(context).pop();
            widget.onNavigateToRoute();
          },
        );
      case 'INCIDENT':
      case 'DELIVERY':
        return _NotificationPresentation(
          label: item.templateCode == 'return.approved'
              ? 'Hoàn hàng'
              : 'Xử lý sự cố',
          icon: item.templateCode == 'return.approved'
              ? Icons.assignment_return_rounded
              : Icons.verified_user_rounded,
          color: AppColors.accent,
          actionLabel: 'Xem điểm dừng',
          onTap: () {
            Navigator.of(context).pop();
            widget.onNavigateToRoute();
          },
        );
      case 'COD':
        final isDiscrepancy = item.templateCode == 'cod.handover_discrepancy';
        return _NotificationPresentation(
          label: isDiscrepancy ? 'Chênh lệch COD' : 'Đối soát COD',
          icon: isDiscrepancy
              ? Icons.warning_amber_rounded
              : Icons.payments_rounded,
          color: isDiscrepancy ? AppColors.error : AppColors.success,
          actionLabel: 'Mở bàn giao COD',
          onTap: () {
            Navigator.of(context).pop();
            widget.onNavigateToHandover?.call();
          },
        );
      case 'EARNINGS':
        return _NotificationPresentation(
          label: item.templateCode == 'settlement.paid'
              ? 'Đã thanh toán'
              : 'Thu nhập',
          icon: Icons.account_balance_wallet_rounded,
          color: AppColors.success,
          actionLabel: 'Xem thu nhập',
          onTap: () {
            Navigator.of(context).pop();
            widget.onNavigateToEarnings?.call();
          },
        );
      default:
        return _NotificationPresentation(
          label: 'Hồ sơ tài xế',
          icon: Icons.badge_rounded,
          color: AppColors.primary,
          actionLabel: 'Xem hồ sơ',
          onTap: () {
            Navigator.of(context).pop();
            widget.onNavigateToProfile?.call();
          },
        );
    }
  }

  @override
  Widget build(BuildContext context) {
    final notificationsState = ref.watch(driverNotificationsProvider);

    return DraggableScrollableSheet(
      initialChildSize: 0.78,
      minChildSize: 0.5,
      maxChildSize: 0.94,
      expand: false,
      builder: (context, scrollController) {
        return Container(
          decoration: const BoxDecoration(
            color: AppColors.surface,
            borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const SizedBox(height: 10),
              Center(
                child: Container(
                  width: 40,
                  height: 4,
                  decoration: BoxDecoration(
                    color: AppColors.divider,
                    borderRadius: BorderRadius.circular(999),
                  ),
                ),
              ),
              Padding(
                padding: const EdgeInsets.fromLTRB(20, 14, 12, 8),
                child: Row(
                  children: [
                    const Icon(
                      Icons.notifications_active_rounded,
                      color: AppColors.primary,
                      size: 22,
                    ),
                    const SizedBox(width: 10),
                    const Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'Hòm thư thông báo',
                            style: TextStyle(
                              fontSize: 17,
                              fontWeight: FontWeight.w800,
                              color: AppColors.textPrimary,
                            ),
                          ),
                          Text(
                            'Cập nhật lộ trình, xử lý sự cố, đối soát COD & thu nhập',
                            style: TextStyle(
                              fontSize: 12,
                              color: AppColors.textSecondary,
                            ),
                          ),
                        ],
                      ),
                    ),
                    IconButton(
                      tooltip: 'Làm mới thông báo',
                      onPressed: () {
                        ref
                            .read(driverNotificationsProvider.notifier)
                            .refresh();
                      },
                      icon: const Icon(Icons.refresh_rounded, size: 20),
                    ),
                    IconButton(
                      tooltip: 'Đóng',
                      onPressed: () => Navigator.of(context).pop(),
                      icon: const Icon(Icons.close_rounded, size: 22),
                    ),
                  ],
                ),
              ),
              SingleChildScrollView(
                scrollDirection: Axis.horizontal,
                padding: const EdgeInsets.symmetric(
                  horizontal: 16,
                  vertical: 6,
                ),
                child: Row(
                  children: [
                    _FilterChipButton(
                      label: 'Tất cả',
                      selected: _filter == 'ALL',
                      onTap: () => setState(() => _filter = 'ALL'),
                    ),
                    const SizedBox(width: 8),
                    _FilterChipButton(
                      label: 'Lộ trình & Sự cố',
                      selected: _filter == 'ROUTE',
                      onTap: () => setState(() => _filter = 'ROUTE'),
                    ),
                    const SizedBox(width: 8),
                    _FilterChipButton(
                      label: 'COD & Thu nhập',
                      selected: _filter == 'FINANCE',
                      onTap: () => setState(() => _filter = 'FINANCE'),
                    ),
                  ],
                ),
              ),
              const Divider(height: 1, color: AppColors.divider),
              Expanded(
                child: notificationsState.when(
                  loading: () => const Center(
                    child: CircularProgressIndicator(color: AppColors.primary),
                  ),
                  error: (err, _) => Center(
                    child: Padding(
                      padding: const EdgeInsets.all(24),
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          const Icon(
                            Icons.cloud_off_rounded,
                            size: 38,
                            color: AppColors.textSecondary,
                          ),
                          const SizedBox(height: 12),
                          const Text(
                            'Chưa tải được danh sách thông báo',
                            style: TextStyle(
                              fontWeight: FontWeight.w700,
                              color: AppColors.textPrimary,
                            ),
                          ),
                          const SizedBox(height: 10),
                          OutlinedButton.icon(
                            onPressed: () {
                              ref
                                  .read(driverNotificationsProvider.notifier)
                                  .refresh();
                            },
                            icon: const Icon(Icons.refresh_rounded, size: 18),
                            label: const Text('Thử lại'),
                          ),
                        ],
                      ),
                    ),
                  ),
                  data: (items) {
                    final filtered = items.where((item) {
                      if (_filter == 'ROUTE') {
                        return item.category == 'DISPATCH' ||
                            item.category == 'INCIDENT' ||
                            item.category == 'DELIVERY';
                      }
                      if (_filter == 'FINANCE') {
                        return item.category == 'COD' ||
                            item.category == 'EARNINGS';
                      }
                      return true;
                    }).toList();

                    if (filtered.isEmpty) {
                      return ListView(
                        controller: scrollController,
                        padding: const EdgeInsets.all(32),
                        children: const [
                          SizedBox(height: 36),
                          Icon(
                            Icons.notifications_none_rounded,
                            size: 48,
                            color: AppColors.textHint,
                          ),
                          SizedBox(height: 12),
                          Text(
                            'Chưa có thông báo mới',
                            textAlign: TextAlign.center,
                            style: TextStyle(
                              fontSize: 15,
                              fontWeight: FontWeight.w700,
                              color: AppColors.textPrimary,
                            ),
                          ),
                          SizedBox(height: 6),
                          Text(
                            'Khi có lộ trình mới, phản hồi xử lý sự cố từ điều phối viên, kết quả bàn giao COD hoặc thanh toán thu nhập, bạn sẽ nhận thông báo tại đây.',
                            textAlign: TextAlign.center,
                            style: TextStyle(
                              fontSize: 13,
                              height: 1.45,
                              color: AppColors.textSecondary,
                            ),
                          ),
                        ],
                      );
                    }

                    return ListView.separated(
                      controller: scrollController,
                      padding: const EdgeInsets.fromLTRB(16, 12, 16, 28),
                      itemCount: filtered.length,
                      separatorBuilder: (_, _) => const SizedBox(height: 10),
                      itemBuilder: (context, index) {
                        final item = filtered[index];
                        final pres = _presentationFor(item);
                        return InkWell(
                          onTap: pres.onTap,
                          borderRadius: BorderRadius.circular(14),
                          child: Container(
                            padding: const EdgeInsets.all(14),
                            decoration: BoxDecoration(
                              color: AppColors.background,
                              borderRadius: BorderRadius.circular(14),
                              border: Border.all(color: AppColors.divider),
                            ),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Row(
                                  children: [
                                    Container(
                                      padding: const EdgeInsets.symmetric(
                                        horizontal: 8,
                                        vertical: 3,
                                      ),
                                      decoration: BoxDecoration(
                                        color: pres.color.withValues(
                                          alpha: 0.12,
                                        ),
                                        borderRadius: BorderRadius.circular(6),
                                      ),
                                      child: Row(
                                        mainAxisSize: MainAxisSize.min,
                                        children: [
                                          Icon(
                                            pres.icon,
                                            size: 13,
                                            color: pres.color,
                                          ),
                                          const SizedBox(width: 4),
                                          Text(
                                            pres.label,
                                            style: TextStyle(
                                              fontSize: 11,
                                              fontWeight: FontWeight.w700,
                                              color: pres.color,
                                            ),
                                          ),
                                        ],
                                      ),
                                    ),
                                    const Spacer(),
                                    Text(
                                      _formatRelativeTime(item.createdAt),
                                      style: const TextStyle(
                                        fontSize: 11,
                                        color: AppColors.textSecondary,
                                      ),
                                    ),
                                  ],
                                ),
                                const SizedBox(height: 8),
                                Text(
                                  item.title,
                                  style: const TextStyle(
                                    fontSize: 14,
                                    fontWeight: FontWeight.w700,
                                    color: AppColors.textPrimary,
                                  ),
                                ),
                                if (item.body.isNotEmpty) ...[
                                  const SizedBox(height: 4),
                                  Text(
                                    item.body,
                                    style: const TextStyle(
                                      fontSize: 13,
                                      height: 1.4,
                                      color: AppColors.textSecondary,
                                    ),
                                  ),
                                ],
                                const SizedBox(height: 8),
                                Row(
                                  mainAxisAlignment: MainAxisAlignment.end,
                                  children: [
                                    Text(
                                      pres.actionLabel,
                                      style: TextStyle(
                                        fontSize: 12,
                                        fontWeight: FontWeight.w700,
                                        color: pres.color,
                                      ),
                                    ),
                                    const SizedBox(width: 4),
                                    Icon(
                                      Icons.arrow_forward_rounded,
                                      size: 14,
                                      color: pres.color,
                                    ),
                                  ],
                                ),
                              ],
                            ),
                          ),
                        );
                      },
                    );
                  },
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}

class _NotificationPresentation {
  final String label;
  final IconData icon;
  final Color color;
  final String actionLabel;
  final VoidCallback onTap;

  const _NotificationPresentation({
    required this.label,
    required this.icon,
    required this.color,
    required this.actionLabel,
    required this.onTap,
  });
}

class _FilterChipButton extends StatelessWidget {
  const _FilterChipButton({
    required this.label,
    required this.selected,
    required this.onTap,
  });

  final String label;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return ChoiceChip(
      label: Text(label),
      selected: selected,
      onSelected: (_) => onTap(),
      labelStyle: TextStyle(
        fontSize: 12,
        fontWeight: selected ? FontWeight.w700 : FontWeight.w500,
        color: selected ? Colors.white : AppColors.textPrimary,
      ),
      selectedColor: AppColors.primary,
      backgroundColor: AppColors.background,
      showCheckmark: false,
    );
  }
}

