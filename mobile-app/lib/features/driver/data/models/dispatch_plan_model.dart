class DispatchPlanModel {
  final String planId;
  final String tenantId;
  final String planStatus;
  final String? driverId;
  final String? algorithmUsed;
  final double totalDistanceKm;
  final int estimatedDurationMinutes;
  final String? triggerType;
  final int orderCount;
  final List<String> orderedStopIds;
  final DateTime? dispatchedAt;
  final DateTime? ackDeadlineAt;
  final DateTime? acknowledgedAt;
  final DateTime createdAt;

  const DispatchPlanModel({
    required this.planId,
    required this.tenantId,
    required this.planStatus,
    this.driverId,
    this.algorithmUsed,
    required this.totalDistanceKm,
    required this.estimatedDurationMinutes,
    this.triggerType,
    required this.orderCount,
    required this.orderedStopIds,
    this.dispatchedAt,
    this.ackDeadlineAt,
    this.acknowledgedAt,
    required this.createdAt,
  });

  bool get isAcknowledged => acknowledgedAt != null;

  factory DispatchPlanModel.fromJson(Map<String, dynamic> json) {
    final rawStops = json['ordered_stop_ids'] ?? json['orderedStopIds'];
    final stopIds = rawStops is List
        ? rawStops.map((e) => e.toString()).toList()
        : <String>[];

    return DispatchPlanModel(
      planId: (json['plan_id'] ?? json['planId'] ?? '').toString(),
      tenantId: (json['tenant_id'] ?? json['tenantId'] ?? '').toString(),
      planStatus: (json['plan_status'] ?? json['planStatus'] ?? 'DISPATCHED')
          .toString(),
      driverId: (json['driver_id'] ?? json['driverId'])?.toString(),
      algorithmUsed: (json['algorithm_used'] ?? json['algorithmUsed'])
          ?.toString(),
      totalDistanceKm: (json['total_distance_km'] as num?)?.toDouble() ?? 0.0,
      estimatedDurationMinutes:
          (json['estimated_duration_minutes'] as num?)?.toInt() ?? 0,
      triggerType: (json['trigger_type'] ?? json['triggerType'])?.toString(),
      orderCount: (json['order_count'] as num?)?.toInt() ?? stopIds.length,
      orderedStopIds: stopIds,
      dispatchedAt: _parseDate(json['dispatched_at'] ?? json['dispatchedAt']),
      ackDeadlineAt: _parseDate(
        json['ack_deadline_at'] ?? json['ackDeadlineAt'],
      ),
      acknowledgedAt: _parseDate(
        json['acknowledged_at'] ?? json['acknowledgedAt'],
      ),
      createdAt:
          _parseDate(json['created_at'] ?? json['createdAt']) ?? DateTime.now(),
    );
  }

  Map<String, dynamic> toJson() => {
    'plan_id': planId,
    'tenant_id': tenantId,
    'plan_status': planStatus,
    'driver_id': driverId,
    'algorithm_used': algorithmUsed,
    'total_distance_km': totalDistanceKm,
    'estimated_duration_minutes': estimatedDurationMinutes,
    'trigger_type': triggerType,
    'order_count': orderCount,
    'ordered_stop_ids': orderedStopIds,
    'dispatched_at': dispatchedAt?.toUtc().toIso8601String(),
    'ack_deadline_at': ackDeadlineAt?.toUtc().toIso8601String(),
    'acknowledged_at': acknowledgedAt?.toUtc().toIso8601String(),
    'created_at': createdAt.toUtc().toIso8601String(),
  };

  DispatchPlanModel copyWith({String? planStatus, DateTime? acknowledgedAt}) =>
      DispatchPlanModel(
        planId: planId,
        tenantId: tenantId,
        planStatus: planStatus ?? this.planStatus,
        driverId: driverId,
        algorithmUsed: algorithmUsed,
        totalDistanceKm: totalDistanceKm,
        estimatedDurationMinutes: estimatedDurationMinutes,
        triggerType: triggerType,
        orderCount: orderCount,
        orderedStopIds: orderedStopIds,
        dispatchedAt: dispatchedAt,
        ackDeadlineAt: ackDeadlineAt,
        acknowledgedAt: acknowledgedAt ?? this.acknowledgedAt,
        createdAt: createdAt,
      );

  static DateTime? _parseDate(dynamic value) {
    if (value == null) return null;
    if (value is String && value.isNotEmpty) {
      return DateTime.tryParse(value);
    }
    return null;
  }
}
