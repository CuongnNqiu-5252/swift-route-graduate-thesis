// features/order/presentation/widgets/goong_map_widget.dart

import 'dart:math';

import 'package:flutter/material.dart';
import 'package:maplibre_gl/maplibre_gl.dart';
import 'package:swiftroute_app/config/goong_config.dart';
import 'package:swiftroute_app/core/utils/polyline_decoder.dart';

/// Reusable MapLibre widget configured with Goong vector tiles and fallback support.
/// Dynamically updates the driver marker as a modern glowing GPS puck, and consumes
/// (erases) the passed segments of the blue route line behind the driver in real-time.
class GoongMapWidget extends StatefulWidget {
  final LatLngPoint? pickupLocation;
  final LatLngPoint? deliveryLocation;
  final LatLngPoint? driverLocation;
  final List<LatLngPoint> routePoints;
  final bool interactive;
  final void Function(LatLngPoint point)? onMapClick;

  const GoongMapWidget({
    super.key,
    this.pickupLocation,
    this.deliveryLocation,
    this.driverLocation,
    this.routePoints = const [],
    this.interactive = true,
    this.onMapClick,
  });

  @override
  State<GoongMapWidget> createState() => _GoongMapWidgetState();
}

class _GoongMapWidgetState extends State<GoongMapWidget> {
  MapLibreMapController? _mapController;
  Circle? _pickupCircle;
  Circle? _deliveryCircle;
  Circle? _driverHaloCircle; // Glowing radar pulse halo
  Circle? _driverCircle;     // Vibrant primary driver puck
  Circle? _driverCoreCircle; // Crisp white inner core dot
  Line? _routeLine;
  bool _isStyleLoaded = false;
  bool _hasInitialFitted = false;
  int _maxPassedIndex = 0;

  String get _mapStyleUrl {
    if (GoongConfig.hasValidMapKey) {
      return GoongConfig.vectorStyleUrl;
    }
    // Free public fallback vector style if Goong Map Key is not yet configured
    return 'https://demotiles.maplibre.org/style.json';
  }

  LatLng get _initialCenter {
    if (widget.pickupLocation != null) {
      return LatLng(
        widget.pickupLocation!.latitude,
        widget.pickupLocation!.longitude,
      );
    }
    // Default to Can Tho center (Ninh Kieu)
    return const LatLng(10.0371, 105.7828);
  }

  @override
  void didUpdateWidget(covariant GoongMapWidget oldWidget) {
    super.didUpdateWidget(oldWidget);

    // If route changed or was replaced, reset progress tracker
    if (oldWidget.routePoints != widget.routePoints) {
      _maxPassedIndex = 0;
      _hasInitialFitted = false;
    }

    // Detect if simulation was reset back to pickup origin
    if (widget.driverLocation != null &&
        oldWidget.driverLocation != null &&
        widget.routePoints.isNotEmpty) {
      final start = widget.routePoints.first;
      final dToStart =
          (widget.driverLocation!.latitude - start.latitude).abs() +
          (widget.driverLocation!.longitude - start.longitude).abs();
      if (dToStart < 0.0001 && _maxPassedIndex > 3) {
        _maxPassedIndex = 0;
      }
    }

    if (_isStyleLoaded) {
      _updateMapOverlays();
    }
  }

  @override
  void dispose() {
    _isStyleLoaded = false;
    _mapController = null;
    _pickupCircle = null;
    _deliveryCircle = null;
    _driverHaloCircle = null;
    _driverCircle = null;
    _driverCoreCircle = null;
    _routeLine = null;
    super.dispose();
  }

  void _onMapCreated(MapLibreMapController controller) {
    if (!mounted) return;
    _mapController = controller;
  }

  void _onStyleLoaded() {
    if (!mounted) return;
    _isStyleLoaded = true;
    _updateMapOverlays();
  }

  /// Finds the index in [points] that is closest to [target], searching forward
  /// from [_maxPassedIndex] to ensure monotonic progress.
  int _findClosestIndex(LatLngPoint target, List<LatLngPoint> points) {
    if (points.isEmpty) return 0;
    int bestIndex = _maxPassedIndex;
    double minDistanceSq = double.infinity;

    final start = max(0, _maxPassedIndex);
    for (int i = start; i < points.length; i++) {
      final p = points[i];
      final dLat = p.latitude - target.latitude;
      final dLng = p.longitude - target.longitude;
      final distSq = dLat * dLat + dLng * dLng;
      if (distSq < minDistanceSq) {
        minDistanceSq = distSq;
        bestIndex = i;
      }
    }
    return bestIndex;
  }

  Future<void> _updateMapOverlays() async {
    final controller = _mapController;
    if (!mounted || controller == null || !_isStyleLoaded) return;

    // ── 1. Update Route Polyline (Consumes / disappears behind driver) ──────
    try {
      if (widget.routePoints.length >= 2) {
        List<LatLng> lineGeometry;

        if (widget.driverLocation != null) {
          final driverPt = widget.driverLocation!;
          final closestIdx = _findClosestIndex(driverPt, widget.routePoints);
          if (closestIdx > _maxPassedIndex) {
            _maxPassedIndex = closestIdx;
          }

          // If driver reached the end of the route, the line is fully consumed
          if (_maxPassedIndex >= widget.routePoints.length - 1) {
            lineGeometry = [];
          } else {
            // Remaining line begins at current driver position and proceeds forward to destination
            lineGeometry = [
              LatLng(driverPt.latitude, driverPt.longitude),
              ...widget.routePoints
                  .sublist(_maxPassedIndex + 1)
                  .map((p) => LatLng(p.latitude, p.longitude)),
            ];
          }
        } else {
          // No driver position available: render full planned path
          lineGeometry = widget.routePoints
              .map((p) => LatLng(p.latitude, p.longitude))
              .toList();
        }

        if (lineGeometry.length >= 2) {
          if (_routeLine == null) {
            _routeLine = await controller.addLine(
              LineOptions(
                geometry: lineGeometry,
                lineColor: '#2563EB', // Vibrant Blue route
                lineWidth: 5.5,
                lineOpacity: 0.95,
                lineJoin: 'round',
              ),
            );
          } else {
            await controller.updateLine(
              _routeLine!,
              LineOptions(
                geometry: lineGeometry,
                lineColor: '#2563EB',
                lineWidth: 5.5,
                lineOpacity: 0.95,
                lineJoin: 'round',
              ),
            );
          }

          // Fit overview bounds once when the route is first displayed
          if (!_hasInitialFitted) {
            _fitRouteBounds(
              controller,
              widget.routePoints
                  .map((p) => LatLng(p.latitude, p.longitude))
                  .toList(),
            );
            _hasInitialFitted = true;
          }
        } else if (_routeLine != null) {
          // Reached destination: remove route line completely
          await controller.removeLine(_routeLine!);
          _routeLine = null;
        }
      } else if (_routeLine != null) {
        await controller.removeLine(_routeLine!);
        _routeLine = null;
      }
    } catch (e) {
      debugPrint('[GoongMapWidget] Error updating route line: $e');
    }

    // ── 2. Update Pickup Marker (Green Depot Pin) ──────────────────────────
    try {
      if (widget.pickupLocation != null) {
        final pos = LatLng(
          widget.pickupLocation!.latitude,
          widget.pickupLocation!.longitude,
        );
        if (_pickupCircle == null) {
          _pickupCircle = await controller.addCircle(
            CircleOptions(
              geometry: pos,
              circleColor: '#10B981', // Emerald Green
              circleRadius: 8.5,
              circleStrokeColor: '#FFFFFF',
              circleStrokeWidth: 3.0,
            ),
          );
        } else {
          await controller.updateCircle(
            _pickupCircle!,
            CircleOptions(geometry: pos),
          );
        }
      } else if (_pickupCircle != null) {
        await controller.removeCircle(_pickupCircle!);
        _pickupCircle = null;
      }
    } catch (e) {
      debugPrint('[GoongMapWidget] Error updating pickup marker: $e');
    }

    // ── 3. Update Delivery Marker (Red Customer Pin) ───────────────────────
    try {
      if (widget.deliveryLocation != null) {
        final pos = LatLng(
          widget.deliveryLocation!.latitude,
          widget.deliveryLocation!.longitude,
        );
        if (_deliveryCircle == null) {
          _deliveryCircle = await controller.addCircle(
            CircleOptions(
              geometry: pos,
              circleColor: '#EF4444', // Red
              circleRadius: 8.5,
              circleStrokeColor: '#FFFFFF',
              circleStrokeWidth: 3.0,
            ),
          );
        } else {
          await controller.updateCircle(
            _deliveryCircle!,
            CircleOptions(geometry: pos),
          );
        }
      } else if (_deliveryCircle != null) {
        await controller.removeCircle(_deliveryCircle!);
        _deliveryCircle = null;
      }
    } catch (e) {
      debugPrint('[GoongMapWidget] Error updating delivery marker: $e');
    }

    // ── 4. Update Driver Motorbike Marker (Redrawn Concentric GPS Puck) ────
    try {
      if (widget.driverLocation != null) {
        final pos = LatLng(
          widget.driverLocation!.latitude,
          widget.driverLocation!.longitude,
        );

        // Layer A: Outer Pulse / Radar Halo (Vòng phát quang radar)
        if (_driverHaloCircle == null) {
          _driverHaloCircle = await controller.addCircle(
            CircleOptions(
              geometry: pos,
              circleColor: '#FF7A45', // SwiftRoute Accent Orange
              circleRadius: 18.0,
              circleOpacity: 0.22,
              circleStrokeColor: '#FF9A6C',
              circleStrokeWidth: 1.5,
              circleStrokeOpacity: 0.45,
            ),
          );
        } else {
          await controller.updateCircle(
            _driverHaloCircle!,
            CircleOptions(geometry: pos),
          );
        }

        // Layer B: Main Solid Driver Puck (Chấm tròn chính viền trắng 3.5px)
        if (_driverCircle == null) {
          _driverCircle = await controller.addCircle(
            CircleOptions(
              geometry: pos,
              circleColor: '#FF7A45', // Vibrant Orange
              circleRadius: 9.0,
              circleOpacity: 1.0,
              circleStrokeColor: '#FFFFFF',
              circleStrokeWidth: 3.5,
            ),
          );
        } else {
          await controller.updateCircle(
            _driverCircle!,
            CircleOptions(geometry: pos),
          );
        }

        // Layer C: Inner White Core Dot (Tâm điểm trắng tinh tế)
        if (_driverCoreCircle == null) {
          _driverCoreCircle = await controller.addCircle(
            CircleOptions(
              geometry: pos,
              circleColor: '#FFFFFF',
              circleRadius: 2.5,
              circleOpacity: 1.0,
            ),
          );
        } else {
          await controller.updateCircle(
            _driverCoreCircle!,
            CircleOptions(geometry: pos),
          );
        }
      } else {
        if (_driverHaloCircle != null) {
          await controller.removeCircle(_driverHaloCircle!);
          _driverHaloCircle = null;
        }
        if (_driverCircle != null) {
          await controller.removeCircle(_driverCircle!);
          _driverCircle = null;
        }
        if (_driverCoreCircle != null) {
          await controller.removeCircle(_driverCoreCircle!);
          _driverCoreCircle = null;
        }
      }
    } catch (e) {
      debugPrint('[GoongMapWidget] Error updating driver marker: $e');
    }
  }

  void _fitRouteBounds(MapLibreMapController controller, List<LatLng> coords) {
    if (coords.isEmpty) return;

    double minLat = coords.first.latitude;
    double maxLat = coords.first.latitude;
    double minLng = coords.first.longitude;
    double maxLng = coords.first.longitude;

    for (final c in coords) {
      if (c.latitude < minLat) minLat = c.latitude;
      if (c.latitude > maxLat) maxLat = c.latitude;
      if (c.longitude < minLng) minLng = c.longitude;
      if (c.longitude > maxLng) maxLng = c.longitude;
    }

    final centerLat = (minLat + maxLat) / 2.0;
    final centerLng = (minLng + maxLng) / 2.0;
    final spanLat = (maxLat - minLat).abs();
    final spanLng = (maxLng - minLng).abs();
    final maxSpan = max(spanLat, spanLng);

    double zoom;
    if (maxSpan < 0.005) {
      zoom = 15.0;
    } else if (maxSpan < 0.015) {
      zoom = 14.0;
    } else if (maxSpan < 0.04) {
      zoom = 13.0;
    } else if (maxSpan < 0.1) {
      zoom = 12.0;
    } else {
      zoom = 11.0;
    }

    controller.moveCamera(
      CameraUpdate.newLatLngZoom(LatLng(centerLat, centerLng), zoom),
    );
  }

  @override
  Widget build(BuildContext context) {
    return MapLibreMap(
      styleString: _mapStyleUrl,
      initialCameraPosition: CameraPosition(target: _initialCenter, zoom: 14.0),
      onMapCreated: _onMapCreated,
      onStyleLoadedCallback: _onStyleLoaded,
      myLocationEnabled: false,
      compassEnabled: false,
      attributionButtonMargins: const Point(
        -100,
        -100,
      ), // Hide attribution off-screen
      onMapClick: (point, latLng) {
        if (widget.onMapClick != null) {
          widget.onMapClick!(LatLngPoint(latLng.latitude, latLng.longitude));
        }
      },
    );
  }
}
