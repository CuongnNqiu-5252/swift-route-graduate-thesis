// features/driver/data/models/driver_notification_model.dart

class DriverNotificationModel {
  final String id;
  final String templateCode;
  final String category;
  final String severity;
  final String title;
  final String body;
  final String? incidentId;
  final String? orderId;
  final String? dispatchPlanId;
  final DateTime createdAt;

  const DriverNotificationModel({
    required this.id,
    required this.templateCode,
    required this.category,
    required this.severity,
    required this.title,
    required this.body,
    this.incidentId,
    this.orderId,
    this.dispatchPlanId,
    required this.createdAt,
  });

  factory DriverNotificationModel.fromJson(Map<String, dynamic> json) {
    final rawCreatedAt =
        (json['created_at'] ?? json['createdAt'])?.toString() ?? '';
    return DriverNotificationModel(
      id: (json['id'] ?? '').toString(),
      templateCode:
          (json['template_code'] ?? json['templateCode'] ?? '').toString(),
      category: (json['category'] ?? 'SYSTEM').toString(),
      severity: (json['severity'] ?? 'LOW').toString(),
      title: (json['title'] ?? 'Thông báo vận hành').toString(),
      body: (json['body'] ?? '').toString(),
      incidentId: (json['incident_id'] ?? json['incidentId'])?.toString(),
      orderId: (json['order_id'] ?? json['orderId'])?.toString(),
      dispatchPlanId:
          (json['dispatch_plan_id'] ?? json['dispatchPlanId'])?.toString(),
      createdAt: DateTime.tryParse(rawCreatedAt) ?? DateTime.now(),
    );
  }
}

