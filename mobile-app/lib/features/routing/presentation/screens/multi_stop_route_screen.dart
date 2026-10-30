// features/routing/presentation/screens/multi_stop_route_screen.dart

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/theme/app_colors.dart';
import 'package:swiftroute_app/core/utils/polyline_decoder.dart';
import 'package:swiftroute_app/features/order/data/datasources/goong_remote_data_source.dart';
import 'package:swiftroute_app/features/order/data/models/order_model.dart';
import 'package:swiftroute_app/features/order/presentation/widgets/goong_map_widget.dart';
import 'package:swiftroute_app/features/routing/data/datasources/routing_remote_data_source.dart';
import 'package:swiftroute_app/features/routing/data/models/optimization_result_model.dart';

class MultiStopRouteScreen extends ConsumerStatefulWidget {
  final List<OrderModel> orders;
  final LocationPointModel depot;

  const MultiStopRouteScreen({
    super.key,
    required this.orders,
    required this.depot,
  });

  @override
  ConsumerState<MultiStopRouteScreen> createState() => _MultiStopRouteScreenState();
}

class _MultiStopRouteScreenState extends ConsumerState<MultiStopRouteScreen> {
  String _selectedAlgorithm = 'auto'; // 'auto', 'held_karp', 'nearest_neighbor'
  bool _isLoading = false;
  OptimizationResultModel? _result;
  List<LatLngPoint> _routePoints = [];
  String? _errorMessage;

  @override
  void initState() {
    super.initState();
    _runOptimization();
  }

  Future<void> _runOptimization() async {
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      final routingSource = ref.read(routingRemoteDataSourceProvider);
      final stops = widget.orders.map((o) {
        return (
          id: o.id,
          name: o.deliveryAddress,
          location: LocationPointModel(
            lat: o.deliveryLat,
            lng: o.deliveryLng,
            address: o.deliveryAddress,
          ),
          demandKg: o.weightKg,
        );
      }).toList();

      final res = await routingSource.optimizeBatch(
        depot: widget.depot,
        stops: stops,
        algorithm: _selectedAlgorithm,
      );

      // Fetch street polyline via Goong for consecutive optimized stops
      final combinedPoints = await _buildMultiStopPolyline(res);

      if (mounted) {
        setState(() {
          _result = res;
          _routePoints = combinedPoints;
          _isLoading = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _isLoading = false;
          _errorMessage = 'Lỗi tối ưu hóa lộ trình: $e';
        });
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(_errorMessage!)),
        );
      }
    }
  }

  Future<List<LatLngPoint>> _buildMultiStopPolyline(OptimizationResultModel res) async {
    final goongSource = ref.read(goongRemoteDataSourceProvider);
    final allPoints = <LatLngPoint>[];

    // Build coordinates sequence: Depot -> Stop 1 -> Stop 2 -> ... -> Stop N
    final sequence = <LocationPointModel>[widget.depot];
    for (final s in res.orderedStops) {
      sequence.add(s.location);
    }

    // Connect segments with Goong bike directions
    for (int i = 0; i < sequence.length - 1; i++) {
      try {
        final dir = await goongSource.getDirections(
          originLat: sequence[i].lat,
          originLng: sequence[i].lng,
          destLat: sequence[i + 1].lat,
          destLng: sequence[i + 1].lng,
          vehicle: 'bike',
        );
        allPoints.addAll(dir.points);
      } catch (_) {
        // Fallback straight line
        allPoints.add(LatLngPoint(sequence[i].lat, sequence[i].lng));
        allPoints.add(LatLngPoint(sequence[i + 1].lat, sequence[i + 1].lng));
      }
    }
    return allPoints;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Tối ưu tuyến đường AI (TSP)'),
        backgroundColor: Colors.white,
        elevation: 0.5,
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh_rounded),
            tooltip: 'Chạy lại thuật toán',
            onPressed: _isLoading ? null : _runOptimization,
          ),
        ],
      ),
      body: Column(
        children: [
          // ── 1. Map View (45% height) ──────────────────────────────────────
          SizedBox(
            height: MediaQuery.of(context).size.height * 0.45,
            child: Stack(
              children: [
                GoongMapWidget(
                  pickupLocation: LatLngPoint(widget.depot.lat, widget.depot.lng),
                  deliveryLocation: _result != null && _result!.orderedStops.isNotEmpty
                      ? LatLngPoint(
                          _result!.orderedStops.last.location.lat,
                          _result!.orderedStops.last.location.lng,
                        )
                      : null,
                  routePoints: _routePoints,
                ),
                if (_isLoading)
                  Container(
                    color: Colors.black.withValues(alpha: 0.25),
                    child: const Center(
                      child: CircularProgressIndicator(color: Colors.white),
                    ),
                  ),
              ],
            ),
          ),

          // ── 2. Algorithm Control Bar ─────────────────────────────────────
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
            color: Colors.white,
            child: Row(
              children: [
                const Text(
                  'Thuật toán:',
                  style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: SegmentedButton<String>(
                    segments: const [
                      ButtonSegment(
                        value: 'auto',
                        label: Text('Auto (DP/NN)', style: TextStyle(fontSize: 11)),
                      ),
                      ButtonSegment(
                        value: 'held_karp',
                        label: Text('Held-Karp DP', style: TextStyle(fontSize: 11)),
                      ),
                      ButtonSegment(
                        value: 'nearest_neighbor',
                        label: Text('Nearest Neighbor', style: TextStyle(fontSize: 11)),
                      ),
                    ],
                    selected: {_selectedAlgorithm},
                    onSelectionChanged: (set) {
                      setState(() => _selectedAlgorithm = set.first);
                      _runOptimization();
                    },
                    style: SegmentedButton.styleFrom(
                      selectedBackgroundColor: AppColors.primary,
                      selectedForegroundColor: Colors.white,
                      visualDensity: VisualDensity.compact,
                    ),
                  ),
                ),
              ],
            ),
          ),

          // ── 3. Performance Metrics Card ──────────────────────────────────
          if (_result != null)
            Container(
              margin: const EdgeInsets.fromLTRB(16, 12, 16, 8),
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: const Color(0xFFF0F4FF),
                borderRadius: BorderRadius.circular(10),
                border: Border.all(color: const Color(0xFFC7D2FE)),
              ),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceAround,
                children: [
                  _buildMetric(
                    label: 'THUẬT TOÁN',
                    value: _formatAlgorithmName(_result!.algorithmUsed),
                    icon: Icons.psychology_rounded,
                    color: AppColors.primary,
                  ),
                  _buildMetric(
                    label: 'THỜI GIAN GIẢI',
                    value: '${_result!.executionTimeMs.toStringAsFixed(2)} ms',
                    icon: Icons.speed_rounded,
                    color: const Color(0xFF059669),
                  ),
                  _buildMetric(
                    label: 'TỔNG QUÃNG ĐƯỜNG',
                    value: '${_result!.totalDistanceKm.toStringAsFixed(1)} km',
                    icon: Icons.timeline_rounded,
                    color: AppColors.accent,
                  ),
                  _buildMetric(
                    label: 'TỔNG THỜI GIAN',
                    value: '${_result!.totalDurationMinutes.toStringAsFixed(0)} p',
                    icon: Icons.schedule_rounded,
                    color: const Color(0xFF7C3AED),
                  ),
                ],
              ),
            ),

          // ── 4. Stop Sequence List ─────────────────────────────────────────
          Expanded(
            child: _result == null
                ? const Center(child: Text('Đang tính toán thứ tự tối ưu...'))
                : ListView.builder(
                    padding: const EdgeInsets.fromLTRB(16, 4, 16, 24),
                    itemCount: _result!.orderedStops.length,
                    itemBuilder: (context, idx) {
                      final stop = _result!.orderedStops[idx];
                      return Container(
                        margin: const EdgeInsets.only(bottom: 8),
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: Colors.white,
                          borderRadius: BorderRadius.circular(10),
                          border: Border.all(color: AppColors.divider),
                        ),
                        child: Row(
                          children: [
                            Container(
                              width: 28,
                              height: 28,
                              alignment: Alignment.center,
                              decoration: const BoxDecoration(
                                color: AppColors.primary,
                                shape: BoxShape.circle,
                              ),
                              child: Text(
                                '${stop.stopIndex}',
                                style: const TextStyle(
                                  color: Colors.white,
                                  fontWeight: FontWeight.bold,
                                  fontSize: 12,
                                ),
                              ),
                            ),
                            const SizedBox(width: 12),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    stop.location.address ?? 'Điểm giao #${stop.stopId}',
                                    style: const TextStyle(
                                      fontWeight: FontWeight.bold,
                                      fontSize: 13,
                                    ),
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                  ),
                                  const SizedBox(height: 2),
                                  Text(
                                    'Cách xuất phát: ${stop.cumulativeDistanceKm.toStringAsFixed(1)} km • Dự kiến tới sau: ${stop.estimatedArrivalMinutes.toStringAsFixed(0)} phút',
                                    style: const TextStyle(
                                      fontSize: 11,
                                      color: AppColors.textSecondary,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            const Icon(
                              Icons.arrow_forward_ios_rounded,
                              size: 14,
                              color: AppColors.textSecondary,
                            ),
                          ],
                        ),
                      );
                    },
                  ),
          ),
        ],
      ),
    );
  }

  Widget _buildMetric({
    required String label,
    required String value,
    required IconData icon,
    required Color color,
  }) {
    return Column(
      children: [
        Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 14, color: color),
            const SizedBox(width: 4),
            Text(
              value,
              style: TextStyle(
                fontWeight: FontWeight.bold,
                fontSize: 13,
                color: color,
              ),
            ),
          ],
        ),
        const SizedBox(height: 2),
        Text(
          label,
          style: const TextStyle(
            fontSize: 9,
            color: AppColors.textSecondary,
            fontWeight: FontWeight.w600,
          ),
        ),
      ],
    );
  }

  String _formatAlgorithmName(String raw) {
    switch (raw.toLowerCase()) {
      case 'held_karp':
        return 'Held-Karp DP';
      case 'nearest_neighbor':
        return 'Nearest Neighbor';
      case 'genetic':
        return 'Genetic Alg';
      default:
        return raw;
    }
  }
}

