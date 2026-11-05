// features/order/data/models/order_model.dart

import 'package:swiftroute_app/core/utils/formatters.dart';

enum OrderStatus {
  pendingPayment,
  paymentFailed,
  paid,
  planned,
  inTransit,
  delivered,
  returned,
  failed,
  cancelled;

  static OrderStatus fromString(String? value) {
    switch (value?.toUpperCase()) {
      case 'PENDING_PAYMENT':
        return OrderStatus.pendingPayment;
      case 'PAYMENT_FAILED':
        return OrderStatus.paymentFailed;
      case 'PAID':
        return OrderStatus.paid;
      case 'PLANNED':
        return OrderStatus.planned;
      case 'IN_TRANSIT':
        return OrderStatus.inTransit;
      case 'DELIVERED':
        return OrderStatus.delivered;
      case 'RETURNED':
        return OrderStatus.returned;
      case 'FAILED':
        return OrderStatus.failed;
      case 'CANCELLED':
        return OrderStatus.cancelled;
      default:
        return OrderStatus.pendingPayment;
    }
  }

  String toServerString() {
    switch (this) {
      case OrderStatus.pendingPayment:
        return 'PENDING_PAYMENT';
      case OrderStatus.paymentFailed:
        return 'PAYMENT_FAILED';
      case OrderStatus.paid:
        return 'PAID';
      case OrderStatus.planned:
        return 'PLANNED';
      case OrderStatus.inTransit:
        return 'IN_TRANSIT';
      case OrderStatus.delivered:
        return 'DELIVERED';
      case OrderStatus.returned:
        return 'RETURNED';
      case OrderStatus.failed:
        return 'FAILED';
      case OrderStatus.cancelled:
        return 'CANCELLED';
    }
  }

  String get displayName {
    switch (this) {
      case OrderStatus.pendingPayment:
        return 'Chờ thanh toán';
      case OrderStatus.paymentFailed:
        return 'Thanh toán thất bại';
      case OrderStatus.paid:
        return 'Đang tìm tài xế';
      case OrderStatus.planned:
        return 'Đã phân công';
      case OrderStatus.inTransit:
        return 'Đang giao hàng';
      case OrderStatus.delivered:
        return 'Đã giao';
      case OrderStatus.returned:
        return 'Đã hoàn kho';
      case OrderStatus.failed:
        return 'Giao thất bại';
      case OrderStatus.cancelled:
        return 'Đã huỷ';
    }
  }
}

enum DeliveryTaskStatus {
  assigned,
  accepted,
  arrived,
  pickedUp,
  outForDelivery,
  completed,
  failed,
  cancelled;

  static DeliveryTaskStatus? fromString(String? value) =>
      switch (value?.toUpperCase()) {
        'ASSIGNED' => DeliveryTaskStatus.assigned,
        'ACCEPTED' => DeliveryTaskStatus.accepted,
        'ARRIVED' => DeliveryTaskStatus.arrived,
        'PICKED_UP' => DeliveryTaskStatus.pickedUp,
        'OUT_FOR_DELIVERY' => DeliveryTaskStatus.outForDelivery,
        'COMPLETED' => DeliveryTaskStatus.completed,
        'FAILED' => DeliveryTaskStatus.failed,
        'CANCELLED' => DeliveryTaskStatus.cancelled,
        _ => null,
      };

  String get serverValue => switch (this) {
    DeliveryTaskStatus.assigned => 'ASSIGNED',
    DeliveryTaskStatus.accepted => 'ACCEPTED',
    DeliveryTaskStatus.arrived => 'ARRIVED',
    DeliveryTaskStatus.pickedUp => 'PICKED_UP',
    DeliveryTaskStatus.outForDelivery => 'OUT_FOR_DELIVERY',
    DeliveryTaskStatus.completed => 'COMPLETED',
    DeliveryTaskStatus.failed => 'FAILED',
    DeliveryTaskStatus.cancelled => 'CANCELLED',
  };

  String get displayName => switch (this) {
    DeliveryTaskStatus.assigned => 'Chờ nhận chuyến',
    DeliveryTaskStatus.accepted => 'Chờ lấy hàng',
    DeliveryTaskStatus.arrived => 'Tại điểm lấy',
    DeliveryTaskStatus.pickedUp => 'Đã lấy hàng',
    DeliveryTaskStatus.outForDelivery => 'Đang đi giao',
    DeliveryTaskStatus.completed => 'Đã hoàn tất',
    DeliveryTaskStatus.failed => 'Cần xử lý',
    DeliveryTaskStatus.cancelled => 'Đã huỷ',
  };
}

class OrderModel {
  final String id;
  final String tenantId;
  final String? tenantName;
  final String? externalOrderCode;
  final String customerId;
  final String? senderName;
  final String? senderPhone;
  final String? recipientName;
  final String? recipientPhone;
  final String? pickupLocationId;
  final String? pickupLocationName;
  final double pickupLat;
  final double pickupLng;
  final double deliveryLat;
  final double deliveryLng;
  final String pickupAddress;
  final String deliveryAddress;
  final DateTime timeWindowStart;
  final DateTime timeWindowEnd;
  final double weightKg;
  final int packageCount;
  final String? packageDescription;
  final String? notes;
  final String priority;
  final double codAmount;
  final double deliveryFee;
  final OrderStatus status;
  final DeliveryTaskStatus? deliveryTaskStatus;
  final String? deliveryTaskId;
  final String deliveryTaskType;
  final String? assignedDriverId;
  final DateTime createdAt;

  const OrderModel({
    required this.id,
    required this.tenantId,
    this.tenantName,
    this.externalOrderCode,
    required this.customerId,
    this.senderName,
    this.senderPhone,
    this.recipientName,
    this.recipientPhone,
    this.pickupLocationId,
    this.pickupLocationName,
    required this.pickupLat,
    required this.pickupLng,
    required this.deliveryLat,
    required this.deliveryLng,
    required this.pickupAddress,
    required this.deliveryAddress,
    required this.timeWindowStart,
    required this.timeWindowEnd,
    required this.weightKg,
    this.packageCount = 1,
    this.packageDescription,
    this.notes,
    required this.priority,
    this.codAmount = 0,
    this.deliveryFee = 0,
    required this.status,
    this.deliveryTaskStatus,
    this.deliveryTaskId,
    this.deliveryTaskType = 'DELIVERY',
    this.assignedDriverId,
    required this.createdAt,
  });

  /// Human-readable waybill code (uses shop/ERP order code if present).
  String get displayCode => AppFormatters.shortCode(externalOrderCode, id);

  /// Stable grouping key for grouping multiple orders at the same warehouse/depot.
  String get pickupGroupKey {
    if (pickupLocationId != null && pickupLocationId!.isNotEmpty) {
      return pickupLocationId!;
    }
    return '${pickupLat.toStringAsFixed(4)},${pickupLng.toStringAsFixed(4)}';
  }

  /// Friendly warehouse / sender label for pickup stops.
  String get pickupDisplayName {
    if (pickupLocationName != null && pickupLocationName!.trim().isNotEmpty) {
      return pickupLocationName!.trim();
    }
    if (senderName != null && senderName!.trim().isNotEmpty) {
      return senderName!.trim();
    }
    if (tenantName != null && tenantName!.trim().isNotEmpty) {
      return 'Kho ${tenantName!.trim()}';
    }
    return 'Điểm lấy hàng';
  }

  /// True when this is a forward delivery order whose physical package has not been picked up yet.
  bool get needsPickup =>
      deliveryTaskType != 'RETURN' &&
      (deliveryTaskStatus ?? DeliveryTaskStatus.assigned).index <
          DeliveryTaskStatus.pickedUp.index;

  /// True when the package is on the vehicle and ready to be delivered to the customer.
  bool get isOutForDelivery =>
      deliveryTaskType != 'RETURN' &&
      (deliveryTaskStatus == DeliveryTaskStatus.pickedUp ||
          deliveryTaskStatus == DeliveryTaskStatus.outForDelivery);

  /// True when this task represents a Return-to-Sender (RTS) trip back to the warehouse.
  bool get isReturnTask => deliveryTaskType == 'RETURN';

  factory OrderModel.fromJson(Map<String, dynamic> json) {
    return OrderModel(
      id: json['order_id'] as String? ?? json['id'] as String? ?? '',
      tenantId: json['tenant_id'] as String? ?? '',
      tenantName: json['tenant_name'] as String?,
      externalOrderCode:
          json['external_order_code'] as String? ??
          json['order_code'] as String?,
      customerId: json['customer_id'] as String? ?? '',
      senderName: json['sender_name'] as String?,
      senderPhone: json['sender_phone'] as String?,
      recipientName: json['recipient_name'] as String?,
      recipientPhone: json['recipient_phone'] as String?,
      pickupLocationId: json['pickup_location_id'] as String?,
      pickupLocationName: json['pickup_location_name'] as String?,
      pickupLat: (json['pickup_lat'] as num?)?.toDouble() ?? 0.0,
      pickupLng: (json['pickup_lng'] as num?)?.toDouble() ?? 0.0,
      deliveryLat: (json['delivery_lat'] as num?)?.toDouble() ?? 0.0,
      deliveryLng: (json['delivery_lng'] as num?)?.toDouble() ?? 0.0,
      pickupAddress: json['pickup_address'] as String? ?? '',
      deliveryAddress: json['delivery_address'] as String? ?? '',
      timeWindowStart:
          DateTime.tryParse(json['time_window_start'] as String? ?? '') ??
          DateTime.now(),
      timeWindowEnd:
          DateTime.tryParse(json['time_window_end'] as String? ?? '') ??
          DateTime.now().add(const Duration(hours: 4)),
      weightKg: (json['weight_kg'] as num?)?.toDouble() ?? 1.0,
      packageCount:
          (json['package_count'] as num?)?.toInt() ??
          (json['packageCount'] as num?)?.toInt() ??
          1,
      packageDescription: json['package_description'] as String?,
      notes: json['notes'] as String?,
      priority: json['priority'] as String? ?? 'NORMAL',
      codAmount:
          (json['cod_amount'] as num?)?.toDouble() ??
          (json['codAmount'] as num?)?.toDouble() ??
          0,
      deliveryFee: (json['delivery_fee'] as num?)?.toDouble() ?? 0,
      status: OrderStatus.fromString(json['status'] as String?),
      deliveryTaskStatus: DeliveryTaskStatus.fromString(
        json['delivery_task_status'] as String? ??
            json['deliveryTaskStatus'] as String?,
      ),
      deliveryTaskId:
          json['delivery_task_id'] as String? ??
          json['deliveryTaskId'] as String?,
      deliveryTaskType:
          json['delivery_task_type'] as String? ??
          json['deliveryTaskType'] as String? ??
          'DELIVERY',
      assignedDriverId: json['assigned_driver_id'] as String?,
      createdAt:
          DateTime.tryParse(json['created_at'] as String? ?? '') ??
          DateTime.now(),
    );
  }

  OrderModel copyWith({
    String? id,
    String? tenantId,
    String? tenantName,
    String? externalOrderCode,
    String? customerId,
    String? senderName,
    String? senderPhone,
    String? recipientName,
    String? recipientPhone,
    String? pickupLocationId,
    String? pickupLocationName,
    double? pickupLat,
    double? pickupLng,
    double? deliveryLat,
    double? deliveryLng,
    String? pickupAddress,
    String? deliveryAddress,
    DateTime? timeWindowStart,
    DateTime? timeWindowEnd,
    double? weightKg,
    int? packageCount,
    String? packageDescription,
    String? notes,
    String? priority,
    double? codAmount,
    double? deliveryFee,
    OrderStatus? status,
    DeliveryTaskStatus? deliveryTaskStatus,
    String? deliveryTaskId,
    String? deliveryTaskType,
    String? assignedDriverId,
    DateTime? createdAt,
  }) {
    return OrderModel(
      id: id ?? this.id,
      tenantId: tenantId ?? this.tenantId,
      tenantName: tenantName ?? this.tenantName,
      externalOrderCode: externalOrderCode ?? this.externalOrderCode,
      customerId: customerId ?? this.customerId,
      senderName: senderName ?? this.senderName,
      senderPhone: senderPhone ?? this.senderPhone,
      recipientName: recipientName ?? this.recipientName,
      recipientPhone: recipientPhone ?? this.recipientPhone,
      pickupLocationId: pickupLocationId ?? this.pickupLocationId,
      pickupLocationName: pickupLocationName ?? this.pickupLocationName,
      pickupLat: pickupLat ?? this.pickupLat,
      pickupLng: pickupLng ?? this.pickupLng,
      deliveryLat: deliveryLat ?? this.deliveryLat,
      deliveryLng: deliveryLng ?? this.deliveryLng,
      pickupAddress: pickupAddress ?? this.pickupAddress,
      deliveryAddress: deliveryAddress ?? this.deliveryAddress,
      timeWindowStart: timeWindowStart ?? this.timeWindowStart,
      timeWindowEnd: timeWindowEnd ?? this.timeWindowEnd,
      weightKg: weightKg ?? this.weightKg,
      packageCount: packageCount ?? this.packageCount,
      packageDescription: packageDescription ?? this.packageDescription,
      notes: notes ?? this.notes,
      priority: priority ?? this.priority,
      codAmount: codAmount ?? this.codAmount,
      deliveryFee: deliveryFee ?? this.deliveryFee,
      status: status ?? this.status,
      deliveryTaskStatus: deliveryTaskStatus ?? this.deliveryTaskStatus,
      deliveryTaskId: deliveryTaskId ?? this.deliveryTaskId,
      deliveryTaskType: deliveryTaskType ?? this.deliveryTaskType,
      assignedDriverId: assignedDriverId ?? this.assignedDriverId,
      createdAt: createdAt ?? this.createdAt,
    );
  }
}
