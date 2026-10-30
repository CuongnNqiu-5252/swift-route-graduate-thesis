// presentation/screens/home_screen.dart

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/theme/app_colors.dart';
import 'package:swiftroute_app/features/order/application/home_provider.dart';
import 'package:swiftroute_app/features/order/application/order_form_provider.dart';
import 'package:swiftroute_app/features/order/application/order_tracking_provider.dart';
import 'package:swiftroute_app/features/order/data/models/order_model.dart';
import 'package:swiftroute_app/features/order/data/models/tenant_model.dart';
import 'package:swiftroute_app/features/order/presentation/screens/create_order_screen.dart';
import 'package:swiftroute_app/features/order/presentation/screens/order_tracking_screen.dart';
import 'package:swiftroute_app/features/order/presentation/widgets/address_search_sheet.dart';
import 'package:swiftroute_app/presentation/providers/auth_provider.dart';
import 'package:swiftroute_app/widget/bell.dart';
import 'package:swiftroute_app/widget/floating_bottom_bar.dart';
import 'package:swiftroute_app/widget/profile_bottom_sheet.dart';

class HomeScreen extends ConsumerStatefulWidget {
  const HomeScreen({super.key});

  @override
  ConsumerState<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends ConsumerState<HomeScreen> {
  final TextEditingController _searchController = TextEditingController();

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      ref.read(orderTrackingProvider.notifier).fetchActiveOrder();
    });
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final homeState = ref.watch(homeProvider);
    final homeNotifier = ref.read(homeProvider.notifier);
    final trackingState = ref.watch(orderTrackingProvider);
    final filteredTenants = homeState.filteredTenants;

    return Scaffold(
      backgroundColor: AppColors.surface,
      appBar: _buildCustomHeader(context, homeState, homeNotifier),
      body: RefreshIndicator(
        onRefresh: () async {
          await Future.wait([
            homeNotifier.loadNearbyTenants(),
            ref.read(orderTrackingProvider.notifier).fetchActiveOrder(),
          ]);
        },
        color: AppColors.primary,
        child: ListView(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          children: [
            // ── 0. Active Delivery Card (If active order exists) ─────────────
            if (trackingState.hasActiveOrder) ...[
              _buildActiveDeliveryCard(context, trackingState),
              const SizedBox(height: 16),
            ],

            // ── 1. Hero Express Banner ──────────────────────────────────────
            _buildExpressBanner(context, trackingState),
            const SizedBox(height: 16),

            // ── 2. Search Bar ───────────────────────────────────────────────
            _buildSearchBar(homeNotifier),
            const SizedBox(height: 16),

            // ── 3. Category Horizontal Filter ───────────────────────────────
            _buildCategoryFilter(homeState, homeNotifier),
            const SizedBox(height: 20),

            // ── 4. Section Header ───────────────────────────────────────────
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Row(
                  children: [
                    const Icon(
                      Icons.storefront_rounded,
                      color: AppColors.primary,
                      size: 22,
                    ),
                    const SizedBox(width: 8),
                    const Text(
                      'Cửa hàng gần bạn',
                      style: TextStyle(
                        fontSize: 18,
                        fontWeight: FontWeight.bold,
                        color: AppColors.textPrimary,
                      ),
                    ),
                  ],
                ),
                Text(
                  '${filteredTenants.length} địa điểm',
                  style: const TextStyle(
                    fontSize: 13,
                    color: AppColors.textSecondary,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),

            // ── 5. Store List / Loading / Empty State ───────────────────────
            if (homeState.isLoading)
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 40),
                child: Center(
                  child: CircularProgressIndicator(color: AppColors.primary),
                ),
              )
            else if (filteredTenants.isEmpty)
              _buildEmptyState(homeNotifier)
            else
              ...filteredTenants.map(
                (tenant) =>
                    _buildStoreCard(context, tenant, homeState, trackingState),
              ),

            const SizedBox(height: 84),
          ],
        ),
      ),
      bottomNavigationBar: const FloatingBottomBar(currentIndex: 0),
    );
  }

  /// Top navigation bar with delivery destination picker, bell, and avatar.
  PreferredSizeWidget _buildCustomHeader(
    BuildContext context,
    HomeState homeState,
    HomeNotifier homeNotifier,
  ) {
    return AppBar(
      backgroundColor: Colors.white,
      elevation: 0.5,
      titleSpacing: 16,
      title: InkWell(
        borderRadius: BorderRadius.circular(8),
        onTap: () async {
          final detail = await AddressSearchSheet.show(
            context,
            title: 'Chọn vị trí của bạn',
          );
          if (detail != null) {
            homeNotifier.updateLocation(
              address: detail.name,
              lat: detail.lat,
              lng: detail.lng,
            );
          }
        },
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 4),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                padding: const EdgeInsets.all(6),
                decoration: BoxDecoration(
                  color: AppColors.accent.withValues(alpha: 0.12),
                  shape: BoxShape.circle,
                ),
                child: const Icon(
                  Icons.location_on,
                  color: AppColors.accent,
                  size: 18,
                ),
              ),
              const SizedBox(width: 8),
              Flexible(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      'GIAO ĐẾN VỊ TRÍ',
                      style: TextStyle(
                        fontSize: 10,
                        fontWeight: FontWeight.w700,
                        color: AppColors.textSecondary,
                        letterSpacing: 0.5,
                      ),
                    ),
                    Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Flexible(
                          child: Text(
                            homeState.currentAddress,
                            style: const TextStyle(
                              fontSize: 14,
                              fontWeight: FontWeight.bold,
                              color: AppColors.textPrimary,
                            ),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                        const Icon(
                          Icons.keyboard_arrow_down_rounded,
                          size: 18,
                          color: AppColors.textSecondary,
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
      actions: [
        const Ring(),
        const SizedBox(width: 6),
        InkWell(
          borderRadius: BorderRadius.circular(20),
          onTap: () => ProfileBottomSheet.show(context),
          child: Container(
            width: 36,
            height: 36,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: AppColors.primary.withValues(alpha: 0.1),
              border: Border.all(
                color: AppColors.primary.withValues(alpha: 0.25),
                width: 1.5,
              ),
            ),
            child: Center(
              child: Text(
                (ref.watch(authProvider).user?.name.isNotEmpty == true)
                    ? ref.watch(authProvider).user!.name[0].toUpperCase()
                    : 'U',
                style: const TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.bold,
                  color: AppColors.primary,
                ),
              ),
            ),
          ),
        ),
        const SizedBox(width: 16),
      ],
    );
  }

  /// Prominent Active Delivery Card showing ongoing order and live tracking shortcut.
  Widget _buildActiveDeliveryCard(
    BuildContext context,
    OrderTrackingState trackingState,
  ) {
    final order = trackingState.order;
    if (order == null) return const SizedBox.shrink();

    final shortId = order.id.length > 8 ? order.id.substring(0, 8) : order.id;

    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: () {
          Navigator.of(context).push(
            MaterialPageRoute(builder: (_) => const OrderTrackingScreen()),
          );
        },
        borderRadius: BorderRadius.circular(16),
        child: Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            gradient: const LinearGradient(
              colors: [Colors.white, Color(0xFFF1F5F9)],
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
            ),
            borderRadius: BorderRadius.circular(16),
            border: Border.all(
              color: AppColors.primary.withValues(alpha: 0.2),
              width: 1.5,
            ),
            boxShadow: [
              BoxShadow(
                color: AppColors.primary.withValues(alpha: 0.08),
                blurRadius: 14,
                offset: const Offset(0, 4),
              ),
            ],
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Header: Live Indicator + Order ID + Status Badge
              Row(
                children: [
                  Container(
                    padding: const EdgeInsets.all(8),
                    decoration: BoxDecoration(
                      color: AppColors.primary.withValues(alpha: 0.1),
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(
                      Icons.two_wheeler_rounded,
                      color: AppColors.primary,
                      size: 20,
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Container(
                              width: 8,
                              height: 8,
                              decoration: const BoxDecoration(
                                color: Color(0xFF10B981),
                                shape: BoxShape.circle,
                              ),
                            ),
                            const SizedBox(width: 6),
                            const Text(
                              'ĐƠN HÀNG ĐANG GIAO (LIVE)',
                              style: TextStyle(
                                fontSize: 11,
                                fontWeight: FontWeight.w700,
                                color: AppColors.primary,
                                letterSpacing: 0.5,
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 2),
                        Text(
                          'Mã đơn: #$shortId',
                          style: const TextStyle(
                            fontSize: 13,
                            fontWeight: FontWeight.w700,
                            color: AppColors.textPrimary,
                          ),
                        ),
                      ],
                    ),
                  ),
                  _buildOrderStatusPill(trackingState.status),
                ],
              ),

              const Padding(
                padding: EdgeInsets.symmetric(vertical: 12),
                child: Divider(height: 1, color: Color(0xFFE2E8F0)),
              ),

              // Pickup & Destination Route
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Column(
                    children: [
                      const Icon(
                        Icons.circle,
                        size: 10,
                        color: Color(0xFF10B981),
                      ),
                      Container(
                        width: 1.5,
                        height: 24,
                        color: Colors.grey.shade300,
                      ),
                      const Icon(
                        Icons.location_on,
                        size: 14,
                        color: AppColors.accent,
                      ),
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
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            fontSize: 12,
                            color: AppColors.textSecondary,
                          ),
                        ),
                        const SizedBox(height: 12),
                        Text(
                          order.deliveryAddress.isNotEmpty
                              ? order.deliveryAddress
                              : 'Điểm nhận hàng',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            fontSize: 13,
                            fontWeight: FontWeight.w600,
                            color: AppColors.textPrimary,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),

              const SizedBox(height: 12),

              // Live Driver / Action Bar
              Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 12,
                  vertical: 10,
                ),
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(color: const Color(0xFFE2E8F0)),
                ),
                child: Row(
                  children: [
                    const Icon(
                      Icons.navigation_rounded,
                      color: AppColors.primary,
                      size: 18,
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        trackingState.driverInfo != null
                            ? '${trackingState.driverInfo!['full_name']} • ${trackingState.driverInfo!['license_plate']}'
                            : 'Đang kết nối tài xế...',
                        style: const TextStyle(
                          fontSize: 12,
                          fontWeight: FontWeight.w500,
                          color: AppColors.textPrimary,
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                    Row(
                      mainAxisSize: MainAxisSize.min,
                      children: const [
                        Text(
                          'Xem lộ trình',
                          style: TextStyle(
                            fontSize: 12,
                            fontWeight: FontWeight.bold,
                            color: AppColors.primary,
                          ),
                        ),
                        SizedBox(width: 4),
                        Icon(
                          Icons.arrow_forward_ios_rounded,
                          size: 12,
                          color: AppColors.primary,
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildOrderStatusPill(OrderStatus status) {
    final (Color bg, Color text, String label) = switch (status) {
      OrderStatus.pendingPayment => (
        const Color(0xFFFEF3C7),
        const Color(0xFFD97706),
        'Chờ thanh toán',
      ),
      OrderStatus.paid => (
        const Color(0xFFEFF6FF),
        const Color(0xFF2563EB),
        'Đang tìm tài xế',
      ),
      OrderStatus.planned => (
        const Color(0xFFE0E7FF),
        const Color(0xFF4F46E5),
        'Đã phân tài xế',
      ),
      OrderStatus.inTransit => (
        const Color(0xFFDCFCE7),
        const Color(0xFF16A34A),
        'Đang giao hàng',
      ),
      OrderStatus.delivered => (
        const Color(0xFFDCFCE7),
        const Color(0xFF15803D),
        'Đã giao hàng',
      ),
      _ => (
        const Color(0xFFF3F4F6),
        const Color(0xFF4B5563),
        status.displayName,
      ),
    };

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Text(
        label,
        style: TextStyle(
          fontSize: 11,
          fontWeight: FontWeight.w700,
          color: text,
        ),
      ),
    );
  }

  void _showActiveOrderNotice(
    BuildContext context,
    OrderTrackingState trackingState,
  ) {
    final orderId = trackingState.order?.id ?? '';
    final shortId = orderId.length > 8 ? orderId.substring(0, 8) : orderId;

    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        title: Row(
          children: const [
            Icon(Icons.info_outline_rounded, color: AppColors.primary),
            SizedBox(width: 8),
            Text(
              'Đơn hàng đang xử lý',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
            ),
          ],
        ),
        content: Text(
          'Bạn đang có đơn hàng #$shortId đang được xử lý.\n'
          'SwiftRoute hiện chỉ cho phép đặt tối đa 1 đơn hàng hoạt động tại một thời điểm.\n\n'
          'Vui lòng theo dõi hoặc chờ đơn hàng hoàn thành trước khi đặt đơn mới.',
          style: const TextStyle(fontSize: 14, height: 1.4),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(),
            child: const Text('Đóng'),
          ),
          ElevatedButton.icon(
            onPressed: () {
              Navigator.of(ctx).pop();
              Navigator.of(context).push(
                MaterialPageRoute(builder: (_) => const OrderTrackingScreen()),
              );
            },
            style: ElevatedButton.styleFrom(
              backgroundColor: AppColors.primary,
              foregroundColor: Colors.white,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(8),
              ),
            ),
            icon: const Icon(Icons.navigation_rounded, size: 16),
            label: const Text('Xem lộ trình đơn'),
          ),
        ],
      ),
    );
  }

  /// Promotional / CTA Hero card for SwiftRoute on-demand deliveries.
  Widget _buildExpressBanner(
    BuildContext context,
    OrderTrackingState trackingState,
  ) {
    final hasActiveOrder = trackingState.hasActiveOrder;
    final order = trackingState.order;
    final shortId = (order != null && order.id.length > 8)
        ? order.id.substring(0, 8)
        : (order?.id ?? '');

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          colors: [Color(0xFF2F3C7E), Color(0xFF1E2756)],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          BoxShadow(
            color: AppColors.primary.withValues(alpha: 0.25),
            blurRadius: 10,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 8,
                    vertical: 3,
                  ),
                  decoration: BoxDecoration(
                    color: AppColors.accent,
                    borderRadius: BorderRadius.circular(6),
                  ),
                  child: Text(
                    hasActiveOrder ? '1 ĐƠN ĐANG CHẠY' : 'GIAO SIÊU TỐC',
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 10,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  hasActiveOrder
                      ? 'Đơn hàng #$shortId đang được xử lý'
                      : 'Giao hàng hỏa tốc trong 30 phút',
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 16,
                    fontWeight: FontWeight.bold,
                    height: 1.2,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  hasActiveOrder
                      ? 'Nhấn để theo dõi vị trí và tiến độ giao hàng trực tiếp'
                      : 'Tối ưu tuyến đường thông minh AI • Phí chỉ từ 15.000 đ',
                  style: const TextStyle(color: Colors.white70, fontSize: 12),
                ),
                const SizedBox(height: 12),
                ElevatedButton.icon(
                  onPressed: () {
                    if (hasActiveOrder) {
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) => const OrderTrackingScreen(),
                        ),
                      );
                    } else {
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) => const CreateOrderScreen(),
                        ),
                      );
                    }
                  },
                  icon: Icon(
                    hasActiveOrder
                        ? Icons.navigation_rounded
                        : Icons.arrow_forward_rounded,
                    size: 16,
                  ),
                  label: Text(
                    hasActiveOrder ? 'Xem đơn đang giao' : 'Tạo đơn ngay',
                  ),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.white,
                    foregroundColor: AppColors.primary,
                    textStyle: const TextStyle(
                      fontWeight: FontWeight.bold,
                      fontSize: 13,
                    ),
                    padding: const EdgeInsets.symmetric(
                      horizontal: 14,
                      vertical: 8,
                    ),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(8),
                    ),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(width: 12),
          Container(
            width: 72,
            height: 72,
            decoration: BoxDecoration(
              color: Colors.white.withValues(alpha: 0.12),
              shape: BoxShape.circle,
            ),
            child: const Icon(
              Icons.two_wheeler_rounded,
              color: Colors.white,
              size: 42,
            ),
          ),
        ],
      ),
    );
  }

  /// Live search input for filtering store name or address.
  Widget _buildSearchBar(HomeNotifier homeNotifier) {
    return Container(
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: AppColors.divider),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.03),
            blurRadius: 4,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: TextField(
        controller: _searchController,
        onChanged: (text) => homeNotifier.setSearchQuery(text),
        decoration: InputDecoration(
          hintText: 'Tìm kiếm cửa hàng, nhà hàng, quán cafe...',
          hintStyle: const TextStyle(fontSize: 14, color: AppColors.textHint),
          prefixIcon: const Icon(Icons.search, color: AppColors.textSecondary),
          suffixIcon: _searchController.text.isNotEmpty
              ? IconButton(
                  icon: const Icon(Icons.clear, size: 18),
                  onPressed: () {
                    _searchController.clear();
                    homeNotifier.setSearchQuery('');
                  },
                )
              : null,
          border: InputBorder.none,
          contentPadding: const EdgeInsets.symmetric(vertical: 14),
        ),
      ),
    );
  }

  /// Horizontal category picker chips (All, Restaurant, Cafe, Retail, Pharmacy).
  Widget _buildCategoryFilter(HomeState homeState, HomeNotifier homeNotifier) {
    final categories = [
      (null, 'Tất cả', Icons.all_inclusive_rounded),
      (BusinessType.restaurant, 'Nhà hàng', Icons.restaurant_rounded),
      (BusinessType.cafe, 'Quán Cafe', Icons.local_cafe_rounded),
      (BusinessType.retail, 'Cửa hàng bán lẻ', Icons.storefront_rounded),
      (BusinessType.pharmacy, 'Nhà thuốc', Icons.local_pharmacy_rounded),
    ];

    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: Row(
        children: categories.map((cat) {
          final isSelected = homeState.selectedCategory == cat.$1;
          return Padding(
            padding: const EdgeInsets.only(right: 8),
            child: FilterChip(
              avatar: Icon(
                cat.$3,
                size: 16,
                color: isSelected ? Colors.white : AppColors.primary,
              ),
              label: Text(cat.$2),
              selected: isSelected,
              onSelected: (_) => homeNotifier.selectCategory(cat.$1),
              backgroundColor: Colors.white,
              selectedColor: AppColors.primary,
              checkmarkColor: Colors.white,
              labelStyle: TextStyle(
                fontSize: 13,
                fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                color: isSelected ? Colors.white : AppColors.textPrimary,
              ),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(20),
                side: BorderSide(
                  color: isSelected ? AppColors.primary : AppColors.divider,
                ),
              ),
              padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 6),
            ),
          );
        }).toList(),
      ),
    );
  }

  /// Card item representing a nearby approved store / tenant.
  Widget _buildStoreCard(
    BuildContext context,
    TenantModel tenant,
    HomeState homeState,
    OrderTrackingState trackingState,
  ) {
    final distKm = HomeNotifier.calculateDistanceKm(
      homeState.currentLat,
      homeState.currentLng,
      tenant.pickupLat,
      tenant.pickupLng,
    );

    final categoryDetails = _getCategoryVisuals(tenant.businessType);

    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: AppColors.divider),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.03),
            blurRadius: 6,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Category Icon Badge
            Container(
              width: 48,
              height: 48,
              decoration: BoxDecoration(
                color: categoryDetails.bgColor,
                borderRadius: BorderRadius.circular(12),
              ),
              child: Icon(
                categoryDetails.icon,
                color: categoryDetails.iconColor,
                size: 26,
              ),
            ),
            const SizedBox(width: 12),

            // Store Info
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          tenant.businessName,
                          style: const TextStyle(
                            fontSize: 15,
                            fontWeight: FontWeight.bold,
                            color: AppColors.textPrimary,
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 6,
                          vertical: 2,
                        ),
                        decoration: BoxDecoration(
                          color: AppColors.surface,
                          borderRadius: BorderRadius.circular(4),
                        ),
                        child: Text(
                          '${distKm.toStringAsFixed(1)} km',
                          style: const TextStyle(
                            fontSize: 11,
                            fontWeight: FontWeight.w600,
                            color: AppColors.primary,
                          ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 4),

                  // Category tag
                  Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 6,
                      vertical: 2,
                    ),
                    decoration: BoxDecoration(
                      color: categoryDetails.bgColor,
                      borderRadius: BorderRadius.circular(4),
                    ),
                    child: Text(
                      tenant.businessType.displayName,
                      style: TextStyle(
                        fontSize: 11,
                        fontWeight: FontWeight.w600,
                        color: categoryDetails.iconColor,
                      ),
                    ),
                  ),
                  const SizedBox(height: 6),

                  // Store Address
                  if (tenant.address != null)
                    Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Icon(
                          Icons.place_outlined,
                          size: 14,
                          color: AppColors.textSecondary,
                        ),
                        const SizedBox(width: 4),
                        Expanded(
                          child: Text(
                            tenant.address!,
                            style: const TextStyle(
                              fontSize: 12,
                              color: AppColors.textSecondary,
                            ),
                            maxLines: 2,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                      ],
                    ),
                  const SizedBox(height: 10),

                  // Action Button: Order from this store
                  Align(
                    alignment: Alignment.centerRight,
                    child: OutlinedButton.icon(
                      onPressed: () {
                        if (trackingState.hasActiveOrder) {
                          _showActiveOrderNotice(context, trackingState);
                          return;
                        }

                        // Prefill store pickup address into create order form
                        ref
                            .read(orderFormProvider.notifier)
                            .setPickupLocation(
                              address: tenant.address ?? tenant.businessName,
                              lat: tenant.pickupLat,
                              lng: tenant.pickupLng,
                              tenantId: tenant.id,
                            );

                        Navigator.push(
                          context,
                          MaterialPageRoute(
                            builder: (_) => const CreateOrderScreen(),
                          ),
                        );
                      },
                      icon: const Icon(
                        Icons.delivery_dining_rounded,
                        size: 16,
                        color: AppColors.accent,
                      ),
                      label: const Text(
                        'Đặt giao từ đây',
                        style: TextStyle(
                          fontSize: 12,
                          fontWeight: FontWeight.bold,
                          color: AppColors.accent,
                        ),
                      ),
                      style: OutlinedButton.styleFrom(
                        side: const BorderSide(color: AppColors.accent),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(8),
                        ),
                        padding: const EdgeInsets.symmetric(
                          horizontal: 10,
                          vertical: 4,
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  /// Visual theme for business categories.
  ({Color bgColor, Color iconColor, IconData icon}) _getCategoryVisuals(
    BusinessType type,
  ) {
    switch (type) {
      case BusinessType.restaurant:
        return (
          bgColor: const Color(0xFFFFECE8),
          iconColor: const Color(0xFFFF5722),
          icon: Icons.restaurant_rounded,
        );
      case BusinessType.cafe:
        return (
          bgColor: const Color(0xFFFFF4E5),
          iconColor: const Color(0xFFFF9800),
          icon: Icons.local_cafe_rounded,
        );
      case BusinessType.retail:
        return (
          bgColor: const Color(0xFFE8F0FE),
          iconColor: const Color(0xFF1976D2),
          icon: Icons.storefront_rounded,
        );
      case BusinessType.pharmacy:
        return (
          bgColor: const Color(0xFFE6F4EA),
          iconColor: const Color(0xFF2E7D32),
          icon: Icons.local_pharmacy_rounded,
        );
      case BusinessType.other:
        return (
          bgColor: const Color(0xFFF3F4F6),
          iconColor: const Color(0xFF6B7280),
          icon: Icons.business_rounded,
        );
    }
  }

  /// Empty state when no stores match the search query or category filter.
  Widget _buildEmptyState(HomeNotifier homeNotifier) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 40),
      child: Center(
        child: Column(
          children: [
            Container(
              padding: const EdgeInsets.all(16),
              decoration: const BoxDecoration(
                color: Colors.white,
                shape: BoxShape.circle,
              ),
              child: const Icon(
                Icons.search_off_rounded,
                size: 40,
                color: AppColors.textHint,
              ),
            ),
            const SizedBox(height: 12),
            const Text(
              'Không tìm thấy cửa hàng phù hợp',
              style: TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.bold,
                color: AppColors.textPrimary,
              ),
            ),
            const SizedBox(height: 6),
            const Text(
              'Hãy thử tìm kiếm với từ khóa khác hoặc xóa bộ lọc.',
              style: TextStyle(fontSize: 13, color: AppColors.textSecondary),
            ),
            const SizedBox(height: 16),
            TextButton.icon(
              onPressed: () {
                _searchController.clear();
                homeNotifier.setSearchQuery('');
                homeNotifier.selectCategory(null);
              },
              icon: const Icon(Icons.refresh_rounded, size: 16),
              label: const Text('Xem tất cả cửa hàng'),
            ),
          ],
        ),
      ),
    );
  }
}
