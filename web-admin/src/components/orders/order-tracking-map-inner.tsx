'use client';

import { useEffect, useMemo, useRef, useState } from 'react';
import { AttributionControl, LngLatBounds, Map as MapLibreMap, Marker, NavigationControl, setWorkerUrl } from 'maplibre-gl';
import type { GeoJSONSource } from 'maplibre-gl';
import { AlertTriangle, LoaderCircle, Navigation } from 'lucide-react';
import { useRoadRoute } from '@/application/hooks/use-road-route';
import 'maplibre-gl/dist/maplibre-gl.css';

if (typeof window !== 'undefined') {
  setWorkerUrl('/maplibre-gl-worker.mjs');
}

type Coordinate = [number, number];

function marker(color: string, label: string, pulse = false) {
  const element = document.createElement('div');
  element.setAttribute('aria-label', label);
  element.title = label;
  Object.assign(element.style, {
    width: pulse ? '24px' : '18px', height: pulse ? '24px' : '18px', borderRadius: '50%',
    background: color, border: pulse ? '5px solid white' : '3px solid white',
    boxShadow: pulse ? `0 0 0 6px ${color}33, 0 6px 16px rgb(15 23 42 / 28%)` : '0 4px 12px rgb(15 23 42 / 25%)',
  });
  return element;
}

export default function OrderTrackingMap({ pickup, delivery, driver }: { pickup: Coordinate; delivery: Coordinate; driver?: Coordinate }) {
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<MapLibreMap | null>(null);
  const markersRef = useRef<Marker[]>([]);
  const [mapError, setMapError] = useState('');
  const mapKey = process.env.NEXT_PUBLIC_GOONG_MAP_KEY;
  const [pickupLat, pickupLng] = pickup;
  const [deliveryLat, deliveryLng] = delivery;
  const driverLat = driver?.[0];
  const driverLng = driver?.[1];
  const routePoints = useMemo(() => {
    const origin = driverLat != null && driverLng != null ? [driverLat, driverLng] : [pickupLat, pickupLng];
    return [{ lat: origin[0], lng: origin[1] }, { lat: deliveryLat, lng: deliveryLng }];
  }, [deliveryLat, deliveryLng, driverLat, driverLng, pickupLat, pickupLng]);
  const route = useRoadRoute(routePoints);

  useEffect(() => {
    if (!containerRef.current || mapRef.current || !mapKey) return;
    const map = new MapLibreMap({
      container: containerRef.current,
      style: `https://tiles.goong.io/assets/goong_map_web.json?api_key=${encodeURIComponent(mapKey)}`,
      center: [pickupLng, pickupLat], zoom: 13, attributionControl: false,
    });
    map.addControl(new NavigationControl({ showCompass: false }), 'top-right');
    map.addControl(new AttributionControl({ compact: true, customAttribution: '© Goong' }), 'bottom-right');
    map.on('error', () => setMapError('Không tải được nền bản đồ Goong.'));
    map.on('load', () => setMapError(''));
    mapRef.current = map;
    const resize = new ResizeObserver(() => map.resize());
    resize.observe(containerRef.current);
    return () => {
      resize.disconnect(); markersRef.current.forEach((item) => item.remove()); markersRef.current = [];
      map.remove(); mapRef.current = null;
    };
  }, [mapKey, pickupLat, pickupLng]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;
    markersRef.current.forEach((item) => item.remove());
    const pickupMarker = new Marker({ element: marker('#0f766e', 'Điểm lấy hàng') }).setLngLat([pickupLng, pickupLat]).addTo(map);
    const deliveryMarker = new Marker({ element: marker('#e11d48', 'Điểm giao hàng') }).setLngLat([deliveryLng, deliveryLat]).addTo(map);
    const driverMarker = driverLat != null && driverLng != null ? new Marker({ element: marker('#2563eb', 'Vị trí tài xế', true) }).setLngLat([driverLng, driverLat]).addTo(map) : null;
    markersRef.current = [pickupMarker, deliveryMarker, ...(driverMarker ? [driverMarker] : [])];
    const bounds = new LngLatBounds().extend([pickupLng, pickupLat]).extend([deliveryLng, deliveryLat]);
    if (driverLat != null && driverLng != null) bounds.extend([driverLng, driverLat]);
    const frame = () => map.fitBounds(bounds, { padding: 58, maxZoom: 16, duration: 450 });
    if (map.loaded()) frame(); else map.once('load', frame);
    return () => { map.off('load', frame); };
  }, [deliveryLat, deliveryLng, driverLat, driverLng, pickupLat, pickupLng]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;
    const coordinates = route.coordinates.length > 1 ? route.coordinates : routePoints.map((point) => [point.lng, point.lat] as Coordinate);
    const update = () => {
      const data = { type: 'Feature' as const, properties: {}, geometry: { type: 'LineString' as const, coordinates } };
      const source = map.getSource('order-route');
      if (source) (source as GeoJSONSource).setData(data);
      else {
        map.addSource('order-route', { type: 'geojson', data });
        map.addLayer({ id: 'order-route-line', type: 'line', source: 'order-route', layout: { 'line-cap': 'round', 'line-join': 'round' }, paint: { 'line-color': '#2563eb', 'line-width': 5, 'line-opacity': 0.88 } });
      }
      if (map.getLayer('order-route-line')) {
        map.setPaintProperty('order-route-line', 'line-color', route.coordinates.length > 1 ? '#2563eb' : '#64748b');
        map.setPaintProperty('order-route-line', 'line-dasharray', route.coordinates.length > 1 ? [1, 0] : [2, 2]);
      }
    };
    if (map.loaded()) update(); else map.once('load', update);
    return () => { map.off('load', update); };
  }, [route.coordinates, routePoints]);

  if (!mapKey) return <div role="alert" className="flex h-80 items-center justify-center bg-slate-100 px-6 text-center text-sm text-rose-700">Chưa cấu hình NEXT_PUBLIC_GOONG_MAP_KEY cho Web Admin.</div>;
  return <div className="relative h-80 w-full overflow-hidden bg-slate-100">
    <div
      ref={containerRef}
      className="h-80 w-full"
      style={{ position: 'absolute', inset: 0, width: '100%', height: '100%' }}
      aria-label="Bản đồ theo dõi đơn hàng và tài xế"
    />
    <div className="pointer-events-none absolute start-3 top-3 z-10 max-w-[calc(100%-5rem)] rounded-xl bg-slate-950/90 px-3 py-2.5 text-white shadow-[0_8px_24px_rgb(15_23_42/0.22)]">
      <div className="flex items-center gap-2 text-xs font-semibold">{route.isLoading ? <LoaderCircle className="animate-spin text-blue-300" size={14} /> : route.error ? <AlertTriangle className="text-amber-300" size={14} /> : <Navigation className="text-blue-300" size={14} />}{route.isLoading ? 'Đang cập nhật tuyến' : driver ? 'Tài xế đang tới điểm giao' : 'Tuyến giao dự kiến'}</div>
      <p className="mt-1 text-[11px] text-slate-300">{route.coordinates.length ? `${(route.distanceMeters / 1000).toFixed(1)} km · ${Math.max(1, Math.round(route.durationSeconds / 60))} phút` : route.error || 'Đang chờ dữ liệu đường đi'}</p>
    </div>
    {mapError && <div role="alert" className="absolute inset-x-3 bottom-3 z-10 rounded-lg bg-white px-3 py-2 text-xs text-rose-700 shadow-lg">{mapError}</div>}
  </div>;
}
