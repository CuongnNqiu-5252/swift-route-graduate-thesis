class DriverEarningModel {
  const DriverEarningModel({
    required this.id,
    required this.orderId,
    required this.distanceKm,
    required this.baseAmount,
    required this.distanceAmount,
    required this.bonusAmount,
    required this.adjustmentAmount,
    required this.netAmount,
    required this.status,
    required this.completedAt,
  });

  final String id;
  final String orderId;
  final double distanceKm;
  final double baseAmount;
  final double distanceAmount;
  final double bonusAmount;
  final double adjustmentAmount;
  final double netAmount;
  final String status;
  final DateTime completedAt;

  factory DriverEarningModel.fromJson(Map<String, dynamic> json) {
    double amount(String key) => (json[key] as num?)?.toDouble() ?? 0;
    return DriverEarningModel(
      id: json['id']?.toString() ?? '',
      orderId: json['order_id']?.toString() ?? '',
      distanceKm: amount('payable_distance_km'),
      baseAmount: amount('base_amount'),
      distanceAmount: amount('distance_amount'),
      bonusAmount: amount('bonus_amount'),
      adjustmentAmount: amount('adjustment_amount'),
      netAmount: amount('net_amount'),
      status: json['status']?.toString() ?? 'PENDING',
      completedAt:
          DateTime.tryParse(json['completed_at']?.toString() ?? '') ??
          DateTime.fromMillisecondsSinceEpoch(0),
    );
  }
}

class DriverEarningSummary {
  const DriverEarningSummary({
    required this.periodStart,
    required this.periodEnd,
    required this.pendingAmount,
    required this.approvedAmount,
    required this.paidAmount,
    required this.totalAmount,
    required this.deliveryCount,
    required this.currency,
  });

  final DateTime periodStart;
  final DateTime periodEnd;
  final double pendingAmount;
  final double approvedAmount;
  final double paidAmount;
  final double totalAmount;
  final int deliveryCount;
  final String currency;

  factory DriverEarningSummary.fromJson(Map<String, dynamic> json) {
    double amount(String key) => (json[key] as num?)?.toDouble() ?? 0;
    return DriverEarningSummary(
      periodStart:
          DateTime.tryParse(json['period_start']?.toString() ?? '') ??
          DateTime.now(),
      periodEnd:
          DateTime.tryParse(json['period_end']?.toString() ?? '') ??
          DateTime.now(),
      pendingAmount: amount('pending_amount'),
      approvedAmount: amount('approved_amount'),
      paidAmount: amount('paid_amount'),
      totalAmount: amount('total_amount'),
      deliveryCount: (json['delivery_count'] as num?)?.toInt() ?? 0,
      currency: json['currency']?.toString() ?? 'VND',
    );
  }
}

class DriverEarningsBundle {
  const DriverEarningsBundle({required this.summary, required this.earnings});
  final DriverEarningSummary summary;
  final List<DriverEarningModel> earnings;
}
