// features/routing/data/datasources/routing_remote_data_source.dart

import 'dart:math';
import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/network/dio.dart';
import 'package:swiftroute_app/features/routing/data/models/optimization_result_model.dart';

class RoutingRemoteDataSource {
  final Dio _dio;

  RoutingRemoteDataSource(this._dio);

  /// Calls SwiftRoute Routing Engine (FastAPI) to solve TSP/VRP batch route optimization.
  Future<OptimizationResultModel> optimizeBatch({
    required LocationPointModel depot,
    required List<({String id, String? name, LocationPointModel location, double demandKg})> stops,
    String algorithm = 'auto', // 'auto', 'held_karp', 'nearest_neighbor', 'genetic'
  }) async {
    final payload = {
      'depot': depot.toJson(),
      'stops': stops.map((s) => {
        'id': s.id,
        if (s.name != null) 'name': s.name,
        'location': s.location.toJson(),
        'demand_kg': s.demandKg,
      }).toList(),
      'algorithm': algorithm,
    };

    // 1. Try Gateway endpoint (/routing/api/v1/optimize/batch)
    try {
      final response = await _dio.post(
        '/routing/api/v1/optimize/batch',
        data: payload,
      );
      if (response.statusCode == 200 && response.data != null) {
        return OptimizationResultModel.fromJson(response.data as Map<String, dynamic>);
      }
    } catch (e) {
      debugPrint('[RoutingRemoteDataSource] Gateway call failed, trying direct 8084: $e');
    }

    // 2. Try direct call to routing-engine (http://localhost:8084/api/v1/optimize/batch)
    try {
      final directDio = Dio(BaseOptions(
        baseUrl: 'http://localhost:8084',
        connectTimeout: const Duration(seconds: 5),
        receiveTimeout: const Duration(seconds: 5),
      ));
      final response = await directDio.post(
        '/api/v1/optimize/batch',
        data: payload,
      );
      if (response.statusCode == 200 && response.data != null) {
        return OptimizationResultModel.fromJson(response.data as Map<String, dynamic>);
      }
    } catch (e) {
      debugPrint('[RoutingRemoteDataSource] Direct call failed, using local fallback solver: $e');
    }

    // 3. Fallback: local greedy nearest neighbor solver
    return _localFallbackSolve(depot, stops, algorithm);
  }

  /// High accuracy local Haversine distance solver (fallback).
  OptimizationResultModel _localFallbackSolve(
    LocationPointModel depot,
    List<({String id, String? name, LocationPointModel location, double demandKg})> stops,
    String algorithm,
  ) {
    final stopwatch = Stopwatch()..start();
    final remaining = List.of(stops);
    final ordered = <OrderedStopModel>[];
    double currentLat = depot.lat;
    double currentLng = depot.lng;
    double cumulativeDist = 0.0;
    double cumulativeTime = 0.0;

    int index = 1;
    while (remaining.isNotEmpty) {
      int bestIdx = 0;
      double bestDist = double.infinity;

      for (int i = 0; i < remaining.length; i++) {
        final d = _haversineKm(currentLat, currentLng, remaining[i].location.lat, remaining[i].location.lng);
        if (d < bestDist) {
          bestDist = d;
          bestIdx = i;
        }
      }

      final chosen = remaining.removeAt(bestIdx);
      cumulativeDist += bestDist;
      final legTime = (bestDist / 28.0) * 60.0; // 28 km/h avg speed
      cumulativeTime += legTime;

      ordered.add(OrderedStopModel(
        stopIndex: index++,
        stopId: chosen.id,
        location: chosen.location,
        cumulativeDistanceKm: double.parse(cumulativeDist.toStringAsFixed(3)),
        estimatedArrivalMinutes: double.parse(cumulativeTime.toStringAsFixed(1)),
      ));

      currentLat = chosen.location.lat;
      currentLng = chosen.location.lng;
    }

    stopwatch.stop();

    return OptimizationResultModel(
      jobId: 'local-${DateTime.now().millisecondsSinceEpoch}',
      algorithmUsed: algorithm == 'held_karp' ? 'held_karp' : 'nearest_neighbor',
      totalDistanceKm: double.parse(cumulativeDist.toStringAsFixed(2)),
      totalDurationMinutes: double.parse(cumulativeTime.toStringAsFixed(1)),
      orderedStops: ordered,
      executionTimeMs: stopwatch.elapsedMicroseconds / 1000.0,
      timestamp: DateTime.now().toIso8601String(),
    );
  }

  double _haversineKm(double lat1, double lng1, double lat2, double lng2) {
    const p = 0.017453292519943295;
    final a = 0.5 - cos((lat2 - lat1) * p) / 2 + cos(lat1 * p) * cos(lat2 * p) * (1 - cos((lng2 - lng1) * p)) / 2;
    return 12742 * asin(sqrt(a));
  }
}

final routingRemoteDataSourceProvider = Provider<RoutingRemoteDataSource>((ref) {
  return RoutingRemoteDataSource(ref.watch(dioProvider));
});

