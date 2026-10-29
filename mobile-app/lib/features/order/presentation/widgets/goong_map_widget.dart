// features/order/presentation/widgets/goong_map_widget.dart

import 'dart:math';

import 'package:flutter/material.dart';
import 'package:maplibre_gl/maplibre_gl.dart';
import 'package:swiftroute_app/config/goong_config.dart';
import 'package:swiftroute_app/core/utils/polyline_decoder.dart';

/// Reusable MapLibre widget configured with Goong vector tiles and fallback support.
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
  Circle? _driverCircle;
  Line? _routeLine;
  bool _isStyleLoaded = false;

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
    // Default to Ho Chi Minh City center
    return const LatLng(10.7769, 106.7009);
  }

  @override
  void didUpdateWidget(covariant GoongMapWidget oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (_isStyleLoaded) {
      _updateMapOverlays();
    }
  }

  void _onMapCreated(MapLibreMapController controller) {
    _mapController = controller;
  }

  void _onStyleLoaded() {
    _isStyleLoaded = true;
    _updateMapOverlays();
  }

  Future<void> _updateMapOverlays() async {
    final controller = _mapController;
    if (controller == null || !_isStyleLoaded) return;

    // 1. Update Route Polyline
    if (widget.routePoints.isNotEmpty) {
      final lineGeometry = widget.routePoints
          .map((p) => LatLng(p.latitude, p.longitude))
          .toList();

      if (_routeLine == null) {
        _routeLine = await controller.addLine(
          LineOptions(
            geometry: lineGeometry,
            lineColor: '#2F3C7E',
            lineWidth: 5.0,
            lineOpacity: 0.9,
          ),
        );
      } else {
        await controller.updateLine(
          _routeLine!,
          LineOptions(geometry: lineGeometry),
        );
      }

      // Adjust camera to fit the route bounds
      _fitRouteBounds(lineGeometry);
    } else if (_routeLine != null) {
      await controller.removeLine(_routeLine!);
      _routeLine = null;
    }

    // 2. Update Pickup Marker (Green)
    if (widget.pickupLocation != null) {
      final pos = LatLng(
        widget.pickupLocation!.latitude,
        widget.pickupLocation!.longitude,
      );
      if (_pickupCircle == null) {
        _pickupCircle = await controller.addCircle(
          CircleOptions(
            geometry: pos,
            circleColor: '#22C55E', // Green
            circleRadius: 8.0,
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

    // 3. Update Delivery Marker (Red)
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
            circleRadius: 8.0,
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

    // 4. Update Driver Motorbike Marker (Orange accent)
    if (widget.driverLocation != null) {
      final pos = LatLng(
        widget.driverLocation!.latitude,
        widget.driverLocation!.longitude,
      );
      if (_driverCircle == null) {
        _driverCircle = await controller.addCircle(
          CircleOptions(
            geometry: pos,
            circleColor: '#FF7A45', // Vibrant Orange
            circleRadius: 10.0,
            circleStrokeColor: '#FFFFFF',
            circleStrokeWidth: 3.0,
          ),
        );
      } else {
        await controller.updateCircle(
          _driverCircle!,
          CircleOptions(geometry: pos),
        );
      }
    } else if (_driverCircle != null) {
      await controller.removeCircle(_driverCircle!);
      _driverCircle = null;
    }
  }

  void _fitRouteBounds(List<LatLng> coords) {
    if (coords.length < 2 || _mapController == null) return;

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

    _mapController!.animateCamera(
      CameraUpdate.newLatLngBounds(
        LatLngBounds(
          southwest: LatLng(minLat, minLng),
          northeast: LatLng(maxLat, maxLng),
        ),
        left: 50,
        right: 50,
        top: 50,
        bottom: 50,
      ),
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
