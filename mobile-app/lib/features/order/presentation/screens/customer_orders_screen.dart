// features/order/presentation/screens/customer_orders_screen.dart

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/theme/app_colors.dart';
import 'package:swiftroute_app/features/order/application/order_tracking_provider.dart';
import 'package:swiftroute_app/features/order/data/datasources/order_remote_data_source.dart';
import 'package:swiftroute_app/features/order/data/models/order_model.dart';
import 'package:swiftroute_app/features/order/presentation/screens/order_tracking_screen.dart';
import 'package:swiftroute_app/widget/floating_bottom_bar.dart';

class CustomerOrdersScreen extends ConsumerStatefulWidget {
  const CustomerOrdersScreen({super.key});

  @override
  ConsumerState<CustomerOrdersScreen> createState() => _CustomerOrdersScreenState();
}

class _CustomerOrdersScreenState extends ConsumerState<CustomerOrdersScreen> {
  List<OrderModel> _orders = [];
  bool _isLoading = true;
  String _selectedFilter = 'ALL'; // ALL, ACTIVE, DELIVERED

  @override
  void initState() {
    super.initState();
    _loadOrders();
  }

  Future<void> _loadOrders() async {
    setState(() => _isLoading = true);
    try {
      final orders = await ref.read(orderRemoteDataSourceProvider).getMyOrders();
      // Sort newest first
      orders.sort((a, b) => b.createdAt.compareTo(a.createdAt));
      if (mounted) {
        setState(() {
          _orders = orders;
          _isLoading = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() => _isLoading = false);
      }
    }
  }

  List<OrderModel> get _filteredOrders {
    if (_selectedFilter == 'ACTIVE') {
      return _orders.where((o) =>
          o.status == OrderStatus.paid ||
          o.status == OrderStatus.planned ||
          o.status == OrderStatus.inTransit).toList();
    }
    if (_selectedFilter == 'DELIVERED') {
      return _orders.where((o) => o.status == OrderStatus.delivered).toList();
    }
    return _orders;
  }

  @override
  Widget build(BuildContext context) {
    final filtered = _filteredOrders;

    return Scaffold(
      backgroundColor: AppColors.surface,
      appBar: AppBar(
        backgroundColor: Colors.white,
        elevation: 0.5,
        title: const Text(
          'Đơn hàng của tôi',
          style: TextStyle(
            fontSize: 18,
            fontWeight: FontWeight.bold,
            color: AppColors.textPrimary,
          ),
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh_rounded, color: AppColors.primary),
            tooltip: 'Làm mới',
            onPressed: _loadOrders,
          ),
          const SizedBox(width: 8),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: _loadOrders,
        color: AppColors.primary,
        child: Column(
          children: [
            // ── Filter Chips ────────────────────────────────────────────────
            Container(
              color: Colors.white,
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
              child: Row(
                children: [
                  _buildFilterChip('Tất cả (${_orders.length})', 'ALL'),
                  const SizedBox(width: 8),
                  _buildFilterChip(
                    'Đang xử lý (${_orders.where((o) => o.status != OrderStatus.delivered && o.status != OrderStatus.cancelled && o.status != OrderStatus.failed).length})',
                    'ACTIVE',
                  ),
                  const SizedBox(width: 8),
                  _buildFilterChip(
                    'Đã giao (${_orders.where((o) => o.status == OrderStatus.delivered).length})',
                    'DELIVERED',
                  ),
                ],
              ),
            ),
            const Divider(height: 1, color: AppColors.divider),

            // ── Order List ──────────────────────────────────────────────────
            Expanded(
              child: _isLoading
                  ? const Center(
                      child: CircularProgressIndicator(color: AppColors.primary),
                    )
                  : filtered.isEmpty
                      ? _buildEmptyState()
                      : ListView.separated(
                          padding: const EdgeInsets.fromLTRB(16, 16, 16, 90),
                          itemCount: filtered.length,
                          separatorBuilder: (_, _) => const SizedBox(height: 12),
                          itemBuilder: (context, index) {
                            return _buildOrderCard(filtered[index]);
                          },
                        ),
            ),
          ],
        ),
      ),
      bottomNavigationBar: FloatingBottomBar(
        currentIndex: 1,
        onTap: (index) {
          if (index == 0) {
            Navigator.of(context).pushNamedAndRemoveUntil(
              '/customer/home',
              (route) => false,
            );
          }
        },
      ),
    );
  }

  Widget _buildFilterChip(String label, String value) {
    final isSelected = _selectedFilter == value;
    return InkWell(
      borderRadius: BorderRadius.circular(20),
      onTap: () => setState(() => _selectedFilter = value),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 7),
        decoration: BoxDecoration(
          color: isSelected ? AppColors.primary : Colors.grey.shade100,
          borderRadius: BorderRadius.circular(20),
        ),
        child: Text(
          label,
          style: TextStyle(
            fontSize: 12,
            fontWeight: isSelected ? FontWeight.bold : FontWeight.w500,
            color: isSelected ? Colors.white : AppColors.textSecondary,
          ),
        ),
      ),
    );
  }

  Widget _buildEmptyState() {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                color: AppColors.primary.withValues(alpha: 0.08),
                shape: BoxShape.circle,
              ),
              child: const Icon(
                Icons.receipt_long_outlined,
                size: 48,
                color: AppColors.primary,
              ),
            ),
            const SizedBox(height: 16),
            const Text(
              'Không tìm thấy đơn hàng nào',
              style: TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.bold,
                color: AppColors.textPrimary,
              ),
            ),
            const SizedBox(height: 6),
            const Text(
              'Các đơn hàng bạn đặt sẽ xuất hiện tại đây.',
              style: TextStyle(fontSize: 13, color: AppColors.textSecondary),
              textAlign: TextAlign.center,
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildOrderCard(OrderModel order) {
    final shortId = order.id.length > 8 ? order.id.substring(0, 8) : order.id;
    final (badgeColor, badgeText, badgeIcon) = _getStatusDetails(order.status);

    return Material(
      color: Colors.white,
      borderRadius: BorderRadius.circular(16),
      elevation: 0.5,
      child: InkWell(
        borderRadius: BorderRadius.circular(16),
        onTap: () => _openTrackingForOrder(order),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // ── Header: ID + Status Badge ─────────────────────────────────
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Row(
                    children: [
                      Container(
                        padding: const EdgeInsets.all(6),
                        decoration: BoxDecoration(
                          color: AppColors.primary.withValues(alpha: 0.1),
                          shape: BoxShape.circle,
                        ),
                        child: const Icon(
                          Icons.inventory_2_outlined,
                          size: 16,
                          color: AppColors.primary,
                        ),
                      ),
                      const SizedBox(width: 8),
                      Text(
                        '#$shortId',
                        style: const TextStyle(
                          fontSize: 15,
                          fontWeight: FontWeight.bold,
                          color: AppColors.textPrimary,
                        ),
                      ),
                    ],
                  ),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                    decoration: BoxDecoration(
                      color: badgeColor.withValues(alpha: 0.12),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(badgeIcon, size: 12, color: badgeColor),
                        const SizedBox(width: 4),
                        Text(
                          badgeText,
                          style: TextStyle(
                            fontSize: 11,
                            fontWeight: FontWeight.w700,
                            color: badgeColor,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),

              // ── Pickup & Delivery Route ───────────────────────────────────
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Column(
                    children: [
                      const Icon(Icons.circle, size: 10, color: AppColors.primary),
                      Container(
                        width: 1.5,
                        height: 24,
                        color: Colors.grey.shade300,
                      ),
                      const Icon(Icons.location_on, size: 12, color: AppColors.accent),
                    ],
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          order.pickupAddress.isNotEmpty
                              ? order.pickupAddress
                              : 'Điểm lấy hàng',
                          style: const TextStyle(
                            fontSize: 12,
                            color: AppColors.textSecondary,
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                        const SizedBox(height: 12),
                        Text(
                          order.deliveryAddress.isNotEmpty
                              ? order.deliveryAddress
                              : 'Điểm giao hàng',
                          style: const TextStyle(
                            fontSize: 13,
                            fontWeight: FontWeight.w600,
                            color: AppColors.textPrimary,
                          ),
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              const Divider(height: 1, color: AppColors.divider),
              const SizedBox(height: 10),

              // ── Footer: Weight/Category & Action Button ───────────────────
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Row(
                    children: [
                      Icon(Icons.scale_rounded, size: 14, color: Colors.grey.shade500),
                      const SizedBox(width: 4),
                      Text(
                        '${order.weightKg.toStringAsFixed(1)} kg',
                        style: TextStyle(fontSize: 12, color: Colors.grey.shade600),
                      ),
                      const SizedBox(width: 12),
                      Icon(Icons.flag_outlined, size: 14, color: Colors.grey.shade500),
                      const SizedBox(width: 4),
                      Text(
                        order.priority.isNotEmpty ? order.priority : 'NORMAL',
                        style: TextStyle(fontSize: 12, color: Colors.grey.shade600),
                      ),
                    ],
                  ),
                  ElevatedButton.icon(
                    onPressed: () => _openTrackingForOrder(order),
                    icon: Icon(
                      order.status == OrderStatus.delivered
                          ? Icons.check_circle_outline
                          : Icons.navigation_rounded,
                      size: 14,
                    ),
                    label: Text(
                      order.status == OrderStatus.delivered
                          ? 'Xem lại'
                          : 'Theo dõi live',
                      style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold),
                    ),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: order.status == OrderStatus.delivered
                          ? Colors.grey.shade100
                          : AppColors.primary,
                      foregroundColor: order.status == OrderStatus.delivered
                          ? AppColors.textPrimary
                          : Colors.white,
                      elevation: 0,
                      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(10),
                      ),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _openTrackingForOrder(OrderModel order) {
    ref.read(orderTrackingProvider.notifier).selectOrderForTracking(order);
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => OrderTrackingScreen(targetOrder: order),
      ),
    ).then((_) {
      _loadOrders();
    });
  }

  (Color, String, IconData) _getStatusDetails(OrderStatus status) {
    switch (status) {
      case OrderStatus.pendingPayment:
        return (Colors.orange, 'Chờ thanh toán', Icons.payment);
      case OrderStatus.paid:
        return (Colors.teal, 'Chờ điều phối', Icons.schedule);
      case OrderStatus.planned:
        return (AppColors.primary, 'Đã lên lịch', Icons.assignment_turned_in);
      case OrderStatus.inTransit:
        return (AppColors.accent, 'Đang giao', Icons.two_wheeler);
      case OrderStatus.delivered:
        return (AppColors.success, 'Đã giao', Icons.check_circle);
      case OrderStatus.cancelled:
        return (AppColors.error, 'Đã huỷ', Icons.cancel);
      case OrderStatus.failed:
        return (Colors.red.shade700, 'Giao thất bại', Icons.error_outline);
      default:
        return (Colors.grey, status.name, Icons.info_outline);
    }
  }
}
