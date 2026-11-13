class PackageHandoverModel {
  const PackageHandoverModel({
    required this.id,
    required this.fromDriverId,
    required this.toDriverId,
    required this.status,
    required this.reason,
    required this.packageCodes,
  });

  final String id;
  final String fromDriverId;
  final String toDriverId;
  final String status;
  final String reason;
  final List<String> packageCodes;

  factory PackageHandoverModel.fromJson(Map<String, dynamic> json) {
    final packages = json['packages'] is List
        ? json['packages'] as List
        : const <dynamic>[];
    return PackageHandoverModel(
      id: (json['handover_id'] ?? json['handoverId'] ?? '').toString(),
      fromDriverId: (json['from_driver_id'] ?? json['fromDriverId'] ?? '')
          .toString(),
      toDriverId: (json['to_driver_id'] ?? json['toDriverId'] ?? '').toString(),
      status: (json['status'] ?? '').toString(),
      reason: (json['reason'] ?? '').toString(),
      packageCodes: packages
          .whereType<Map>()
          .map(
            (item) =>
                (item['package_code'] ?? item['packageCode'] ?? '').toString(),
          )
          .where((code) => code.isNotEmpty)
          .toList(),
    );
  }
}
