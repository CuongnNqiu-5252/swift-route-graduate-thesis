'use client';

import { useLocale } from '@/i18n/provider';

import { useEffect, useRef, useState } from 'react';
import {
  AttributionControl,
  LngLatBounds,
  Map as MapLibreMap,
  Marker,
  NavigationControl,
  Popup,
  setWorkerUrl,
} from 'maplibre-gl';
import type { PickupLocation } from '@/domain/entities/pickup-location.entity';
import 'maplibre-gl/dist/maplibre-gl.css';

setWorkerUrl(new URL('maplibre-gl/dist/maplibre-gl-worker.mjs', import.meta.url).toString());

export interface PickupLocationsMapProps {
  locations: PickupLocation[];
}

function createMarkerElement(location: PickupLocation) {
  const element = document.createElement('button');
  element.type = 'button';
  element.setAttribute('aria-label', `Xem ${location.name} trên bản đồ`);
  element.title = location.name;
  element.style.width = location.is_default ? '30px' : '26px';
  element.style.height = location.is_default ? '30px' : '26px';
  element.style.borderRadius = '50% 50% 50% 0';
  element.style.background = location.status === 'ACTIVE' ? '#2563eb' : '#64748b';
  element.style.border = location.is_default ? '4px solid white' : '3px solid white';
  element.style.boxShadow = '0 5px 14px rgb(15 23 42 / 30%)';
  element.style.transform = 'rotate(-45deg)';
  element.style.cursor = 'pointer';
  return element;
}

function createPopupContent(location: PickupLocation) {
  const root = document.createElement('div');
  root.style.maxWidth = '240px';

  const heading = document.createElement('p');
  heading.textContent = location.name;
  heading.style.fontSize = '14px';
  heading.style.fontWeight = '700';
  heading.style.color = '#0f172a';
  root.appendChild(heading);

  const address = document.createElement('p');
  address.textContent = location.address;
  address.style.marginTop = '4px';
  address.style.fontSize = '12px';
  address.style.lineHeight = '18px';
  address.style.color = '#475569';
  root.appendChild(address);

  const status = document.createElement('p');
  status.textContent = [location.is_default ? 'Điểm mặc định' : null, location.status === 'ACTIVE' ? 'Đang hoạt động' : 'Ngừng hoạt động'].filter(Boolean).join(' · ');
  status.style.marginTop = '8px';
  status.style.fontSize = '11px';
  status.style.fontWeight = '600';
  status.style.color = location.status === 'ACTIVE' ? '#1d4ed8' : '#64748b';
  root.appendChild(status);

  return root;
}

export default function PickupLocationsMapInner({ locations }: PickupLocationsMapProps) {
  const { t } = useLocale();
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<MapLibreMap | null>(null);
  const markersRef = useRef<Marker[]>([]);
  const [mapError, setMapError] = useState('');
  const mapKey = process.env.NEXT_PUBLIC_GOONG_MAP_KEY;

  useEffect(() => {
    if (!containerRef.current || mapRef.current || !mapKey) return;
    const instance = new MapLibreMap({
      container: containerRef.current,
      style: `https://tiles.goong.io/assets/goong_map_web.json?api_key=${encodeURIComponent(mapKey)}`,
      center: [105.78, 10.03],
      zoom: 11,
      attributionControl: false,
    });
    instance.addControl(new NavigationControl({ showCompass: false }), 'top-right');
    instance.addControl(new AttributionControl({ compact: true, customAttribution: '© Goong' }), 'bottom-right');
    instance.on('load', () => setMapError(''));
    instance.on('error', () => setMapError('Không tải được nền bản đồ Goong. Hãy kiểm tra Map Key hoặc kết nối mạng.'));
    mapRef.current = instance;

    const resize = new ResizeObserver(() => instance.resize());
    resize.observe(containerRef.current);
    return () => {
      resize.disconnect();
      markersRef.current.forEach((marker) => marker.remove());
      markersRef.current = [];
      instance.remove();
      mapRef.current = null;
    };
  }, [mapKey]);

  useEffect(() => {
    const instance = mapRef.current;
    if (!instance) return;

    markersRef.current.forEach((marker) => marker.remove());
    markersRef.current = [];
    const validLocations = locations.filter((location) => Number.isFinite(location.lat) && Number.isFinite(location.lng));
    if (validLocations.length === 0) return;

    const bounds = new LngLatBounds();
    markersRef.current = validLocations.map((location) => {
      bounds.extend([location.lng, location.lat]);
      const popup = new Popup({ offset: 22, closeButton: false, maxWidth: '280px' }).setDOMContent(createPopupContent(location));
      return new Marker({ element: createMarkerElement(location) })
        .setLngLat([location.lng, location.lat])
        .setPopup(popup)
        .addTo(instance);
    });

    const frameLocations = () => {
      if (validLocations.length === 1) {
        instance.easeTo({ center: [validLocations[0].lng, validLocations[0].lat], zoom: 15, duration: 500 });
      } else {
        instance.fitBounds(bounds, { padding: 64, maxZoom: 15, duration: 500 });
      }
    };
    if (instance.loaded()) frameLocations();
    else instance.once('load', frameLocations);

    return () => {
      instance.off('load', frameLocations);
      markersRef.current.forEach((marker) => marker.remove());
      markersRef.current = [];
    };
  }, [locations]);

  if (!mapKey) return <div className="flex h-80 items-center justify-center bg-slate-100 px-6 text-center text-sm text-rose-700">Chưa cấu hình NEXT_PUBLIC_GOONG_MAP_KEY cho Web Admin.</div>;

  return <div className="relative">
    <div ref={containerRef} className="h-80 w-full" aria-label={t("Bản đồ các điểm lấy hàng của doanh nghiệp")} />
    {mapError && <div role="alert" className="absolute inset-x-3 bottom-3 rounded-lg bg-white px-3 py-2 text-xs text-rose-700 shadow-lg">{mapError}</div>}
  </div>;
}
