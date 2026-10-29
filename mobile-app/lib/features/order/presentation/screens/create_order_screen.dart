// features/order/presentation/screens/create_order_screen.dart

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/theme/app_colors.dart';
import 'package:swiftroute_app/core/utils/polyline_decoder.dart';
import 'package:swiftroute_app/features/order/application/order_form_provider.dart';
import 'package:swiftroute_app/features/order/application/order_tracking_provider.dart';
import 'package:swiftroute_app/features/order/data/datasources/order_remote_data_source.dart';
import 'package:swiftroute_app/features/order/data/models/order_model.dart';
import 'package:swiftroute_app/features/payment/data/datasources/payment_remote_data_source.dart';
import 'package:swiftroute_app/features/order/presentation/screens/order_tracking_screen.dart';
import 'package:swiftroute_app/features/order/presentation/widgets/address_search_sheet.dart';
import 'package:swiftroute_app/features/order/presentation/widgets/goong_map_widget.dart';
import 'package:swiftroute_app/widget/layout/app_header.dart';

/// Screen allowing customers to select pickup/delivery locations via GPS & Goong,
/// view real-time motorbike routing, calculate shipping fees, and place orders.
class CreateOrderScreen extends ConsumerWidget {
  const CreateOrderScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final formState = ref.watch(orderFormProvider);
    final formNotifier = ref.read(orderFormProvider.notifier);

    // Convert form coordinates to LatLngPoint for map
    final pickupPoint =
        formState.pickupLat != null && formState.pickupLng != null
        ? LatLngPoint(formState.pickupLat!, formState.pickupLng!)
        : null;

    final deliveryPoint =
        formState.deliveryLat != null && formState.deliveryLng != null
        ? LatLngPoint(formState.deliveryLat!, formState.deliveryLng!)
        : null;

    final routePoints = formState.direction?.points ?? [];

    return Scaffold(
      appBar: const Header(),
      body: Column(
        children: [
          // ── Map View (38% screen height) ──────────────────────────────────
          SizedBox(
            height: MediaQuery.of(context).size.height * 0.38,
            child: Stack(
              children: [
                GoongMapWidget(
                  pickupLocation: pickupPoint,
                  deliveryLocation: deliveryPoint,
                  routePoints: routePoints,
                ),

                // Map quick legend
                Positioned(
                  top: 12,
                  left: 12,
                  child: Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 10,
                      vertical: 6,
                    ),
                    decoration: BoxDecoration(
                      color: Colors.white.withAlpha(230),
                      borderRadius: BorderRadius.circular(8),
                      boxShadow: [
                        BoxShadow(
                          color: Colors.black.withAlpha(20),
                          blurRadius: 4,
                        ),
                      ],
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Icon(
                          Icons.circle,
                          color: AppColors.success,
                          size: 10,
                        ),
                        const SizedBox(width: 4),
                        const Text('Lấy hàng', style: TextStyle(fontSize: 11)),
                        const SizedBox(width: 10),
                        const Icon(
                          Icons.circle,
                          color: AppColors.error,
                          size: 10,
                        ),
                        const SizedBox(width: 4),
                        const Text('Giao hàng', style: TextStyle(fontSize: 11)),
                      ],
                    ),
                  ),
                ),

                // Route calculating indicator overlay
                if (formState.isCalculatingRoute)
                  Positioned(
                    bottom: 12,
                    left: 20,
                    right: 20,
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                        vertical: 8,
                        horizontal: 16,
                      ),
                      decoration: BoxDecoration(
                        color: AppColors.primary.withAlpha(230),
                        borderRadius: BorderRadius.circular(20),
                      ),
                      child: const Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          SizedBox(
                            width: 14,
                            height: 14,
                            child: CircularProgressIndicator(
                              color: Colors.white,
                              strokeWidth: 2,
                            ),
                          ),
                          SizedBox(width: 10),
                          Text(
                            'Goong đang tính tuyến đường xe máy...',
                            style: TextStyle(color: Colors.white, fontSize: 12),
                          ),
                        ],
                      ),
                    ),
                  ),
              ],
            ),
          ),

          // ── Bottom Order Input Sheet ──────────────────────────────────────
          Expanded(
            child: Container(
              decoration: const BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black12,
                    blurRadius: 10,
                    offset: Offset(0, -3),
                  ),
                ],
              ),
              child: ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  // Error alert banner
                  if (formState.errorMessage != null)
                    Container(
                      margin: const EdgeInsets.only(bottom: 12),
                      padding: const EdgeInsets.all(10),
                      decoration: BoxDecoration(
                        color: AppColors.error.withAlpha(25),
                        borderRadius: BorderRadius.circular(8),
                        border: Border.all(
                          color: AppColors.error.withAlpha(80),
                        ),
                      ),
                      child: Row(
                        children: [
                          const Icon(
                            Icons.error_outline,
                            color: AppColors.error,
                            size: 18,
                          ),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              formState.errorMessage!,
                              style: const TextStyle(
                                color: AppColors.error,
                                fontSize: 13,
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),

                  // 1. Điểm lấy hàng (Pickup)
                  _buildLocationTile(
                    context: context,
                    icon: Icons.storefront,
                    iconColor: AppColors.success,
                    label: 'ĐIỂM LẤY HÀNG (NGƯỜI GỬI)',
                    value:
                        formState.pickupAddress ?? 'Chạm để chọn điểm lấy hàng',
                    isPlaceholder: formState.pickupAddress == null,
                    trailing: ElevatedButton.icon(
                      onPressed: formState.isLoadingLocation
                          ? null
                          : () => formNotifier.useCurrentLocationAsPickup(),
                      icon: formState.isLoadingLocation
                          ? const SizedBox(
                              width: 12,
                              height: 12,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : const Icon(Icons.my_location, size: 14),
                      label: const Text(
                        'GPS máy',
                        style: TextStyle(fontSize: 11),
                      ),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: AppColors.primary,
                        foregroundColor: Colors.white,
                        padding: const EdgeInsets.symmetric(
                          horizontal: 10,
                          vertical: 6,
                        ),
                        minimumSize: Size.zero,
                        tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                      ),
                    ),
                    onTap: () async {
                      final place = await AddressSearchSheet.show(
                        context,
                        title: 'Chọn điểm lấy hàng',
                      );
                      if (place != null) {
                        formNotifier.setPickupLocation(
                          address: place.formattedAddress,
                          lat: place.lat,
                          lng: place.lng,
                        );
                      }
                    },
                  ),

                  const SizedBox(height: 12),

                  // 2. Điểm giao hàng (Delivery)
                  _buildLocationTile(
                    context: context,
                    icon: Icons.location_on,
                    iconColor: AppColors.error,
                    label: 'ĐIỂM GIAO HÀNG (NGƯỜI NHẬN)',
                    value:
                        formState.deliveryAddress ??
                        'Chạm để nhập địa chỉ giao hàng',
                    isPlaceholder: formState.deliveryAddress == null,
                    onTap: () async {
                      final place = await AddressSearchSheet.show(
                        context,
                        title: 'Chọn điểm giao hàng',
                      );
                      if (place != null) {
                        formNotifier.setDeliveryLocation(
                          address: place.formattedAddress,
                          lat: place.lat,
                          lng: place.lng,
                        );
                      }
                    },
                  ),

                  const Divider(height: 24, color: AppColors.divider),

                  // 3. Khối lượng & Ưu tiên
                  Row(
                    children: [
                      // Trọng lượng
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Text(
                              'TRỌNG LƯỢNG (KG)',
                              style: TextStyle(
                                fontSize: 11,
                                fontWeight: FontWeight.bold,
                                color: AppColors.textSecondary,
                              ),
                            ),
                            const SizedBox(height: 6),
                            Row(
                              children: [
                                _weightButton(
                                  icon: Icons.remove,
                                  onPressed: () => formNotifier.setWeight(
                                    (formState.weightKg - 0.5).clamp(0.5, 30.0),
                                  ),
                                ),
                                Padding(
                                  padding: const EdgeInsets.symmetric(
                                    horizontal: 12,
                                  ),
                                  child: Text(
                                    '${formState.weightKg.toStringAsFixed(1)} kg',
                                    style: const TextStyle(
                                      fontSize: 15,
                                      fontWeight: FontWeight.bold,
                                    ),
                                  ),
                                ),
                                _weightButton(
                                  icon: Icons.add,
                                  onPressed: () => formNotifier.setWeight(
                                    (formState.weightKg + 0.5).clamp(0.5, 30.0),
                                  ),
                                ),
                              ],
                            ),
                          ],
                        ),
                      ),

                      // Độ ưu tiên
                      Column(
                        crossAxisAlignment: CrossAxisAlignment.end,
                        children: [
                          const Text(
                            'ƯU TIÊN GIAO',
                            style: TextStyle(
                              fontSize: 11,
                              fontWeight: FontWeight.bold,
                              color: AppColors.textSecondary,
                            ),
                          ),
                          const SizedBox(height: 6),
                          ChoiceChip(
                            label: const Text(
                              'Hỏa tốc',
                              style: TextStyle(fontSize: 12),
                            ),
                            selected: formState.priority == 'URGENT',
                            selectedColor: AppColors.accent.withAlpha(50),
                            onSelected: (selected) {
                              formNotifier.setPriority(
                                selected ? 'URGENT' : 'NORMAL',
                              );
                            },
                          ),
                        ],
                      ),
                    ],
                  ),

                  const SizedBox(height: 16),

                  // 4. Card báo giá cước phí & cự ly Goong
                  if (formState.direction != null &&
                      formState.estimatedFee != null)
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: AppColors.surface,
                        borderRadius: BorderRadius.circular(12),
                        border: Border.all(color: AppColors.divider),
                      ),
                      child: Row(
                        children: [
                          Container(
                            padding: const EdgeInsets.all(8),
                            decoration: BoxDecoration(
                              color: AppColors.accent.withAlpha(30),
                              shape: BoxShape.circle,
                            ),
                            child: const Icon(
                              Icons.two_wheeler,
                              color: AppColors.accent,
                              size: 22,
                            ),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  'Khoảng cách: ${formState.direction!.distanceText} (~${formState.direction!.durationText})',
                                  style: const TextStyle(
                                    fontSize: 12,
                                    color: AppColors.textSecondary,
                                  ),
                                ),
                                const SizedBox(height: 2),
                                Text(
                                  '${_formatCurrency(formState.estimatedFee!)} đ',
                                  style: const TextStyle(
                                    fontSize: 18,
                                    fontWeight: FontWeight.bold,
                                    color: AppColors.primary,
                                  ),
                                ),
                              ],
                            ),
                          ),
                          const Icon(
                            Icons.check_circle,
                            color: AppColors.success,
                            size: 20,
                          ),
                        ],
                      ),
                    ),

                  const SizedBox(height: 20),

                  // 5. Nút bấm Xác nhận đặt đơn
                  SizedBox(
                    width: double.infinity,
                    height: 50,
                    child: ElevatedButton(
                      onPressed: formState.canSubmit
                          ? () async {
                              final createdOrder = await formNotifier
                                  .submitOrder();
                              if (createdOrder != null && context.mounted) {
                                final paidOrder = await _showPaymentSheet(
                                  context,
                                  ref,
                                  createdOrder,
                                  formState.estimatedFee ?? 0,
                                );
                                if (paidOrder == null || !context.mounted) {
                                  return;
                                }
                                // Initialize tracking with order and Goong route points
                                ref
                                    .read(orderTrackingProvider.notifier)
                                    .initTracking(
                                      order: paidOrder,
                                      direction: formState.direction,
                                    );

                                // Navigate to Live Tracking screen
                                Navigator.of(context).push(
                                  MaterialPageRoute(
                                    builder: (_) => const OrderTrackingScreen(),
                                  ),
                                );
                              }
                            }
                          : null,
                      style: ElevatedButton.styleFrom(
                        backgroundColor: AppColors.accent,
                        disabledBackgroundColor: Colors.grey.shade300,
                        foregroundColor: Colors.white,
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(12),
                        ),
                        elevation: formState.canSubmit ? 3 : 0,
                      ),
                      child: formState.isSubmitting
                          ? const SizedBox(
                              width: 22,
                              height: 22,
                              child: CircularProgressIndicator(
                                color: Colors.white,
                                strokeWidth: 2.5,
                              ),
                            )
                          : Text(
                              formState.estimatedFee != null
                                  ? 'ĐẶT GIAO NGAY • ${_formatCurrency(formState.estimatedFee!)} đ'
                                  : 'XÁC NHẬN ĐẶT GIAO HÀNG',
                              style: const TextStyle(
                                fontSize: 15,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Future<OrderModel?> _showPaymentSheet(
    BuildContext context,
    WidgetRef ref,
    OrderModel order,
    double amount,
  ) {
    return showModalBottomSheet<OrderModel>(
      context: context,
      isDismissible: false,
      enableDrag: false,
      builder: (sheetContext) =>
          _MockPaymentSheet(order: order, amount: amount, ref: ref),
    );
  }

  Widget _buildLocationTile({
    required BuildContext context,
    required IconData icon,
    required Color iconColor,
    required String label,
    required String value,
    required bool isPlaceholder,
    Widget? trailing,
    required VoidCallback onTap,
  }) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(12),
      child: Container(
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: AppColors.surface,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: AppColors.divider),
        ),
        child: Row(
          children: [
            Icon(icon, color: iconColor, size: 24),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    label,
                    style: const TextStyle(
                      fontSize: 10,
                      fontWeight: FontWeight.bold,
                      color: AppColors.textSecondary,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    value,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      fontSize: 13,
                      fontWeight: isPlaceholder
                          ? FontWeight.normal
                          : FontWeight.w600,
                      color: isPlaceholder
                          ? AppColors.textHint
                          : AppColors.textPrimary,
                    ),
                  ),
                ],
              ),
            ),
            if (trailing != null) trailing,
          ],
        ),
      ),
    );
  }

  Widget _weightButton({
    required IconData icon,
    required VoidCallback onPressed,
  }) {
    return InkWell(
      onTap: onPressed,
      borderRadius: BorderRadius.circular(8),
      child: Container(
        padding: const EdgeInsets.all(6),
        decoration: BoxDecoration(
          color: AppColors.surfaceVariant,
          borderRadius: BorderRadius.circular(8),
        ),
        child: Icon(icon, size: 16, color: AppColors.textPrimary),
      ),
    );
  }

  String _formatCurrency(double amount) {
    return amount
        .toStringAsFixed(0)
        .replaceAllMapped(
          RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'),
          (Match m) => '${m[1]}.',
        );
  }
}

class _MockPaymentSheet extends StatefulWidget {
  const _MockPaymentSheet({
    required this.order,
    required this.amount,
    required this.ref,
  });

  final OrderModel order;
  final double amount;
  final WidgetRef ref;

  @override
  State<_MockPaymentSheet> createState() => _MockPaymentSheetState();
}

class _MockPaymentSheetState extends State<_MockPaymentSheet> {
  var _isPaying = false;
  String? _error;

  Future<void> _confirm() async {
    setState(() {
      _isPaying = true;
      _error = null;
    });

    try {
      final paymentDataSource = widget.ref.read(paymentRemoteDataSourceProvider);
      final payment = await paymentDataSource.createMockPayment(
        orderId: widget.order.id,
        amount: widget.amount,
      );
      final completedPayment = await paymentDataSource.confirmMockPayment(
        payment.id,
      );
      if (completedPayment.status != 'SUCCESS') {
        throw StateError('Payment was not completed');
      }
      if (mounted) {
        Navigator.of(context).pop(widget.order.copyWith(status: OrderStatus.paid));
      }
    } catch (_) {
      if (mounted) {
        setState(() {
          _isPaying = false;
          _error = 'Thanh toán chưa được xác nhận. Vui lòng thử lại.';
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(20, 12, 20, 24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Center(
              child: Container(
                width: 36,
                height: 4,
                decoration: BoxDecoration(
                  color: AppColors.divider,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
            const SizedBox(height: 20),
            const Text(
              'Xác nhận thanh toán',
              style: TextStyle(fontSize: 20, fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: 6),
            const Text(
              'Chế độ mô phỏng VNPay cho phiên demo.',
              style: TextStyle(color: AppColors.textSecondary),
            ),
            const SizedBox(height: 20),
            Container(
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: AppColors.surface,
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: AppColors.divider),
              ),
              child: Row(
                children: [
                  const Icon(Icons.receipt_long_outlined),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text('Đơn #${widget.order.id.length > 8 ? widget.order.id.substring(0, 8) : widget.order.id}'),
                  ),
                  Text('${widget.amount.toStringAsFixed(0)} đ'),
                ],
              ),
            ),
            if (_error != null) ...[
              const SizedBox(height: 12),
              Text(_error!, style: const TextStyle(color: AppColors.error)),
            ],
            const SizedBox(height: 20),
            SizedBox(
              width: double.infinity,
              height: 48,
              child: FilledButton(
                onPressed: _isPaying ? null : _confirm,
                style: FilledButton.styleFrom(backgroundColor: AppColors.textPrimary),
                child: _isPaying
                    ? const SizedBox(
                        width: 20,
                        height: 20,
                        child: CircularProgressIndicator(
                          color: Colors.white,
                          strokeWidth: 2,
                        ),
                      )
                    : const Text('Thanh toán (Mock VNPay)'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
