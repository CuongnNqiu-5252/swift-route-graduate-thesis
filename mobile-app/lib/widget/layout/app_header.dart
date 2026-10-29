import 'package:flutter/material.dart';
import 'package:swiftroute_app/core/theme/app_colors.dart';
import 'package:swiftroute_app/widget/avatar.dart';
import 'package:swiftroute_app/widget/bell.dart';

class Header extends StatelessWidget implements PreferredSizeWidget {
  const Header({
    super.key,
    this.searchHint = 'Tìm kiếm đơn hàng, địa chỉ...',
    this.onSearchChanged,
  });

  final String searchHint;
  final ValueChanged<String>? onSearchChanged;

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      bottom: false,
      child: Container(
        height: preferredSize.height,
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        decoration: const BoxDecoration(
          color: AppColors.background,
          border: Border(
            bottom: BorderSide(color: AppColors.divider, width: 1.0),
          ),
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            // ── 1. LOGO BÊN TRÁI ─────────────────────────────
            Container(
              width: 38,
              height: 38,
              decoration: BoxDecoration(
                color: AppColors.primary.withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(10),
              ),
              child: const Center(child: FlutterLogo(size: 24)),
            ),
            const Expanded(child: const SizedBox(width: 12)),

            const SizedBox(width: 12),

            // ── 3. AVATAR TOOLBAR BÊN PHẢI ───────────────────
            const AvatarFloatingToolbar(),
            const Ring(),
          ],
        ),
      ),
    );
  }

  @override
  Size get preferredSize => const Size.fromHeight(60.0);
}
