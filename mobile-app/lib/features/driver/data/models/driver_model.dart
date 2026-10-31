// features/driver/data/models/driver_model.dart
//
// Maps the backend DriverResponse (snake_case JSON) to a domain-friendly Dart model.
// Backend response shape:
// {
//   "id", "tenant_id", "user_id", "full_name", "phone", "license_plate",
//   "vehicle_type", "capacity", "driver_type", "shift_start", "shift_end",
//   "current_lat", "current_lng", "is_available", "status"
// }

class DriverModel {
  final String id;
  final String? tenantId;
  final String? userId;
  final String fullName;
  final String phone;
  final String licensePlate;
  final String vehicleType;  // MOTORCYCLE | CAR | TRUCK | VAN
  final int? capacity;
  final String driverType;   // FULL_TIME | PART_TIME | FREELANCE
  final String? shiftStart;  // "HH:mm:ss"
  final String? shiftEnd;
  final double? currentLat;
  final double? currentLng;
  final bool isAvailable;
  final String status;       // ACTIVE | SUSPENDED | INACTIVE

  const DriverModel({
    required this.id,
    this.tenantId,
    this.userId,
    required this.fullName,
    required this.phone,
    required this.licensePlate,
    required this.vehicleType,
    this.capacity,
    required this.driverType,
    this.shiftStart,
    this.shiftEnd,
    this.currentLat,
    this.currentLng,
    required this.isAvailable,
    required this.status,
  });

  factory DriverModel.fromJson(Map<String, dynamic> json) {
    return DriverModel(
      id: (json['id'] ?? json['driver_id'] ?? '').toString(),
      tenantId: json['tenant_id'] as String?,
      userId: json['user_id'] as String?,
      fullName: json['full_name'] as String? ?? '',
      phone: json['phone'] as String? ?? '',
      licensePlate: json['license_plate'] as String? ?? '',
      vehicleType: json['vehicle_type'] as String? ?? 'CAR',
      capacity: (json['capacity'] as num?)?.toInt(),
      driverType: json['driver_type'] as String? ?? 'FULL_TIME',
      shiftStart: json['shift_start'] as String?,
      shiftEnd: json['shift_end'] as String?,
      currentLat: (json['current_lat'] as num?)?.toDouble(),
      currentLng: (json['current_lng'] as num?)?.toDouble(),
      isAvailable: json['is_available'] as bool? ?? false,
      status: json['status'] as String? ?? 'ACTIVE',
    );
  }

  /// Human-readable vehicle type label
  String get vehicleTypeLabel => switch (vehicleType) {
        'MOTORCYCLE' => 'Xe máy',
        'CAR' => 'Ô tô',
        'TRUCK' => 'Xe tải',
        'VAN' => 'Van',
        _ => vehicleType,
      };

  /// Human-readable driver type label
  String get driverTypeLabel => switch (driverType) {
        'FULL_TIME' => 'Toàn thời gian',
        'PART_TIME' => 'Bán thời gian',
        'FREELANCE' => 'Tự do',
        _ => driverType,
      };

  /// Shift hours formatted as "08:00 - 17:00"
  String get shiftDisplay {
    if (shiftStart == null || shiftEnd == null) return 'Chua xep ca';
    // Backend sends "HH:mm:ss", we show only HH:mm
    final start = shiftStart!.substring(0, 5);
    final end = shiftEnd!.substring(0, 5);
    return '$start - $end';
  }

  DriverModel copyWith({bool? isAvailable}) {
    return DriverModel(
      id: id,
      tenantId: tenantId,
      userId: userId,
      fullName: fullName,
      phone: phone,
      licensePlate: licensePlate,
      vehicleType: vehicleType,
      capacity: capacity,
      driverType: driverType,
      shiftStart: shiftStart,
      shiftEnd: shiftEnd,
      currentLat: currentLat,
      currentLng: currentLng,
      isAvailable: isAvailable ?? this.isAvailable,
      status: status,
    );
  }
}
