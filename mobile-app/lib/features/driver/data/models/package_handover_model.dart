class PackageHandoverModel {
  const PackageHandoverModel({
    required this.id,
    required this.fromDriverId,
    required this.toDriverId,
    required this.status,
    required this.reason,
    required this.orderCode,
  });

  final String id;
  final String fromDriverId;
  final String toDriverId;
  final String status;
  final String reason;
  final String orderCode;

  factory PackageHandoverModel.fromJson(Map<String, dynamic> json) {
    return PackageHandoverModel(
      id: (json['handover_id'] ?? json['handoverId'] ?? '').toString(),
      fromDriverId: (json['from_driver_id'] ?? json['fromDriverId'] ?? '')
          .toString(),
      toDriverId: (json['to_driver_id'] ?? json['toDriverId'] ?? '').toString(),
      status: (json['status'] ?? '').toString(),
      reason: (json['reason'] ?? '').toString(),
      orderCode:
          (json['order_code'] ??
                  json['orderCode'] ??
                  json['order_id'] ??
                  json['orderId'] ??
                  '')
              .toString(),
    );
  }
}
