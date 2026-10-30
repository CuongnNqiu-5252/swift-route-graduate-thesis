// features/routing/data/models/optimization_result_model.dart

class LocationPointModel {
  final double lat;
  final double lng;
  final String? address;

  const LocationPointModel({
    required this.lat,
    required this.lng,
    this.address,
  });

  factory LocationPointModel.fromJson(Map<String, dynamic> json) {
    return LocationPointModel(
      lat: (json['lat'] as num).toDouble(),
      lng: (json['lng'] as num).toDouble(),
      address: json['address'] as String?,
    );
  }

  Map<String, dynamic> toJson() => {
    'lat': lat,
    'lng': lng,
    if (address != null) 'address': address,
  };
}

class OrderedStopModel {
  final int stopIndex;
  final String stopId;
  final LocationPointModel location;
  final double cumulativeDistanceKm;
  final double estimatedArrivalMinutes;

  const OrderedStopModel({
    required this.stopIndex,
    required this.stopId,
    required this.location,
    required this.cumulativeDistanceKm,
    required this.estimatedArrivalMinutes,
  });

  factory OrderedStopModel.fromJson(Map<String, dynamic> json) {
    return OrderedStopModel(
      stopIndex: (json['stop_index'] as num).toInt(),
      stopId: json['stop_id'] as String? ?? '',
      location: LocationPointModel.fromJson(
        json['location'] as Map<String, dynamic>,
      ),
      cumulativeDistanceKm:
          (json['cumulative_distance_km'] as num).toDouble(),
      estimatedArrivalMinutes:
          (json['estimated_arrival_minutes'] as num).toDouble(),
    );
  }
}

class OptimizationResultModel {
  final String jobId;
  final String algorithmUsed;
  final double totalDistanceKm;
  final double totalDurationMinutes;
  final List<OrderedStopModel> orderedStops;
  final double executionTimeMs;
  final String timestamp;

  const OptimizationResultModel({
    required this.jobId,
    required this.algorithmUsed,
    required this.totalDistanceKm,
    required this.totalDurationMinutes,
    required this.orderedStops,
    required this.executionTimeMs,
    required this.timestamp,
  });

  factory OptimizationResultModel.fromJson(Map<String, dynamic> json) {
    final stops = json['ordered_stops'] as List<dynamic>? ?? [];
    return OptimizationResultModel(
      jobId: json['job_id'] as String? ?? '',
      algorithmUsed: json['algorithm_used'] as String? ?? 'auto',
      totalDistanceKm: (json['total_distance_km'] as num?)?.toDouble() ?? 0.0,
      totalDurationMinutes:
          (json['total_duration_minutes'] as num?)?.toDouble() ?? 0.0,
      orderedStops: stops
          .map((s) => OrderedStopModel.fromJson(s as Map<String, dynamic>))
          .toList(),
      executionTimeMs: (json['execution_time_ms'] as num?)?.toDouble() ?? 0.0,
      timestamp: json['timestamp'] as String? ?? '',
    );
  }
}

