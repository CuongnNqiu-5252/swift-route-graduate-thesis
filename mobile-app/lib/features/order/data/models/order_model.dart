// features/order/data/models/order_model.dart

enum OrderStatus {
  pendingPayment,
  paymentFailed,
  paid,
  planned,
  inTransit,
  delivered,
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
        return 'Đã phân công tài xế';
      case OrderStatus.inTransit:
        return 'Đang giao hàng';
      case OrderStatus.delivered:
        return 'Giao thành công';
      case OrderStatus.failed:
        return 'Giao thất bại';
      case OrderStatus.cancelled:
        return 'Đã huỷ';
    }
  }
}

class OrderModel {
  final String id;
  final String tenantId;
  final String? tenantName;
  final String customerId;
  final double pickupLat;
  final double pickupLng;
  final double deliveryLat;
  final double deliveryLng;
  final String pickupAddress;
  final String deliveryAddress;
  final DateTime timeWindowStart;
  final DateTime timeWindowEnd;
  final double weightKg;
  final String priority;
  final OrderStatus status;
  final String? assignedDriverId;
  final DateTime createdAt;

  const OrderModel({
    required this.id,
    required this.tenantId,
    this.tenantName,
    required this.customerId,
    required this.pickupLat,
    required this.pickupLng,
    required this.deliveryLat,
    required this.deliveryLng,
    required this.pickupAddress,
    required this.deliveryAddress,
    required this.timeWindowStart,
    required this.timeWindowEnd,
    required this.weightKg,
    required this.priority,
    required this.status,
    this.assignedDriverId,
    required this.createdAt,
  });

  factory OrderModel.fromJson(Map<String, dynamic> json) {
    return OrderModel(
      id: json['order_id'] as String? ?? json['id'] as String? ?? '',
      tenantId: json['tenant_id'] as String? ?? '',
      tenantName: json['tenant_name'] as String?,
      customerId: json['customer_id'] as String? ?? '',
      pickupLat: (json['pickup_lat'] as num?)?.toDouble() ?? 0.0,
      pickupLng: (json['pickup_lng'] as num?)?.toDouble() ?? 0.0,
      deliveryLat: (json['delivery_lat'] as num?)?.toDouble() ?? 0.0,
      deliveryLng: (json['delivery_lng'] as num?)?.toDouble() ?? 0.0,
      pickupAddress: json['pickup_address'] as String? ?? '',
      deliveryAddress: json['delivery_address'] as String? ?? '',
      timeWindowStart: DateTime.tryParse(json['time_window_start'] as String? ?? '') ?? DateTime.now(),
      timeWindowEnd: DateTime.tryParse(json['time_window_end'] as String? ?? '') ?? DateTime.now().add(const Duration(hours: 4)),
      weightKg: (json['weight_kg'] as num?)?.toDouble() ?? 1.0,
      priority: json['priority'] as String? ?? 'NORMAL',
      status: OrderStatus.fromString(json['status'] as String?),
      assignedDriverId: json['assigned_driver_id'] as String?,
      createdAt: DateTime.tryParse(json['created_at'] as String? ?? '') ?? DateTime.now(),
    );
  }

  OrderModel copyWith({
    String? id,
    String? tenantId,
    String? tenantName,
    String? customerId,
    double? pickupLat,
    double? pickupLng,
    double? deliveryLat,
    double? deliveryLng,
    String? pickupAddress,
    String? deliveryAddress,
    DateTime? timeWindowStart,
    DateTime? timeWindowEnd,
    double? weightKg,
    String? priority,
    OrderStatus? status,
    String? assignedDriverId,
    DateTime? createdAt,
  }) {
    return OrderModel(
      id: id ?? this.id,
      tenantId: tenantId ?? this.tenantId,
      tenantName: tenantName ?? this.tenantName,
      customerId: customerId ?? this.customerId,
      pickupLat: pickupLat ?? this.pickupLat,
      pickupLng: pickupLng ?? this.pickupLng,
      deliveryLat: deliveryLat ?? this.deliveryLat,
      deliveryLng: deliveryLng ?? this.deliveryLng,
      pickupAddress: pickupAddress ?? this.pickupAddress,
      deliveryAddress: deliveryAddress ?? this.deliveryAddress,
      timeWindowStart: timeWindowStart ?? this.timeWindowStart,
      timeWindowEnd: timeWindowEnd ?? this.timeWindowEnd,
      weightKg: weightKg ?? this.weightKg,
      priority: priority ?? this.priority,
      status: status ?? this.status,
      assignedDriverId: assignedDriverId ?? this.assignedDriverId,
      createdAt: createdAt ?? this.createdAt,
    );
  }
}
