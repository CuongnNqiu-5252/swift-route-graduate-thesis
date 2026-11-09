'use client';

import { useEffect, useMemo, useRef, useState } from 'react';
import { AttributionControl, LngLatBounds, Map as MapLibreMap, Marker, NavigationControl, Popup, setWorkerUrl } from 'maplibre-gl';
import type { GeoJSONSource } from 'maplibre-gl';
import { AlertTriangle, LoaderCircle, MapPinned, Route } from 'lucide-react';
import { useRoadRoute } from '@/application/hooks/use-road-route';
import { DispatchDepot, OptimizationStop } from '@/domain/entities/routing.entity';
import { formatRoadDistance } from '@/lib/route-format';
import type { TrackingSignal } from '@/application/hooks/use-order-live-tracking';
import 'maplibre-gl/dist/maplibre-gl.css';

if (typeof window !== 'undefined') {
  setWorkerUrl('/maplibre-gl-worker.mjs');
}

interface RouteMapInnerProps {
  depot: DispatchDepot | null;
  stops: OptimizationStop[];
  driver?: [number, number];
  driverName?: string;
  driverVehicle?: string;
  trackingSignal?: TrackingSignal;
  driverIncident?: { id: string; label: string; count: number };
  routeOrigin?: [number, number];
  skipPickup?: boolean;
  onPickOrigin?: (position: [number, number]) => void;
  onRouteMetricsChange?: (metrics: { distanceMeters: number; durationSeconds: number } | null) => void;
  onRouteCoordinatesChange?: (waypoints: Array<{ lat: number; lng: number }>) => void;
}

function markerElement(label: string, tone: 'depot' | 'stop' | 'driver') {
  const element = document.createElement('button');
  element.type = 'button';
  element.textContent = label;
  element.setAttribute(
    'aria-label',
    tone === 'depot' ? 'Xem kho xuất phát' : tone === 'driver' ? 'Vị trí tài xế trực tiếp' : `Xem điểm giao ${label}`,
  );
  Object.assign(element.style, {
    width: tone === 'depot' ? '36px' : tone === 'driver' ? '34px' : '30px',
    height: tone === 'depot' ? '36px' : tone === 'driver' ? '34px' : '30px',
    borderRadius: tone === 'depot' ? '11px' : '50%',
    border: '3px solid white',
    background: tone === 'depot' ? '#0f172a' : tone === 'driver' ? '#059669' : '#dc2626',
    color: 'white',
    fontSize: '12px',
    fontWeight: '800',
    boxShadow: tone === 'driver'
      ? '0 0 0 6px rgb(5 150 105 / 26%), 0 6px 18px rgb(15 23 42 / 34%)'
      : '0 6px 18px rgb(15 23 42 / 30%)',
    cursor: 'pointer',
    zIndex: tone === 'driver' ? '20' : '10',
  });
  return element;
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

export default function RouteMapInner({
  depot,
  stops,
  driver,
  driverName,
  driverVehicle,
  trackingSignal = 'waiting',
  driverIncident,
  routeOrigin,
  skipPickup = false,
  onPickOrigin,
  onRouteMetricsChange,
  onRouteCoordinatesChange,
}: RouteMapInnerProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<MapLibreMap | null>(null);
  const markersRef = useRef<Marker[]>([]);
  const driverMarkerRef = useRef<Marker | null>(null);
  const incidentMarkerRef = useRef<Marker | null>(null);
  const driverAnimationFrameRef = useRef<number | null>(null);
  const displayedDriverRef = useRef<[number, number] | null>(null);
  const [mapError, setMapError] = useState('');
  const pickOriginRef = useRef(onPickOrigin);
  useEffect(() => {
    pickOriginRef.current = onPickOrigin;
  }, [onPickOrigin]);
  const mapKey = process.env.NEXT_PUBLIC_GOONG_MAP_KEY;
  const pointsKey = useMemo(() => {
    if (!depot) return '';
    const raw = [
      ...(routeOrigin ? [{ lat: routeOrigin[0], lng: routeOrigin[1] }] : []),
      ...(!skipPickup ? [{ lat: depot.lat, lng: depot.lng }] : []),
      ...stops.map((stop) => ({ lat: stop.location.lat, lng: stop.location.lng })),
    ].filter((point) => Number.isFinite(point.lat) && Number.isFinite(point.lng));
    return raw.map((p) => `${p.lat.toFixed(6)},${p.lng.toFixed(6)}`).join('|');
  }, [depot, stops, routeOrigin, skipPickup]);

  const points = useMemo(
    () =>
      pointsKey
        ? pointsKey.split('|').map((pair) => {
            const [lat, lng] = pair.split(',').map(Number);
            return { lat, lng };
          })
        : [],
    [pointsKey],
  );
  const route = useRoadRoute(points);

  const lastMetricsKeyRef = useRef<string>('');
  const lastWaypointsKeyRef = useRef<string>('');

  useEffect(() => {
    const metricsKey =
      route.coordinates.length > 1
        ? `${route.distanceMeters}:${route.durationSeconds}`
        : 'null';
    if (lastMetricsKeyRef.current !== metricsKey) {
      lastMetricsKeyRef.current = metricsKey;
      onRouteMetricsChange?.(
        route.coordinates.length > 1
          ? { distanceMeters: route.distanceMeters, durationSeconds: route.durationSeconds }
          : null,
      );
    }

    const waypoints =
      route.coordinates.length > 1
        ? route.coordinates.map(([lng, lat]) => ({ lat, lng }))
        : points;
    const waypointsKey = waypoints
      .map((p) => `${p.lat.toFixed(5)},${p.lng.toFixed(5)}`)
      .join('|');
    if (lastWaypointsKeyRef.current !== waypointsKey) {
      lastWaypointsKeyRef.current = waypointsKey;
      onRouteCoordinatesChange?.(waypoints);
    }
  }, [onRouteCoordinatesChange, onRouteMetricsChange, points, route.coordinates, route.distanceMeters, route.durationSeconds]);

  useEffect(() => {
    if (!containerRef.current || mapRef.current || !mapKey || !depot) return;
    const map = new MapLibreMap({
      container: containerRef.current,
      style: `https://tiles.goong.io/assets/goong_map_web.json?api_key=${encodeURIComponent(mapKey)}`,
      center: [depot.lng, depot.lat], zoom: 13, attributionControl: false,
    });
    map.addControl(new NavigationControl({ showCompass: false }), 'top-right');
    map.addControl(new AttributionControl({ compact: true, customAttribution: '© Goong' }), 'bottom-right');
    map.on('load', () => setMapError(''));
    map.on('error', (e) => {
      console.error('[MapLibre error]', e);
      setMapError('Không tải được nền bản đồ Goong. Kiểm tra Map Key và kết nối mạng.');
    });
    mapRef.current = map;
    map.on('click', (event) => pickOriginRef.current?.([event.lngLat.lat, event.lngLat.lng]));
    const resize = new ResizeObserver(() => map.resize());
    resize.observe(containerRef.current);
    return () => {
      resize.disconnect();
      markersRef.current.forEach((marker) => marker.remove());
      markersRef.current = [];
      if (driverAnimationFrameRef.current !== null) {
        window.cancelAnimationFrame(driverAnimationFrameRef.current);
        driverAnimationFrameRef.current = null;
      }
      driverMarkerRef.current?.remove();
      driverMarkerRef.current = null;
      displayedDriverRef.current = null;
      incidentMarkerRef.current?.remove();
      incidentMarkerRef.current = null;
      map.remove();
      mapRef.current = null;
    };
  }, [depot, mapKey]);

  // Static depot + delivery stop markers & camera bounds (does not re-run on 1s driver GPS ticks)
  useEffect(() => {
    const map = mapRef.current;
    if (!map || !depot) return;
    markersRef.current.forEach((marker) => marker.remove());
    const bounds = new LngLatBounds().extend([depot.lng, depot.lat]);
    const depotMarker = new Marker({ element: markerElement('K', 'depot') })
      .setLngLat([depot.lng, depot.lat])
      .setPopup(new Popup({ offset: 22, closeButton: false }).setDOMContent(popupContent(depot.name, depot.address, 'Kho xuất phát')))
      .addTo(map);
    markersRef.current = [depotMarker, ...stops.map((stop, index) => {
      bounds.extend([stop.location.lng, stop.location.lat]);
      return new Marker({ element: markerElement(String(index + 1), 'stop') })
        .setLngLat([stop.location.lng, stop.location.lat])
        .setPopup(new Popup({ offset: 20, closeButton: false }).setDOMContent(popupContent(
          `Điểm giao ${index + 1}`, stop.order?.delivery_address || 'Chưa có địa chỉ hiển thị',
          stop.order?.order_code ? `Đơn ${stop.order.order_code}` : 'Điểm trong kế hoạch',
        ))).addTo(map);
    })];
    const frame = () => points.length === 1
      ? map.easeTo({ center: [depot.lng, depot.lat], zoom: 15, duration: 450 })
      : map.fitBounds(bounds, { padding: 76, maxZoom: 15, duration: 550 });
    if (map.loaded()) frame(); else map.once('load', frame);
    return () => { map.off('load', frame); };
  }, [depot, points.length, stops]);

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
      driverName || 'Vị trí tài xế',
      `${driverVehicle ? `${driverVehicle} · ` : ''}Toạ độ ${lat.toFixed(5)}, ${lng.toFixed(5)}`,
      trackingSignal === 'live' ? 'GPS trực tiếp từ ứng dụng tài xế' : 'Vị trí gần nhất nhận từ tài xế',
    );
    if (!driverMarkerRef.current) {
      driverMarkerRef.current = new Marker({ element: markerElement('TX', 'driver') })
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
      const updateMarkers = (position: [number, number]) => {
        driverMarkerRef.current?.setLngLat(position);
        incidentMarkerRef.current?.setLngLat(position);
        displayedDriverRef.current = position;
      };
      const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
      if (reduceMotion) {
        updateMarkers(target);
        driverAnimationFrameRef.current = null;
        return;
      }

      const startedAt = performance.now();
      const duration = 850;
      const animate = (now: number) => {
        const progress = Math.min(1, (now - startedAt) / duration);
        const eased = 1 - Math.pow(1 - progress, 3);
        updateMarkers([
          from[0] + (target[0] - from[0]) * eased,
          from[1] + (target[1] - from[1]) * eased,
        ]);
        if (progress < 1) {
          driverAnimationFrameRef.current = window.requestAnimationFrame(animate);
        } else {
          driverAnimationFrameRef.current = null;
        }
      };
      driverAnimationFrameRef.current = window.requestAnimationFrame(animate);
    }
  }, [driver, driverName, driverVehicle, trackingSignal]);

  // A separate marker keeps the warning visible above the driver as GPS moves.
  useEffect(() => {
    const map = mapRef.current;
    if (!map || !driverIncident || !driver || !driver.every(Number.isFinite)) {
      incidentMarkerRef.current?.remove();
      incidentMarkerRef.current = null;
      return;
    }
    const link = (incidentMarkerRef.current?.getElement() as HTMLAnchorElement | undefined)
      ?? document.createElement('a');
    link.href = `/operations?incident=${encodeURIComponent(driverIncident.id)}`;
    link.textContent = `${driverIncident.count > 1 ? `${driverIncident.count} sự cố` : 'Có sự cố'} · Xử lý ngay`;
    link.title = driverIncident.label;
    link.setAttribute('aria-label', `${driverName || 'Tài xế'}: ${driverIncident.label}. Mở xử lý sự cố`);
    if (!incidentMarkerRef.current) {
      link.className = 'rounded-lg bg-rose-700 px-3 py-2 text-xs font-semibold text-white focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-rose-700 hover:bg-rose-800';
      link.addEventListener('click', (event) => event.stopPropagation());
      const displayedPosition = displayedDriverRef.current ?? [driver[1], driver[0]];
      incidentMarkerRef.current = new Marker({ element: link, anchor: 'bottom', offset: [0, -27] })
        .setLngLat(displayedPosition)
        .addTo(map);
    }
  }, [driver, driverIncident, driverName]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || points.length < 2) return;
    const coordinates = route.coordinates.length > 1 ? route.coordinates : points.map((point) => [point.lng, point.lat] as [number, number]);
    const update = () => {
      const data = { type: 'Feature' as const, properties: {}, geometry: { type: 'LineString' as const, coordinates } };
      const source = map.getSource('dispatch-route');
      if (source) (source as GeoJSONSource).setData(data);
      else {
        map.addSource('dispatch-route', { type: 'geojson', data });
        map.addLayer({ id: 'dispatch-route-line', type: 'line', source: 'dispatch-route', layout: { 'line-cap': 'round', 'line-join': 'round' }, paint: { 'line-color': '#2563eb', 'line-width': 5, 'line-opacity': 0.88 } });
      }
      if (map.getLayer('dispatch-route-line')) {
        map.setPaintProperty('dispatch-route-line', 'line-color', route.coordinates.length > 1 ? '#2563eb' : '#64748b');
        map.setPaintProperty('dispatch-route-line', 'line-width', route.coordinates.length > 1 ? 5 : 3);
        map.setPaintProperty('dispatch-route-line', 'line-dasharray', route.coordinates.length > 1 ? [1, 0] : [2, 2]);
      }
    };
    if (map.loaded()) update(); else map.once('load', update);
    return () => { map.off('load', update); };
  }, [points, route.coordinates]);

  if (!depot) return <div className="flex h-full min-h-[480px] items-center justify-center bg-slate-50 px-6 text-center"><div className="max-w-sm"><MapPinned className="mx-auto text-blue-700" size={28} /><p className="mt-4 font-semibold text-slate-900">Chưa có kho xuất phát</p><p className="mt-1 text-sm leading-6 text-slate-600">Chọn đơn hoặc thiết lập kho mặc định để bắt đầu lập tuyến.</p></div></div>;
  if (!mapKey) return <div role="alert" className="flex h-full min-h-[480px] items-center justify-center bg-slate-100 px-6 text-center text-sm text-rose-700">Chưa cấu hình NEXT_PUBLIC_GOONG_MAP_KEY cho Web Admin.</div>;

  return <div className="relative isolate h-full min-h-[480px] w-full overflow-hidden bg-slate-100">
    <div
      ref={containerRef}
      className="h-full min-h-[480px] w-full"
      style={{ position: 'absolute', inset: 0, width: '100%', height: '100%' }}
      aria-label="Bản đồ điều phối theo đường phố"
    />
    <div className="pointer-events-none absolute inset-x-3 top-3 z-10 flex justify-start"><div className="max-w-[min(92%,32rem)] rounded-xl bg-slate-950/90 px-3.5 py-3 text-white shadow-[0_10px_30px_rgb(15_23 42/0.24)]">
      <div className="flex items-center gap-2 text-sm font-semibold">{route.isLoading ? <LoaderCircle className="animate-spin text-blue-300" size={16} /> : route.error ? <AlertTriangle className="text-amber-300" size={16} /> : <Route className="text-blue-300" size={16} />}{driverName ? `${driverName} · ${stops.length} điểm giao` : route.isLoading ? 'Đang tính tuyến đường phố' : route.error ? 'Chưa tải được tuyến Goong' : route.coordinates.length ? 'Tuyến xe máy theo Goong' : 'Thứ tự giao dự kiến'}</div>
      <p className="mt-1 text-xs leading-5 text-slate-300">{route.error || (route.coordinates.length ? `${formatRoadDistance(route.distanceMeters)} · khoảng ${Math.max(1, Math.round(route.durationSeconds / 60))} phút${driver ? ' · marker TX là GPS tài xế' : ''}` : `${stops.length} điểm giao; thêm điểm để tính đường đi.`)}</p>
    </div></div>
    {(mapError || route.error) && <div role="status" className="absolute inset-x-3 bottom-3 z-10 rounded-lg bg-white px-3 py-2 text-xs text-slate-700 shadow-[0_8px_24px_rgb(15_23_42/0.18)]">{mapError || 'Đang hiển thị đường nối thứ tự; dữ liệu đơn hàng vẫn được giữ nguyên.'}</div>}
  </div>;
}
