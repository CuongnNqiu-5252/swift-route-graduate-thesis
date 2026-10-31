import 'dart:ui';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/presentation/providers/auth_provider.dart';
import 'package:swiftroute_app/widget/profile_bottom_sheet.dart';

class AvatarFloatingToolbar extends ConsumerStatefulWidget {
  const AvatarFloatingToolbar({super.key});

  @override
  ConsumerState<AvatarFloatingToolbar> createState() => _AvatarFloatingToolbarState();
}

class _AvatarFloatingToolbarState extends ConsumerState<AvatarFloatingToolbar>
    with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  bool isOpen = false;

  // Cấu hình kích thước
  static const double _avatarSize = 40; // 38 - 42 là chuẩn
  static const double _buttonSize = 38; // Kích thước các icon con nhỏ gọn hơn
  static const double _spacing = 10; // Khoảng cách giữa các nút gọn hơn
  static const double _padding = 4;

  // Danh sách nút
  static const List<_ItemData> _items = [
    _ItemData(Icons.receipt_long_rounded, 'Đơn hàng', 0),
    _ItemData(Icons.person_rounded, 'Hồ sơ', 0),
    _ItemData(Icons.logout_rounded, 'Đăng xuất', 0, isDestructive: true),
  ];

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 350),
    );
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void toggle() {
    setState(() {
      isOpen = !isOpen;
      if (isOpen) {
        _controller.forward();
      } else {
        _controller.reverse();
      }
    });
  }

  void _handleItemTap(_ItemData item) {
    toggle();
    if (item.tooltip == 'Hồ sơ') {
      ProfileBottomSheet.show(context);
    } else if (item.tooltip == 'Đăng xuất') {
      _showLogoutDialog();
    } else if (item.tooltip == 'Đơn hàng') {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Danh sách đơn hàng của bạn'),
          duration: Duration(seconds: 2),
        ),
      );
    }
  }

  void _showLogoutDialog() {
    showDialog(
      context: context,
      builder: (dialogCtx) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        title: const Text('Xác nhận đăng xuất'),
        content: const Text(
          'Bạn có chắc chắn muốn đăng xuất khỏi tài khoản này không?',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogCtx),
            child: const Text('Hủy', style: TextStyle(color: Colors.grey)),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(
              backgroundColor: Colors.red,
              foregroundColor: Colors.white,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(8),
              ),
            ),
            onPressed: () async {
              Navigator.pop(dialogCtx);
              await ref.read(authProvider.notifier).logout();
              if (mounted) {
                Navigator.of(context).pushNamedAndRemoveUntil(
                  '/login',
                  (route) => false,
                );
              }
            },
            child: const Text('Đăng xuất'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    // Tổng chiều cao cần: avatar + (button + spacing) * số items + padding
    final double expandedHeight =
        _avatarSize + (_items.length * (_buttonSize + _spacing)) + _padding * 2;
    const double totalWidth = _avatarSize + _padding * 2;

    return SizedBox(
      width: totalWidth,
      height: expandedHeight,
      child: Stack(
        clipBehavior: Clip.none,
        children: [
          // ── Menu items ──
          ...List.generate(_items.length, (index) {
            final item = _items[index];
            // Vị trí từ đáy: avatar + spacing cho mỗi item
            final double targetBottom =
                _avatarSize + _spacing + index * (_buttonSize + _spacing);

            // Stagger animation
            final double staggerStart = index * 0.1;
            final double staggerEnd = (staggerStart + 0.7).clamp(0.0, 1.0);

            final itemCurve = CurvedAnimation(
              parent: _controller,
              curve: Interval(
                staggerStart,
                staggerEnd,
                curve: Curves.easeOutBack,
              ),
              reverseCurve: Interval(
                staggerStart,
                staggerEnd,
                curve: Curves.easeIn,
              ),
            );

            return AnimatedBuilder(
              animation: itemCurve,
              builder: (context, child) {
                final double t = itemCurve.value;
                final double currentBottom = t * targetBottom;

                return Positioned(
                  bottom: currentBottom,
                  left: (totalWidth - _buttonSize) / 2,
                  child: Opacity(
                    opacity: t.clamp(0.0, 1.0),
                    child: Transform.scale(scale: 0.3 + 0.7 * t, child: child),
                  ),
                );
              },
              child: _ToolbarButton(
                icon: item.icon,
                tooltip: item.tooltip,
                badgeCount: item.badgeCount,
                size: _buttonSize,
                isDestructive: item.isDestructive,
                onTap: () => _handleItemTap(item),
              ),
            );
          }),

          // ── Avatar (luôn ở đáy, trên cùng z-order) ──
          Positioned(
            bottom: 0,
            left: (totalWidth - _avatarSize) / 2,
            child: _AvatarButton(
              isOpen: isOpen,
              animation: _controller,
              size: _avatarSize,
              onTap: toggle,
            ),
          ),
        ],
      ),
    );
  }
}

/// Dữ liệu cho mỗi item
class _ItemData {
  final IconData icon;
  final String tooltip;
  final int badgeCount;
  final bool isDestructive;
  const _ItemData(this.icon, this.tooltip, this.badgeCount, {this.isDestructive = false});
}

// ═══════════════════════════════════════════════════════════════════
//  Avatar Button
// ═══════════════════════════════════════════════════════════════════

class _AvatarButton extends ConsumerWidget {
  const _AvatarButton({
    required this.isOpen,
    required this.animation,
    required this.size,
    required this.onTap,
  });

  final bool isOpen;
  final Animation<double> animation;
  final double size;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final authState = ref.watch(authProvider);
    final user = authState.user;
    final String initial = (user?.name.isNotEmpty == true)
        ? user!.name[0].toUpperCase()
        : 'U';

    return GestureDetector(
      onTap: onTap,
      child: AnimatedBuilder(
        animation: animation,
        builder: (context, child) {
          final double v = animation.value;
          return AnimatedScale(
            scale: isOpen ? 0.92 : 1.0,
            duration: const Duration(milliseconds: 150),
            child: Container(
              width: size,
              height: size,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                gradient: const LinearGradient(
                  colors: [Color(0xFF2F3C7E), Color(0xFF1E2756)],
                ),
                border: Border.all(
                  color: Color.lerp(
                    Colors.white,
                    const Color(0xFF00E5FF),
                    v,
                  )!,
                  width: 2.0,
                ),
                boxShadow: [
                  BoxShadow(
                    color: const Color(0xFF00E5FF).withValues(alpha: 0.3 * v),
                    blurRadius: 16 * v,
                    spreadRadius: 2 * v,
                  ),
                  const BoxShadow(
                    color: Colors.black26,
                    blurRadius: 8,
                    offset: Offset(0, 3),
                  ),
                ],
              ),
              child: Center(
                child: Text(
                  initial,
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 16,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
            ),
          );
        },
      ),
    );
  }
}

// ═══════════════════════════════════════════════════════════════════
//  Toolbar Button — glassmorphism + badge
// ═══════════════════════════════════════════════════════════════════

class _ToolbarButton extends StatefulWidget {
  const _ToolbarButton({
    required this.icon,
    required this.tooltip,
    required this.size,
    this.badgeCount = 0,
    this.isDestructive = false,
    this.onTap,
  });

  final IconData icon;
  final String tooltip;
  final double size;
  final int badgeCount;
  final bool isDestructive;
  final VoidCallback? onTap;

  @override
  State<_ToolbarButton> createState() => _ToolbarButtonState();
}

class _ToolbarButtonState extends State<_ToolbarButton> {
  bool _pressed = false;

  @override
  Widget build(BuildContext context) {
    final Color activeColor = widget.isDestructive
        ? const Color(0xFFFF3B5C)
        : const Color(0xFF2F3C7E);

    return GestureDetector(
      onTapDown: (_) => setState(() => _pressed = true),
      onTapUp: (_) {
        setState(() => _pressed = false);
        widget.onTap?.call();
      },
      onTapCancel: () => setState(() => _pressed = false),
      child: Tooltip(
        message: widget.tooltip,
        preferBelow: false,
        child: AnimatedScale(
          scale: _pressed ? 0.85 : 1.0,
          duration: const Duration(milliseconds: 100),
          child: SizedBox(
            width: widget.size + 8,
            height: widget.size + 4,
            child: Stack(
              clipBehavior: Clip.none,
              alignment: Alignment.center,
              children: [
                // Nút tròn glassmorphism
                ClipOval(
                  child: BackdropFilter(
                    filter: ImageFilter.blur(sigmaX: 14, sigmaY: 14),
                    child: AnimatedContainer(
                      duration: const Duration(milliseconds: 150),
                      width: widget.size,
                      height: widget.size,
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        color: widget.isDestructive
                            ? Colors.red.shade50
                            : Colors.white,
                        border: Border.all(
                          color: widget.isDestructive
                              ? Colors.red.shade200
                              : Colors.grey.shade300,
                          width: 1.2,
                        ),
                        boxShadow: [
                          BoxShadow(
                            color: Colors.black.withValues(alpha: 0.15),
                            blurRadius: 8,
                            offset: const Offset(0, 3),
                          ),
                        ],
                      ),
                      child: Icon(
                        widget.icon,
                        color: activeColor,
                        size: widget.size * 0.48,
                      ),
                    ),
                  ),
                ),

                // Badge
                if (widget.badgeCount > 0)
                  Positioned(
                    top: -2,
                    right: 0,
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 5,
                        vertical: 2,
                      ),
                      decoration: BoxDecoration(
                        color: const Color(0xFFFF3B5C),
                        borderRadius: BorderRadius.circular(10),
                      ),
                      constraints: const BoxConstraints(
                        minWidth: 18,
                        minHeight: 18,
                      ),
                      child: Text(
                        widget.badgeCount > 99
                            ? '99+'
                            : widget.badgeCount.toString(),
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 10,
                          fontWeight: FontWeight.w700,
                          height: 1,
                        ),
                        textAlign: TextAlign.center,
                      ),
                    ),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
