// features/order/data/models/order_model.dart

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
        return 'Đã phân công tài xế';
      case OrderStatus.inTransit:
        return 'Đang giao hàng';
      case OrderStatus.delivered:
        return 'Giao thành công';
      case OrderStatus.returned:
        return 'Đã hoàn về';
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

  static DeliveryTaskStatus? fromString(String? value) => switch (value?.toUpperCase()) {
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
        DeliveryTaskStatus.assigned => 'Chờ tài xế nhận',
        DeliveryTaskStatus.accepted => 'Đã nhận nhiệm vụ',
        DeliveryTaskStatus.arrived => 'Đã đến điểm lấy',
        DeliveryTaskStatus.pickedUp => 'Đã nhận kiện hàng',
        DeliveryTaskStatus.outForDelivery => 'Đang giao hàng',
        DeliveryTaskStatus.completed => 'Đã giao thành công',
        DeliveryTaskStatus.failed => 'Giao thất bại',
        DeliveryTaskStatus.cancelled => 'Đã huỷ',
      };
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
  final int packageCount;
  final String priority;
  final double codAmount;
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
    this.packageCount = 1,
    required this.priority,
    this.codAmount = 0,
    required this.status,
    this.deliveryTaskStatus,
    this.deliveryTaskId,
    this.deliveryTaskType = 'DELIVERY',
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
      packageCount: (json['package_count'] as num?)?.toInt() ?? (json['packageCount'] as num?)?.toInt() ?? 1,
      priority: json['priority'] as String? ?? 'NORMAL',
      codAmount: (json['cod_amount'] as num?)?.toDouble() ?? (json['codAmount'] as num?)?.toDouble() ?? 0,
      status: OrderStatus.fromString(json['status'] as String?),
      deliveryTaskStatus: DeliveryTaskStatus.fromString(json['delivery_task_status'] as String? ?? json['deliveryTaskStatus'] as String?),
      deliveryTaskId: json['delivery_task_id'] as String? ?? json['deliveryTaskId'] as String?,
      deliveryTaskType: json['delivery_task_type'] as String? ?? json['deliveryTaskType'] as String? ?? 'DELIVERY',
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
    int? packageCount,
    String? priority,
    double? codAmount,
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
      packageCount: packageCount ?? this.packageCount,
      priority: priority ?? this.priority,
      codAmount: codAmount ?? this.codAmount,
      status: status ?? this.status,
      deliveryTaskStatus: deliveryTaskStatus ?? this.deliveryTaskStatus,
      deliveryTaskId: deliveryTaskId ?? this.deliveryTaskId,
      deliveryTaskType: deliveryTaskType ?? this.deliveryTaskType,
      assignedDriverId: assignedDriverId ?? this.assignedDriverId,
      createdAt: createdAt ?? this.createdAt,
    );
  }
}
