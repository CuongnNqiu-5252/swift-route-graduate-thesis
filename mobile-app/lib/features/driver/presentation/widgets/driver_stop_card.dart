// features/driver/presentation/widgets/driver_stop_card.dart

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/theme/app_colors.dart';
import 'package:swiftroute_app/core/theme/app_theme.dart';
import 'package:swiftroute_app/core/utils/formatters.dart';
import 'package:swiftroute_app/core/widgets/field_ops_widgets.dart';
import 'package:swiftroute_app/features/driver/presentation/screens/driver_navigation_screen.dart';
import 'package:swiftroute_app/features/driver/presentation/widgets/driver_delivery_pod_sheet.dart';
import 'package:swiftroute_app/features/driver/presentation/widgets/driver_stop_detail_sheet.dart';
import 'package:swiftroute_app/features/order/data/models/order_model.dart';

/// Field-Ops card for a single stop on the driver's route timeline (D05).
///
/// For packages already on the vehicle the primary CTA is a one-tap
/// "Giao xong" that opens the POD/COD sheet directly — no intermediate states.
class DriverStopCard extends ConsumerWidget {
  const DriverStopCard({super.key, required this.stop, required this.sequence});

  final OrderModel stop;
  final int sequence;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final status = stop.deliveryTaskStatus ?? DeliveryTaskStatus.assigned;
    final isDone = status == DeliveryTaskStatus.completed;
    final isFailed = status == DeliveryTaskStatus.failed;
    final isCancelled =
        status == DeliveryTaskStatus.cancelled ||
        stop.status == OrderStatus.cancelled;
    final isClosed = isDone || isFailed || isCancelled;
    final canDeliver = stop.isOutForDelivery;

    final badgeColor = isDone
        ? AppColors.success
        : isFailed
        ? AppColors.error
        : (stop.needsPickup ? AppColors.accent : AppColors.primary);

    final address = stop.isReturnTask || stop.needsPickup
        ? stop.pickupAddress
        : stop.deliveryAddress;
    final title = stop.isReturnTask
        ? 'Hoàn về ${stop.pickupDisplayName}'
        : (stop.recipientName?.trim().isNotEmpty == true
              ? stop.recipientName!.trim()
              : 'Khách nhận hàng');

    return Material(
      color: AppColors.card,
      borderRadius: BorderRadius.circular(14),
      child: InkWell(
        borderRadius: BorderRadius.circular(14),
        onTap: () => DriverStopDetailSheet.show(
          context,
          order: stop,
          sequenceNumber: sequence,
        ),
        child: Container(
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(14),
            border: Border.all(color: AppColors.divider),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Container(
                    width: 30,
                    height: 30,
                    alignment: Alignment.center,
                    decoration: BoxDecoration(
                      color: badgeColor,
                      borderRadius: BorderRadius.circular(9),
                    ),
                    child: isDone
                        ? const Icon(
                            Icons.check_rounded,
                            size: 18,
                            color: Colors.white,
                          )
                        : Text(
                            '$sequence',
                            style: AppTheme.numStyle(
                              fontSize: 13.5,
                              fontWeight: FontWeight.w800,
                              color: Colors.white,
                            ),
                          ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Text(
                      stop.displayCode,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: AppTheme.numStyle(
                        fontSize: 14,
                        fontWeight: FontWeight.w800,
                        color: isClosed
                            ? AppColors.textSecondary
                            : AppColors.textPrimary,
                      ),
                    ),
                  ),
                  _StatusPillFor(status: status, isReturn: stop.isReturnTask),
                ],
              ),
              const SizedBox(height: 10),
              Text(
                title,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(
                  fontSize: 15.5,
                  fontWeight: FontWeight.w800,
                  color: isClosed
                      ? AppColors.textSecondary
                      : AppColors.textPrimary,
                ),
              ),
              const SizedBox(height: 3),
              Text(
                address,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  fontSize: 13.5,
                  height: 1.35,
                  color: AppColors.textSecondary,
                ),
              ),
              const SizedBox(height: 10),
              Wrap(
                spacing: 8,
                runSpacing: 6,
                crossAxisAlignment: WrapCrossAlignment.center,
                children: [
                  _MetaText(
                    icon: Icons.schedule_rounded,
                    text: AppFormatters.timeWindow(
                      stop.timeWindowStart,
                      stop.timeWindowEnd,
                    ),
                  ),
                  _MetaText(
                    icon: Icons.inventory_2_outlined,
                    text:
                        '${stop.packageCount} kiện · ${stop.weightKg.toStringAsFixed(1)} kg',
                  ),
                  if (!stop.isReturnTask) CodBadge(amount: stop.codAmount),
                ],
              ),
              if (!isClosed) ...[
                const SizedBox(height: 12),
                Row(
                  children: [
                    PhoneCallButton(
                      phone: stop.needsPickup || stop.isReturnTask
                          ? stop.senderPhone
                          : stop.recipientPhone,
                      compact: true,
                    ),
                    const SizedBox(width: 8),
                    SizedBox(
                      width: 48,
                      height: 48,
                      child: IconButton.outlined(
                        tooltip: 'Mở bản đồ dẫn đường',
                        onPressed: () => Navigator.of(context).push(
                          MaterialPageRoute(
                            builder: (_) => DriverNavigationScreen(order: stop),
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
                    const SizedBox(width: 8),
                    Expanded(
                      child: SizedBox(
                        height: 48,
                        child: canDeliver
                            ? FilledButton.icon(
                                onPressed: () =>
                                    DriverDeliveryPodSheet.show(context, stop),
                                style: FilledButton.styleFrom(
                                  backgroundColor: AppColors.success,
                                  minimumSize: const Size.fromHeight(48),
                                ),
                                icon: const Icon(
                                  Icons.check_circle_rounded,
                                  size: 19,
                                ),
                                label: const Text('Giao xong'),
                              )
                            : OutlinedButton.icon(
                                onPressed: () => DriverStopDetailSheet.show(
                                  context,
                                  order: stop,
                                  sequenceNumber: sequence,
                                ),
                                style: OutlinedButton.styleFrom(
                                  minimumSize: const Size.fromHeight(48),
                                ),
                                icon: Icon(
                                  stop.needsPickup
                                      ? Icons.warehouse_outlined
                                      : Icons.more_horiz_rounded,
                                  size: 19,
                                ),
                                label: Text(
                                  stop.needsPickup ? 'Lấy tại kho' : 'Chi tiết',
                                ),
                              ),
                      ),
                    ),
                  ],
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

class _MetaText extends StatelessWidget {
  const _MetaText({required this.icon, required this.text});

  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, size: 15, color: AppColors.textSecondary),
        const SizedBox(width: 4),
        Text(
          text,
          style: AppTheme.numStyle(
            fontSize: 12.5,
            fontWeight: FontWeight.w600,
            color: AppColors.textSecondary,
          ),
        ),
      ],
    );
  }
}

class _StatusPillFor extends StatelessWidget {
  const _StatusPillFor({required this.status, required this.isReturn});

  final DeliveryTaskStatus status;
  final bool isReturn;

  @override
  Widget build(BuildContext context) {
    final (fg, bg, border) = switch (status) {
      DeliveryTaskStatus.completed => (
        AppColors.success,
        AppColors.successSoft,
        AppColors.successBorder,
      ),
      DeliveryTaskStatus.failed || DeliveryTaskStatus.cancelled => (
        AppColors.errorDark,
        AppColors.errorSoft,
        AppColors.errorBorder,
      ),
      DeliveryTaskStatus.pickedUp || DeliveryTaskStatus.outForDelivery => (
        AppColors.primary,
        AppColors.primarySoft,
        AppColors.primaryBorder,
      ),
      _ => (AppColors.warning, AppColors.warningSoft, AppColors.warningBorder),
    };
    return StatusPill(
      label: isReturn && status == DeliveryTaskStatus.outForDelivery
          ? 'Đang hoàn về'
          : status.displayName,
      foreground: fg,
      background: bg,
      borderColor: border,
    );
  }
}
