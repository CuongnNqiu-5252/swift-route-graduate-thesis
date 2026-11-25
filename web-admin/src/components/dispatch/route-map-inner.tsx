'use client';

import type { TrackingSignal } from '@/application/hooks/use-order-live-tracking';
import { useRoadRoute } from '@/application/hooks/use-road-route';
import {
  DispatchDepot,
  MapDriverMarker,
  MapOverviewRoute,
  MapPlanHighlight,
} from '@/domain/entities/routing.entity';
import { useLocale } from '@/i18n/provider';
import { MapPinned, Route } from 'lucide-react';
import type { GeoJSONSource } from 'maplibre-gl';
import { AttributionControl, LngLatBounds, Map as MapLibreMap, Marker, NavigationControl, Popup, setWorkerUrl } from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import { useEffect, useMemo, useRef, useState } from 'react';
import { RouteMapLayers, TRAFFIC_LAYER_ID } from './route-map-layers';


if (typeof window !== 'undefined') {
  setWorkerUrl('/maplibre-gl-worker.mjs');
}
const EMPTY_OVERVIEW_ROUTES: MapOverviewRoute[] = [];
const EMPTY_LEGS: readonly (number | null)[] = [];

interface RouteMapInnerProps {
  depot: DispatchDepot | null;
  overviewRoutes?: MapOverviewRoute[];
  selectedOverviewRouteId?: string | null;
  highlightedPlan?: MapPlanHighlight | null;
  filterKey?: string;
  showDeliveryStops?: boolean;
  showPlanDetails?: boolean;
  isPlansOpen?: boolean;
  onSelectOverviewRoute?: (planId: string) => void;
  onHoverOverviewPlan?: (highlight: MapPlanHighlight | null) => void;
  fleetDrivers?: MapDriverMarker[];
  fleetOrigin?: MapDriverMarker;
  driver?: [number, number];
  driverName?: string;
  driverVehicle?: string;
  trackingSignal?: TrackingSignal;
}

function markerElement(
  label: string,
  tone: 'depot' | 'stop' | 'driver' | 'origin',
  signal: TrackingSignal = 'live',
  customColor?: string,
) {
  // MapLibre owns the transform of the outer element for positioning, so every
  // visual effect (scale, ring, colors) must be applied to the inner pin only.
  const container = document.createElement('div');
  const element = document.createElement('button');
  element.type = 'button';
  element.dataset.markerPin = 'true';
  element.textContent = label;
  element.setAttribute(
    'aria-label',
    tone === 'depot'
      ? 'Xem kho xuất phát'
      : tone === 'driver'
        ? 'Vị trí tài xế trực tiếp'
        : tone === 'origin'
          ? 'Điểm bắt đầu giả lập'
          : `Xem điểm giao ${label}`,
  );
  const isLiveDriver = tone === 'driver' && signal === 'live';
  const background = tone === 'depot'
    ? '#0f172a'
    : tone === 'driver'
      ? (isLiveDriver ? '#059669' : '#64748b')
      : tone === 'origin'
        ? '#0284c7'
        : (customColor || '#dc2626');
  Object.assign(element.style, {
    width: tone === 'depot' ? '36px' : tone === 'driver' || tone === 'origin' ? '34px' : '30px',
    height: tone === 'depot' ? '36px' : tone === 'driver' || tone === 'origin' ? '34px' : '30px',
    borderRadius: tone === 'depot' ? '11px' : '50%',
    border: '3px solid white',
    background,
    color: 'white',
    fontSize: '12px',
    fontWeight: '800',
    boxShadow: isLiveDriver
      ? '0 0 0 6px rgb(5 150 105 / 26%), 0 6px 18px rgb(15 23 42 / 34%)'
      : '0 6px 18px rgb(15 23 42 / 30%)',
    cursor: 'pointer',
    transition: 'transform 120ms ease, box-shadow 120ms ease, opacity 120ms ease',
    transformOrigin: 'center',
  });
  container.style.zIndex = isLiveDriver ? '20' : tone === 'driver' ? '18' : tone === 'origin' ? '15' : '10';
  container.append(element);
  return container;
}

/** Returns the visual pin inside a marker element created by markerElement(). */
function markerPin(container: HTMLElement): HTMLElement {
  return container.querySelector<HTMLElement>('[data-marker-pin]') ?? container;
}

function animateLngLat(
  from: [number, number],
  target: [number, number],
  onFrame: (position: [number, number]) => void,
  onDone: () => void,
): number | null {
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    onFrame(target);
    onDone();
    return null;
  }
  const startedAt = performance.now();
  const duration = 850;
  let rafId = 0;
  const tick = (now: number) => {
    const progress = Math.min(1, (now - startedAt) / duration);
    const eased = 1 - Math.pow(1 - progress, 3);
    onFrame([
      from[0] + (target[0] - from[0]) * eased,
      from[1] + (target[1] - from[1]) * eased,
    ]);
    if (progress < 1) {
      rafId = window.requestAnimationFrame(tick);
    } else {
      onDone();
    }
  };
  rafId = window.requestAnimationFrame(tick);
  return rafId;
}

function popupContent(title: string, body: string, meta: string) {
  const root = document.createElement('div');
  root.style.maxWidth = '250px';
  const heading = document.createElement('p');
  heading.textContent = title;
  Object.assign(heading.style, { fontWeight: '750', color: '#0f172a' });
  const description = document.createElement('p');
  description.textContent = body;
  Object.assign(description.style, { marginTop: '5px', fontSize: '12px', lineHeight: '18px', color: '#475569' });
  const detail = document.createElement('p');
  detail.textContent = meta;
  Object.assign(detail.style, { marginTop: '7px', fontSize: '11px', fontWeight: '650', color: '#2563eb' });
  root.append(heading, description, detail);
  return root;
}

const EMPTY_FLEET_DRIVERS: MapDriverMarker[] = [];
interface OverviewRoadLineProps {
  map: MapLibreMap;
  route: MapOverviewRoute;
  isSelected: boolean;
  hasSelection: boolean;
  showDeliveryStops: boolean;
  highlightedStopId?: string;
  onSelect?: (planId: string) => void;
  onHover?: (highlight: MapPlanHighlight | null) => void;
}

function OverviewRoadLine({
  map,
  route,
  isSelected,
  hasSelection,
  showDeliveryStops,
  highlightedStopId,
  onSelect,
  onHover,
}: OverviewRoadLineProps) {
  const points = useMemo(
    () =>
      route.stops
        .map((stop) => stop.location)
        .filter((point) => Number.isFinite(point.lat) && Number.isFinite(point.lng)),
    [route.stops],
  );

  const road = useRoadRoute(points);

  const sourceId = `overview-source-${route.id}`;
  const glowLayerId = `overview-glow-${route.id}`;
  const layerId = `overview-layer-${route.id}`;
  const casingLayerId = `overview-casing-${route.id}`;
  const hitLayerId = `overview-hit-${route.id}`;

  const onSelectRef = useRef(onSelect);
  const onHoverRef = useRef(onHover);
  const stopMarkers = useRef<Marker[]>([]);
  useEffect(() => {
    onSelectRef.current = onSelect;
    onHoverRef.current = onHover;
  }, [onHover, onSelect]);

  // Draw stop markers for this plan cluster
  useEffect(() => {
    if (!showDeliveryStops) return;
    const markers = route.stops.map((stop, index) => {
      const element = markerElement(
        String(index + 1),
        'stop',
        'live',
        route.color,
      );
      element.addEventListener('click', () => {
        onSelectRef.current?.(route.id);
      });
      element.addEventListener('mouseenter', () => {
        onHoverRef.current?.({ planId: route.id, stopId: stop.id });
      });
      element.addEventListener('mouseleave', () => {
        onHoverRef.current?.(null);
      });
      return new Marker({ element })
        .setLngLat([stop.location.lng, stop.location.lat])
        .setPopup(
          new Popup({ offset: 20, closeButton: false }).setDOMContent(
            popupContent(
              `${route.label} · Điểm ${index + 1}`,
              stop.order?.delivery_address ?? 'Chưa có địa chỉ',
              [
                stop.order?.order_code ? `Đơn ${stop.order.order_code}` : null,
                route.driverName ? `TX ${route.driverName}` : null,
              ]
                .filter(Boolean)
                .join(' · ') || 'Điểm giao hàng',
            ),
          ),
        )
        .addTo(map);
    });
    stopMarkers.current = markers;
    return () => {
      markers.forEach((marker) => marker.remove());
      stopMarkers.current = [];
    };
  }, [map, route, showDeliveryStops]);

  useEffect(() => {
    stopMarkers.current.forEach((marker, index) => {
      const element = marker.getElement();
      const pin = markerPin(element);
      const isStopHighlighted = route.stops[index].id === highlightedStopId;
      element.style.zIndex = isStopHighlighted ? '35' : isSelected ? '20' : '10';
      pin.style.opacity = hasSelection && !isSelected ? '0.35' : '1';
      pin.style.transform = isStopHighlighted ? 'scale(1.38)' : isSelected ? 'scale(1.12)' : 'scale(1)';
      pin.style.outline = isStopHighlighted ? '2px solid #0f172a' : '';
      pin.style.outlineOffset = isStopHighlighted ? '2px' : '';
      pin.style.boxShadow = isStopHighlighted
        ? `0 0 0 7px ${route.color}4d, 0 0 0 2.5px #ffffff, 0 8px 22px rgb(15 23 42 / 38%)`
        : isSelected
          ? `0 0 0 3px ${route.color}33, 0 6px 18px rgb(15 23 42 / 32%)`
          : '0 4px 14px rgb(15 23 42 / 24%)';
    });
  }, [map, route, isSelected, hasSelection, highlightedStopId, showDeliveryStops]);

  // 3. Initialize Source, luminous glow Layer, casing, line Layer, and wider transparent hit Layer
  useEffect(() => {
    if (!map.getStyle()) return;
    if (!map.getSource(sourceId)) {
      map.addSource(sourceId, {
        type: 'geojson',
        data: {
          type: 'FeatureCollection',
          features: [],
        },
      });
    }

    if (!map.getLayer(glowLayerId)) {
      map.addLayer({
        id: glowLayerId,
        type: 'line',
        source: sourceId,
        layout: { 'line-cap': 'round', 'line-join': 'round' },
        paint: {
          'line-color': route.color,
          'line-width': 20,
          'line-blur': 9,
          'line-opacity': 0,
        },
      });
    }

    if (!map.getLayer(casingLayerId)) {
      map.addLayer({
        id: casingLayerId,
        type: 'line',
        source: sourceId,
        layout: { 'line-cap': 'round', 'line-join': 'round' },
        paint: { 'line-color': '#ffffff', 'line-width': 8, 'line-opacity': 0.9 },
      });
    }

    if (!map.getLayer(layerId)) {
      map.addLayer({
        id: layerId,
        type: 'line',
        source: sourceId,
        layout: {
          'line-cap': 'round',
          'line-join': 'round',
        },
        paint: {
          'line-color': route.color,
          'line-width': 5,
          'line-opacity': 0.88,
        },
      });
    }

    if (!map.getLayer(hitLayerId)) {
      map.addLayer({
        id: hitLayerId,
        type: 'line',
        source: sourceId,
        layout: {
          'line-cap': 'round',
          'line-join': 'round',
        },
        paint: {
          'line-color': route.color,
          'line-width': 20,
          'line-opacity': 0,
        },
      });
    }

    const handleClick = () => {
      onSelectRef.current?.(route.id);
    };
    const handleMouseEnter = () => {
      map.getCanvas().style.cursor = 'pointer';
      onHoverRef.current?.({ planId: route.id });
    };
    const handleMouseLeave = () => {
      map.getCanvas().style.cursor = '';
      onHoverRef.current?.(null);
    };

    map.on('click', hitLayerId, handleClick);
    map.on('mouseenter', hitLayerId, handleMouseEnter);
    map.on('mouseleave', hitLayerId, handleMouseLeave);

    return () => {
      if (!map.getStyle()) return;
      map.off('click', hitLayerId, handleClick);
      map.off('mouseenter', hitLayerId, handleMouseEnter);
      map.off('mouseleave', hitLayerId, handleMouseLeave);
      map.getCanvas().style.cursor = '';

      if (map.getLayer(hitLayerId)) {
        map.removeLayer(hitLayerId);
      }
      if (map.getLayer(layerId)) {
        map.removeLayer(layerId);
      }
      if (map.getLayer(casingLayerId)) {
        map.removeLayer(casingLayerId);
      }
      if (map.getLayer(glowLayerId)) {
        map.removeLayer(glowLayerId);
      }
      if (map.getSource(sourceId)) {
        map.removeSource(sourceId);
      }
    };
  }, [map, sourceId, glowLayerId, layerId, casingLayerId, hitLayerId, route.id, route.color]);

  // 4. Update color, glow, width & opacity when route.color or selection/hover changes
  useEffect(() => {
    if (!map.getStyle() || !map.getLayer(layerId)) return;
    map.setPaintProperty(layerId, 'line-color', route.color);
    map.setPaintProperty(layerId, 'line-width', isSelected ? 7.5 : hasSelection ? 3.5 : 5);
    map.setPaintProperty(layerId, 'line-opacity', isSelected ? 1 : hasSelection ? 0.28 : 0.88);
    if (map.getLayer(glowLayerId)) {
      map.setPaintProperty(glowLayerId, 'line-color', route.color);
      map.setPaintProperty(glowLayerId, 'line-width', isSelected ? 22 : 16);
      map.setPaintProperty(glowLayerId, 'line-opacity', isSelected ? 0.45 : 0);
    }
    if (map.getLayer(casingLayerId)) {
      map.setPaintProperty(casingLayerId, 'line-width', isSelected ? 11.5 : hasSelection ? 5.5 : 8);
      map.setPaintProperty(casingLayerId, 'line-opacity', isSelected ? 0.96 : hasSelection ? 0.28 : 0.9);
    }
    if (isSelected) {
      if (map.getLayer(glowLayerId)) map.moveLayer(glowLayerId);
      if (map.getLayer(casingLayerId)) map.moveLayer(casingLayerId);
      map.moveLayer(layerId);
      if (map.getLayer(hitLayerId)) map.moveLayer(hitLayerId);
    }
  }, [map, glowLayerId, layerId, casingLayerId, hitLayerId, route.color, isSelected, hasSelection]);

  // 5. Update GeoJSON using street coordinates or straight-line fallback
  useEffect(() => {
    if (!map.getStyle()) return;
    const source = map.getSource(sourceId) as GeoJSONSource | undefined;
    if (!source) return;

    const hasStreetRoute = !road.error && road.coordinates.length > 1;
    const coordinates: [number, number][] = hasStreetRoute
      ? road.coordinates
      : points.map((point) => [point.lng, point.lat]);

    const geojson = {
      type: 'FeatureCollection' as const,
      features: coordinates.length > 1
        ? [{
          type: 'Feature' as const,
          properties: {
            planId: route.id,
          },
          geometry: {
            type: 'LineString' as const,
            coordinates,
          },
        }]
        : [],
    };

    source.setData(geojson);
    if (map.getLayer(layerId)) {
      map.setPaintProperty(layerId, 'line-dasharray', hasStreetRoute ? [1, 0] : [2, 2]);
    }
  }, [map, sourceId, layerId, route.id, points, road.coordinates, road.error]);

  return null;
}
export default function RouteMapInner({
  depot,
  overviewRoutes = EMPTY_OVERVIEW_ROUTES,
  showPlanDetails = false,
  isPlansOpen = false,
  selectedOverviewRouteId = null,
  highlightedPlan,
  filterKey = '',
  showDeliveryStops = true,
  onSelectOverviewRoute,
  onHoverOverviewPlan,
  fleetDrivers = EMPTY_FLEET_DRIVERS,
  fleetOrigin,
  driver,
  driverName,
  driverVehicle,
  trackingSignal = 'waiting',
}: RouteMapInnerProps) {
  const { t } = useLocale();
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<MapLibreMap | null>(null);
  const markersRef = useRef<Marker[]>([]);
  const driverMarkerRef = useRef<Marker | null>(null);
  const fleetMarkersRef = useRef<Map<string, { marker: Marker; animationFrame: number | null; displayed: [number, number] }>>(new Map());
  const driverAnimationFrameRef = useRef<number | null>(null);
  const displayedDriverRef = useRef<[number, number] | null>(null);
  const styleReadyRef = useRef(false);
  const framedViewRef = useRef('');
  const [readyMap, setReadyMap] = useState<MapLibreMap | null>(null);
  const [mapError, setMapError] = useState('');
  const mapKey = process.env.NEXT_PUBLIC_GOONG_MAP_KEY;
  const firstFleetDriver = fleetOrigin ?? fleetDrivers[0];
  const firstOverviewDepot = overviewRoutes[0]?.depot;
  const hasMapOrigin = Boolean(depot) || Boolean(firstFleetDriver) || Boolean(firstOverviewDepot);
  const initialCenterRef = useRef<[number, number] | null>(null);

  useEffect(() => {
    if (depot) {
      initialCenterRef.current = [depot.lng, depot.lat];
    } else if (firstFleetDriver) {
      initialCenterRef.current = [firstFleetDriver.position[1], firstFleetDriver.position[0]];
    } else if (firstOverviewDepot) {
      initialCenterRef.current = [firstOverviewDepot.lng, firstOverviewDepot.lat];
    } else {
      initialCenterRef.current = null;
    }
  }, [depot, firstFleetDriver, firstOverviewDepot]);
  useEffect(() => {
    const initialCenter = initialCenterRef.current;
    if (!containerRef.current || mapRef.current || !mapKey || !hasMapOrigin || !initialCenter) return;
    styleReadyRef.current = false;
    framedViewRef.current = '';
    const map = new MapLibreMap({
      container: containerRef.current,
      style: `https://tiles.goong.io/assets/goong_map_web.json?api_key=${encodeURIComponent(mapKey)}`,
      center: initialCenter, zoom: 13, attributionControl: false,
    });
    map.addControl(new NavigationControl({ showCompass: false }), 'top-right');
    map.addControl(new AttributionControl({ compact: true, customAttribution: '© Goong' }), 'bottom-right');
    map.on('style.load', () => {
      styleReadyRef.current = true;
      setReadyMap(map);
    });
    map.on('styleimagemissing', (e) => {
      if (!e.id || map.hasImage(e.id)) return;
      map.addImage(e.id, { width: 1, height: 1, data: new Uint8Array(4) });
    });

    map.on('remove', () => setReadyMap((current) => current === map ? null : current));
    map.on('load', () => {
      styleReadyRef.current = true;
      setMapError('');
    });
    map.on('error', (e) => {
      if ('sourceId' in e && e.sourceId === TRAFFIC_LAYER_ID) return;
      console.error('[MapLibre error]', e);
      setMapError('Không tải được nền bản đồ Goong. Kiểm tra Map Key và kết nối mạng.');
    });
    mapRef.current = map;
    const resize = new ResizeObserver(() => map.resize());
    resize.observe(containerRef.current);
    const fleetMarkers = fleetMarkersRef.current;
    return () => {
      resize.disconnect();
      styleReadyRef.current = false;
      markersRef.current.forEach((marker) => marker.remove());
      markersRef.current = [];
      if (driverAnimationFrameRef.current !== null) {
        window.cancelAnimationFrame(driverAnimationFrameRef.current);
        driverAnimationFrameRef.current = null;
      }
      driverMarkerRef.current?.remove();
      driverMarkerRef.current = null;
      fleetMarkers.forEach((entry) => {
        if (entry.animationFrame !== null) {
          window.cancelAnimationFrame(entry.animationFrame);
        }
        entry.marker.remove();
      });
      fleetMarkers.clear();
      displayedDriverRef.current = null;
      map.remove();
      mapRef.current = null;
    };
  }, [hasMapOrigin, mapKey]);

  // Static depot + delivery stop markers & camera bounds (supports both single selected plan and multi-plan overview)
  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;
    markersRef.current.forEach((marker) => marker.remove());
    markersRef.current = [];

    if (overviewRoutes.length > 0) {
      const selectedRoute = selectedOverviewRouteId
        ? overviewRoutes.find((item) => item.id === selectedOverviewRouteId)
        : undefined;
      const bounds = new LngLatBounds();
      const uniqueDepots = new Map<string, DispatchDepot>();

      for (const item of overviewRoutes) {
        const isSelected = selectedRoute?.id === item.id;
        const depotKey = `${item.depot.lat.toFixed(5)}:${item.depot.lng.toFixed(5)}`;
        if ((!filterKey || item.warehouseId) && !uniqueDepots.has(depotKey)) {
          uniqueDepots.set(depotKey, item.depot);
        }
        if (!selectedRoute || isSelected) {
          if (!filterKey || item.warehouseId) bounds.extend([item.depot.lng, item.depot.lat]);
          for (const stop of item.stops) bounds.extend([stop.location.lng, stop.location.lat]);
        }
      }

      const depotMarkers: Marker[] = [];
      for (const uniqueDepot of uniqueDepots.values()) {
        depotMarkers.push(
          new Marker({ element: markerElement('K', 'depot') })
            .setLngLat([uniqueDepot.lng, uniqueDepot.lat])
            .setPopup(
              new Popup({ offset: 22, closeButton: false }).setDOMContent(
                popupContent(uniqueDepot.name, uniqueDepot.address, t('Kho xuất phát')),
              ),
            )
            .addTo(map),
        );
      }

      markersRef.current = depotMarkers;
      if (!bounds.isEmpty()) {
        const view = JSON.stringify([filterKey, selectedOverviewRouteId, showPlanDetails, bounds.toArray()]);
        const frame = () => {
          if (framedViewRef.current === view) return;
          framedViewRef.current = view;
          map.resize();
          const padding = selectedRoute && showPlanDetails && map.getContainer().clientWidth >= 640
            ? { top: 64, left: 56, bottom: 56, right: 440 }
            : { top: 56, left: 40, bottom: 40, right: 40 };
          map.fitBounds(bounds, { padding, maxZoom: 15, duration: 550 });
        };
        if (styleReadyRef.current || map.loaded()) frame();
        else map.once('load', frame);
        return () => {
          map.off('load', frame);
        };
      }
      return;
    }

    if (filterKey) {
      framedViewRef.current = '';
      map.stop();
      return;
    }

    // No plan to show yet: only mark the default depot.
    if (!depot) return;
    markersRef.current = [
      new Marker({ element: markerElement('K', 'depot') })
        .setLngLat([depot.lng, depot.lat])
        .setPopup(new Popup({ offset: 22, closeButton: false }).setDOMContent(popupContent(depot.name, depot.address, t('Kho xuất phát')))
        )
        .addTo(map),
    ];
    const view = JSON.stringify([depot.lng, depot.lat]);
    const frame = () => {
      if (framedViewRef.current === view) return;
      framedViewRef.current = view;
      map.easeTo({ center: [depot.lng, depot.lat], zoom: 15, duration: 450 });
    };
    if (styleReadyRef.current || map.loaded()) frame(); else map.once('load', frame);
    return () => { map.off('load', frame); };
  }, [depot, filterKey, overviewRoutes, selectedOverviewRouteId, showPlanDetails, t]);

  // Smooth real-time driver marker update (runs every second on SSE location_update)
  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;
    if (!driver || !Number.isFinite(driver[0]) || !Number.isFinite(driver[1])) {
      if (driverAnimationFrameRef.current !== null) {
        window.cancelAnimationFrame(driverAnimationFrameRef.current);
        driverAnimationFrameRef.current = null;
      }
      driverMarkerRef.current?.remove();
      driverMarkerRef.current = null;
      displayedDriverRef.current = null;
      return;
    }
    const [lat, lng] = driver;
    const popup = popupContent(
      driverName || t("Vị trí tài xế"),
      `${driverVehicle ? `${driverVehicle} · ` : ''}Toạ độ ${lat.toFixed(5)}, ${lng.toFixed(5)}`,
      trackingSignal === 'live' ? 'GPS trực tiếp từ ứng dụng tài xế' : 'Vị trí gần nhất nhận từ tài xế',
    );
    if (!driverMarkerRef.current) {
      driverMarkerRef.current = new Marker({ element: markerElement('TX', 'driver', trackingSignal) })
        .setLngLat([lng, lat])
        .setPopup(new Popup({ offset: 22, closeButton: false }).setDOMContent(popup))
        .addTo(map);
      displayedDriverRef.current = [lng, lat];
    } else {
      driverMarkerRef.current.getPopup()?.setDOMContent(popup);
      if (driverAnimationFrameRef.current !== null) {
        window.cancelAnimationFrame(driverAnimationFrameRef.current);
      }

      const target: [number, number] = [lng, lat];
      const currentLngLat = driverMarkerRef.current.getLngLat();
      const from = displayedDriverRef.current ?? [currentLngLat.lng, currentLngLat.lat];
      driverAnimationFrameRef.current = animateLngLat(
        from,
        target,
        (position) => {
          driverMarkerRef.current?.setLngLat(position);
          displayedDriverRef.current = position;
        },
        () => {
          driverAnimationFrameRef.current = null;
        },
      );
    }
  }, [driver, driverName, driverVehicle, trackingSignal, hasMapOrigin, t]);

  // Real-time markers for all streaming drivers in the tenant fleet
  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;
    const entries = fleetMarkersRef.current;
    const activeIds = new Set<string>();

    for (const item of fleetDrivers) {
      const [lat, lng] = item.position;
      if (!Number.isFinite(lat) || !Number.isFinite(lng)) continue;
      activeIds.add(item.id);
      const signal = item.signal ?? 'live';
      const popup = popupContent(
        item.name || t("Tài xế"),
        `${item.vehicle ? `${item.vehicle} · ` : ''}Toạ độ ${lat.toFixed(5)}, ${lng.toFixed(5)}`,
        signal === 'live' ? 'GPS trực tiếp từ ứng dụng tài xế' : 'Vị trí gần nhất nhận từ tài xế',
      );
      const existing = entries.get(item.id);
      if (!existing) {
        const marker = new Marker({ element: markerElement('TX', 'driver', signal) })
          .setLngLat([lng, lat])
          .setPopup(new Popup({ offset: 22, closeButton: false }).setDOMContent(popup))
          .addTo(map);
        entries.set(item.id, { marker, animationFrame: null, displayed: [lng, lat] });
      } else {
        existing.marker.getPopup()?.setDOMContent(popup);
        const el = markerPin(existing.marker.getElement());
        el.style.background = signal === 'live' ? '#059669' : '#64748b';
        el.style.boxShadow = signal === 'live'
          ? '0 0 0 6px rgb(5 150 105 / 26%), 0 6px 18px rgb(15 23 42 / 34%)'
          : '0 6px 18px rgb(15 23 42 / 30%)';

        if (existing.animationFrame !== null) {
          window.cancelAnimationFrame(existing.animationFrame);
        }
        const target: [number, number] = [lng, lat];
        const currentLngLat = existing.marker.getLngLat();
        const from = existing.displayed ?? [currentLngLat.lng, currentLngLat.lat];
        if (from[0] !== target[0] || from[1] !== target[1]) {
          existing.animationFrame = animateLngLat(
            from,
            target,
            (position) => {
              existing.marker.setLngLat(position);
              existing.displayed = position;
            },
            () => {
              existing.animationFrame = null;
            },
          );
        } else {
          existing.animationFrame = null;
        }
      }
    }

    for (const [id, existing] of entries.entries()) {
      if (!activeIds.has(id)) {
        if (existing.animationFrame !== null) {
          window.cancelAnimationFrame(existing.animationFrame);
        }
        existing.marker.remove();
        entries.delete(id);
      }
    }
  }, [fleetDrivers, hasMapOrigin, t]);

  const selectedOverviewRoute = selectedOverviewRouteId
    ? overviewRoutes.find((item) => item.id === selectedOverviewRouteId)
    : undefined;
  const layerStops = useMemo(
    () => selectedOverviewRoute?.stops.map((stop) => stop.location) ?? [],
    [selectedOverviewRoute],
  );
  const noMatchingRoutes = Boolean(filterKey) && overviewRoutes.length === 0;
  if (!hasMapOrigin) return <div className="flex h-full items-center justify-center bg-slate-50 px-6 text-center"><div className="max-w-sm"><MapPinned className="mx-auto text-blue-700" size={28} /><p role="status" className="mt-4 font-semibold text-slate-900">{noMatchingRoutes ? t('Không có tuyến phù hợp với bộ lọc.') : t('Chưa có kho xuất phát')}</p>{!noMatchingRoutes && <p className="mt-1 text-sm leading-6 text-slate-600">{t('Chọn đơn hoặc thiết lập kho mặc định để bắt đầu lập tuyến.')}</p>}</div></div>;
  if (!mapKey) return <div role="alert" className="flex h-full items-center justify-center bg-slate-100 px-6 text-center text-sm text-rose-700">{t("Chưa cấu hình NEXT_PUBLIC_GOONG_MAP_KEY cho Web Admin.")}</div>;

  const totalOverviewStops = overviewRoutes.reduce((sum, item) => sum + item.stops.length, 0);
  const isOverviewMode = overviewRoutes.length > 0;

  return <div className="relative isolate h-full w-full overflow-hidden bg-slate-100">
    <div
      ref={containerRef}
      className="h-full w-full"
      style={{ position: 'absolute', inset: 0, width: '100%', height: '100%' }}
      aria-label={t("Bản đồ điều phối theo đường phố")}
    />
    <div
      aria-hidden={isPlansOpen}
      className={`pointer-events-none absolute inset-x-2 bottom-4 z-10 flex items-end justify-between gap-2 transition-all duration-200 sm:inset-x-3 ${
        isPlansOpen ? 'translate-y-2 opacity-0' : 'translate-y-0 opacity-100'
      }`}
    >
      <div className="min-w-0 max-w-lg rounded-xl border border-slate-200/90 bg-white/95 px-3.5 py-2 text-slate-950 shadow-lg backdrop-blur-md">
        <div className="flex items-center gap-2 text-sm font-semibold">
          <Route className="shrink-0 text-[#17766c]" size={16} />
          {selectedOverviewRoute
            ? `${selectedOverviewRoute.label} · ${selectedOverviewRoute.stops.length} ${t('điểm giao')}`
            : isOverviewMode
              ? `${overviewRoutes.length} ${t('kế hoạch đang hiển thị')}`
              : noMatchingRoutes ? t('Không có tuyến phù hợp với bộ lọc.') : t('Theo dõi đội xe trực tiếp')}
        </div>
        <p role={mapError ? 'status' : undefined} className="mt-1 text-xs leading-5 text-slate-600">
          {mapError || (selectedOverviewRoute
            ? `${selectedOverviewRoute.driverName ? `${selectedOverviewRoute.driverName} · ` : ''}${t('Bấm vào tuyến khác để chuyển hoặc Đóng để xem tất cả')}`
            : isOverviewMode
              ? `${totalOverviewStops} ${t('điểm giao')}${fleetDrivers.length > 0 ? ` · ${fleetDrivers.length} ${t('tài xế trực tuyến')}` : ''} · ${t('Bấm vào tuyến để xem chi tiết')}`
              : noMatchingRoutes ? t('Đổi hoặc xóa bộ lọc để xem tuyến.') : `${fleetDrivers.length} ${t('tài xế đang trực tuyến')}`)}
        </p>
      </div>
      {readyMap && <RouteMapLayers
        map={readyMap}
        stops={layerStops}
        plannedLegs={EMPTY_LEGS}
        firstStopIndex={0}
        showStopMarkers={showDeliveryStops}
      />}
    </div>
    {readyMap && overviewRoutes.map((overviewRoute) => (
      <OverviewRoadLine
        key={overviewRoute.id}
        map={readyMap}
        route={overviewRoute}
        isSelected={(highlightedPlan?.planId ?? selectedOverviewRouteId) === overviewRoute.id}
        hasSelection={Boolean(highlightedPlan || selectedOverviewRoute)}
        showDeliveryStops={showDeliveryStops}
        highlightedStopId={highlightedPlan?.planId === overviewRoute.id ? highlightedPlan.stopId : undefined}
        onSelect={onSelectOverviewRoute}
        onHover={onHoverOverviewPlan}
      />
    ))}

  </div>;
}
