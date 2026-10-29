enum BusinessType {
  restaurant,
  cafe,
  retail,
  pharmacy,
  other;

  static BusinessType fromString(String? value) {
    switch (value?.toUpperCase()) {
      case 'RESTAURANT':
        return BusinessType.restaurant;
      case 'CAFE':
        return BusinessType.cafe;
      case 'RETAIL':
        return BusinessType.retail;
      case 'PHARMACY':
        return BusinessType.pharmacy;
      default:
        return BusinessType.other;
    }
  }

  String toServerString() {
    switch (this) {
      case BusinessType.restaurant:
        return 'RESTAURANT';
      case BusinessType.cafe:
        return 'CAFE';
      case BusinessType.retail:
        return 'RETAIL';
      case BusinessType.pharmacy:
        return 'PHARMACY';
      case BusinessType.other:
        return 'OTHER';
    }
  }

  String get displayName {
    switch (this) {
      case BusinessType.restaurant:
        return 'Nhà hàng';
      case BusinessType.cafe:
        return 'Quán Cafe';
      case BusinessType.retail:
        return 'Cửa hàng bán lẻ';
      case BusinessType.pharmacy:
        return 'Nhà thuốc';
      case BusinessType.other:
        return 'Khác';
    }
  }
}

enum TenantStatus {
  pending,
  approved,
  rejected,
  suspended;

  static TenantStatus fromString(String? value) {
    switch (value?.toUpperCase()) {
      case 'APPROVED':
        return TenantStatus.approved;
      case 'REJECTED':
        return TenantStatus.rejected;
      case 'SUSPENDED':
        return TenantStatus.suspended;
      default:
        return TenantStatus.pending;
    }
  }

  String toServerString() {
    switch (this) {
      case TenantStatus.pending:
        return 'PENDING';
      case TenantStatus.approved:
        return 'APPROVED';
      case TenantStatus.rejected:
        return 'REJECTED';
      case TenantStatus.suspended:
        return 'SUSPENDED';
    }
  }

  String get displayName {
    switch (this) {
      case TenantStatus.pending:
        return 'Chờ duyệt';
      case TenantStatus.approved:
        return 'Đang hoạt động';
      case TenantStatus.rejected:
        return 'Bị từ chối';
      case TenantStatus.suspended:
        return 'Đã tạm ngưng';
    }
  }
}

enum SubscriptionPlan {
  freeTrial,
  basic,
  premium;

  static SubscriptionPlan fromString(String? value) {
    switch (value?.toUpperCase()) {
      case 'BASIC':
        return SubscriptionPlan.basic;
      case 'PREMIUM':
        return SubscriptionPlan.premium;
      default:
        return SubscriptionPlan.freeTrial;
    }
  }

  String toServerString() {
    switch (this) {
      case SubscriptionPlan.freeTrial:
        return 'FREE_TRIAL';
      case SubscriptionPlan.basic:
        return 'BASIC';
      case SubscriptionPlan.premium:
        return 'PREMIUM';
    }
  }

  String get displayName {
    switch (this) {
      case SubscriptionPlan.freeTrial:
        return 'Dùng thử';
      case SubscriptionPlan.basic:
        return 'Gói Cơ bản';
      case SubscriptionPlan.premium:
        return 'Gói Cao cấp';
    }
  }
}

class TenantModel {
  final String id;
  final String businessName;
  final String? ownerUserId;
  final BusinessType businessType;
  final String? address;
  final double pickupLat;
  final double pickupLng;
  final TenantStatus status;
  final SubscriptionPlan subscriptionPlan;
  final String? approvalReason;
  final DateTime? createdAt;
  final DateTime? statusUpdatedAt;

  const TenantModel({
    required this.id,
    required this.businessName,
    this.ownerUserId,
    required this.businessType,
    this.address,
    required this.pickupLat,
    required this.pickupLng,
    required this.status,
    required this.subscriptionPlan,
    this.approvalReason,
    this.createdAt,
    this.statusUpdatedAt,
  });

  factory TenantModel.fromJson(Map<String, dynamic> json) {
    return TenantModel(
      id: json['tenant_id'] as String? ?? json['id'] as String? ?? '',
      businessName: json['business_name'] as String? ?? '',
      ownerUserId: json['owner_user_id'] as String?,
      businessType: BusinessType.fromString(json['business_type'] as String?),
      address: json['address'] as String?,
      pickupLat: (json['pickup_lat'] as num?)?.toDouble() ?? 0.0,
      pickupLng: (json['pickup_lng'] as num?)?.toDouble() ?? 0.0,
      status: TenantStatus.fromString(json['status'] as String?),
      subscriptionPlan:
          SubscriptionPlan.fromString(json['subscription_plan'] as String?),
      approvalReason: json['approval_reason'] as String?,
      createdAt: json['created_at'] != null
          ? DateTime.tryParse(json['created_at'] as String)
          : null,
      statusUpdatedAt: json['status_updated_at'] != null
          ? DateTime.tryParse(json['status_updated_at'] as String)
          : null,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'tenant_id': id,
      'business_name': businessName,
      'owner_user_id': ownerUserId,
      'business_type': businessType.toServerString(),
      'address': address,
      'pickup_lat': pickupLat,
      'pickup_lng': pickupLng,
      'status': status.toServerString(),
      'subscription_plan': subscriptionPlan.toServerString(),
      'approval_reason': approvalReason,
      'created_at': createdAt?.toIso8601String(),
      'status_updated_at': statusUpdatedAt?.toIso8601String(),
    };
  }

  TenantModel copyWith({
    String? id,
    String? businessName,
    String? ownerUserId,
    BusinessType? businessType,
    String? address,
    double? pickupLat,
    double? pickupLng,
    TenantStatus? status,
    SubscriptionPlan? subscriptionPlan,
    String? approvalReason,
    DateTime? createdAt,
    DateTime? statusUpdatedAt,
  }) {
    return TenantModel(
      id: id ?? this.id,
      businessName: businessName ?? this.businessName,
      ownerUserId: ownerUserId ?? this.ownerUserId,
      businessType: businessType ?? this.businessType,
      address: address ?? this.address,
      pickupLat: pickupLat ?? this.pickupLat,
      pickupLng: pickupLng ?? this.pickupLng,
      status: status ?? this.status,
      subscriptionPlan: subscriptionPlan ?? this.subscriptionPlan,
      approvalReason: approvalReason ?? this.approvalReason,
      createdAt: createdAt ?? this.createdAt,
      statusUpdatedAt: statusUpdatedAt ?? this.statusUpdatedAt,
    );
  }
}
