// core/widgets/field_ops_widgets.dart

import 'package:flutter/material.dart';
import 'package:swiftroute_app/core/theme/app_colors.dart';
import 'package:swiftroute_app/core/theme/app_theme.dart';
import 'package:swiftroute_app/core/utils/formatters.dart';

/// High-contrast semantic status pill for outdoor scanability.
class StatusPill extends StatelessWidget {
  const StatusPill({
    super.key,
    required this.label,
    required this.foreground,
    required this.background,
    this.borderColor,
    this.icon,
  });

  final String label;
  final Color foreground;
  final Color background;
  final Color? borderColor;
  final IconData? icon;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 4),
      decoration: BoxDecoration(
        color: background,
        borderRadius: BorderRadius.circular(999),
        border: borderColor != null ? Border.all(color: borderColor!) : null,
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (icon != null) ...[
            Icon(icon, size: 13, color: foreground),
            const SizedBox(width: 4),
          ],
          Flexible(
            child: Text(
              label,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                fontSize: 11.5,
                fontWeight: FontWeight.w700,
                color: foreground,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// Dedicated Safety Amber badge for Cash-on-Delivery (COD) amounts so drivers never miss cash collection.
class CodBadge extends StatelessWidget {
  const CodBadge({super.key, required this.amount, this.large = false});

  final double amount;
  final bool large;

  @override
  Widget build(BuildContext context) {
    if (amount <= 0) {
      return Container(
        padding: EdgeInsets.symmetric(
          horizontal: large ? 10 : 8,
          vertical: large ? 6 : 4,
        ),
        decoration: BoxDecoration(
          color: AppColors.surface,
          borderRadius: BorderRadius.circular(8),
        ),
        child: Text(
          'Không thu COD',
          style: TextStyle(
            fontSize: large ? 12.5 : 11.5,
            fontWeight: FontWeight.w600,
            color: AppColors.textSecondary,
          ),
        ),
      );
    }

    return Container(
      padding: EdgeInsets.symmetric(
        horizontal: large ? 12 : 9,
        vertical: large ? 6 : 4,
      ),
      decoration: BoxDecoration(
        color: AppColors.warningSoft,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: AppColors.warningBorder),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(
            Icons.payments_outlined,
            size: large ? 16 : 14,
            color: AppColors.warning,
          ),
          const SizedBox(width: 5),
          Text(
            'Thu COD ${AppFormatters.currency(amount)}',
            style: AppTheme.numStyle(
              fontSize: large ? 13.5 : 12,
              fontWeight: FontWeight.w800,
              color: AppColors.warning,
            ),
          ),
        ],
      ),
    );
  }
}

/// 48dp minimum touch-target phone button that dials customer or depot immediately.
class PhoneCallButton extends StatelessWidget {
  const PhoneCallButton({
    super.key,
    required this.phone,
    this.label,
    this.compact = false,
  });

  final String? phone;
  final String? label;
  final bool compact;

  @override
  Widget build(BuildContext context) {
    final hasPhone = phone != null && phone!.trim().isNotEmpty;
    if (compact) {
      return SizedBox(
        width: 48,
        height: 48,
        child: IconButton.outlined(
          tooltip: hasPhone ? 'Gọi ${phone!}' : 'Chưa có số điện thoại',
          onPressed: hasPhone
              ? () => AppFormatters.callPhone(context, phone)
              : null,
          style: IconButton.styleFrom(
            backgroundColor: hasPhone
                ? AppColors.successSoft
                : AppColors.surface,
            side: BorderSide(
              color: hasPhone ? AppColors.successBorder : AppColors.divider,
            ),
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(12),
            ),
          ),
          icon: Icon(
            Icons.phone_in_talk_rounded,
            size: 20,
            color: hasPhone ? AppColors.success : AppColors.textHint,
          ),
        ),
      );
    }

    return OutlinedButton.icon(
      onPressed: hasPhone
          ? () => AppFormatters.callPhone(context, phone)
          : null,
      style: OutlinedButton.styleFrom(
        backgroundColor: hasPhone ? AppColors.successSoft : AppColors.surface,
        foregroundColor: hasPhone ? AppColors.success : AppColors.textHint,
        side: BorderSide(
          color: hasPhone ? AppColors.successBorder : AppColors.divider,
        ),
      ),
      icon: const Icon(Icons.phone_in_talk_rounded, size: 18),
      label: Text(label ?? (hasPhone ? phone! : 'Chưa có SĐT')),
    );
  }
}

/// Quiet-when-healthy, explicit-when-degraded GPS & connectivity banner.
class GpsSyncBanner extends StatelessWidget {
  const GpsSyncBanner({
    super.key,
    required this.hasLocationIssue,
    required this.onRetry,
  });

  final bool hasLocationIssue;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    if (!hasLocationIssue) return const SizedBox.shrink();

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: const BoxDecoration(
        color: AppColors.warningSoft,
        border: Border(bottom: BorderSide(color: AppColors.warningBorder)),
      ),
      child: Row(
        children: [
          const Icon(Icons.gps_off_rounded, size: 18, color: AppColors.warning),
          const SizedBox(width: 10),
          const Expanded(
            child: Text(
              'GPS hoặc mạng đang gián đoạn. Kiểm tra vị trí để điều phối theo dõi chính xác.',
              style: TextStyle(
                fontSize: 12.5,
                fontWeight: FontWeight.w600,
                color: AppColors.warning,
              ),
            ),
          ),
          TextButton(
            onPressed: onRetry,
            style: TextButton.styleFrom(
              foregroundColor: AppColors.warning,
              minimumSize: const Size(56, 36),
            ),
            child: const Text(
              'Thử lại',
              style: TextStyle(fontWeight: FontWeight.w800),
            ),
          ),
        ],
      ),
    );
  }
}

/// Animated skeleton placeholder for smooth outdoor loading states.
class ShimmerBox extends StatefulWidget {
  const ShimmerBox({
    super.key,
    required this.height,
    this.width = double.infinity,
    this.borderRadius = 14,
  });

  final double height;
  final double width;
  final double borderRadius;

  @override
  State<ShimmerBox> createState() => _ShimmerBoxState();
}

class _ShimmerBoxState extends State<ShimmerBox>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 1100),
  )..repeat(reverse: true);

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return FadeTransition(
      opacity: Tween<double>(
        begin: 0.45,
        end: 0.9,
      ).animate(CurvedAnimation(parent: _controller, curve: Curves.easeInOut)),
      child: Container(
        height: widget.height,
        width: widget.width,
        decoration: BoxDecoration(
          color: AppColors.surfaceVariant,
          borderRadius: BorderRadius.circular(widget.borderRadius),
        ),
      ),
    );
  }
}

/// Standardized empty / feedback card across all 5 driver tabs.
class EmptyStateCard extends StatelessWidget {
  const EmptyStateCard({
    super.key,
    required this.icon,
    required this.title,
    required this.message,
    this.actionLabel,
    this.onAction,
  });

  final IconData icon;
  final String title;
  final String message;
  final String? actionLabel;
  final VoidCallback? onAction;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 28),
      decoration: BoxDecoration(
        color: AppColors.card,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: AppColors.divider),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 52,
            height: 52,
            decoration: BoxDecoration(
              color: AppColors.primarySoft,
              borderRadius: BorderRadius.circular(14),
            ),
            child: Icon(icon, size: 26, color: AppColors.primary),
          ),
          const SizedBox(height: 14),
          Text(
            title,
            textAlign: TextAlign.center,
            style: const TextStyle(
              fontSize: 16,
              fontWeight: FontWeight.w800,
              color: AppColors.textPrimary,
            ),
          ),
          const SizedBox(height: 6),
          Text(
            message,
            textAlign: TextAlign.center,
            style: const TextStyle(
              fontSize: 13.5,
              height: 1.4,
              color: AppColors.textSecondary,
            ),
          ),
          if (actionLabel != null && onAction != null) ...[
            const SizedBox(height: 16),
            OutlinedButton.icon(
              onPressed: onAction,
              icon: const Icon(Icons.refresh_rounded, size: 18),
              label: Text(actionLabel!),
            ),
          ],
        ],
      ),
    );
  }
}
