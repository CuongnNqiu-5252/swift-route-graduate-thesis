'use client';

import { useLocale } from '@/i18n/provider';

import axios from 'axios';
import { useCallback, useEffect, useId, useRef, useState } from 'react';
import {
  AttributionControl,
  type GeoJSONSource,
  Map as MapLibreMap,
  Marker as MapLibreMarker,
  NavigationControl,
  setWorkerUrl,
} from 'maplibre-gl';
import { Loader2, LocateFixed, MapPin, Search } from 'lucide-react';
import 'maplibre-gl/dist/maplibre-gl.css';
import {
  createCircleFeatureCollection,
  getCircleBounds,
} from '@/components/settings/delivery-zone-geometry';
import type { TenantDeliveryZone } from '@/domain/entities/delivery-zone.entity';
import api from '@/lib/api';

if (typeof window !== 'undefined') {
  setWorkerUrl('/maplibre-gl-worker.mjs');
}

export interface LocationMapPickerProps {
  lat: number;
  lng: number;
  address?: string;
  onChange: (lat: number, lng: number, address?: string) => void;
  onResolvingChange?: (resolving: boolean) => void;
  deliveryZone?: TenantDeliveryZone | null;
  zoneStatus?: 'inside' | 'warn' | 'blocked' | null;
  hasSelection?: boolean;
}

const ZONE_CIRCLE_SOURCE_ID = 'picker-delivery-zone-circle';
const ZONE_CIRCLE_FILL_LAYER_ID = 'picker-delivery-zone-circle-fill';
const ZONE_CIRCLE_LINE_LAYER_ID = 'picker-delivery-zone-circle-line';

function markerColorForZoneStatus(zoneStatus?: 'inside' | 'warn' | 'blocked' | null): string {
  if (zoneStatus === 'inside') return '#15803d';
  if (zoneStatus === 'warn') return '#d97706';
  if (zoneStatus === 'blocked') return '#dc2626';
  return '#2563eb';
}

interface Prediction {
  place_id: string;
  description: string;
  structured_formatting?: { main_text?: string; secondary_text?: string };
}

function createMarkerElement() {
  const element = document.createElement('div');
  element.setAttribute('aria-label', 'Điểm lấy hàng đã chọn');
  element.style.width = '24px';
  element.style.height = '24px';
  element.style.borderRadius = '50% 50% 50% 0';
  element.style.background = '#2563eb';
  element.style.border = '3px solid white';
  element.style.boxShadow = '0 4px 12px rgb(15 23 42 / 28%)';
  element.style.transform = 'rotate(-45deg)';
  return element;
}

export default function LocationMapPickerInner({
  lat,
  lng,
  address,
  onChange,
  onResolvingChange,
  deliveryZone,
  zoneStatus,
  hasSelection,
}: LocationMapPickerProps) {
  const { t } = useLocale();
  const mapContainer = useRef<HTMLDivElement>(null);
  const map = useRef<MapLibreMap | null>(null);
  const selectedMarker = useRef<MapLibreMarker | null>(null);
  const mapLoadedRef = useRef(false);
  const zoneFittedRef = useRef(false);
  const latestCoordinates = useRef({ lat, lng });
  const deliveryZoneRef = useRef(deliveryZone);
  const hasSelectionRef = useRef(hasSelection);
  const onChangeRef = useRef(onChange);
  const onResolvingChangeRef = useRef(onResolvingChange);
  const [query, setQuery] = useState(address ?? '');
  const [predictions, setPredictions] = useState<Prediction[]>([]);
  const [searching, setSearching] = useState(false);
  const [resolving, setResolving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [mapError, setMapError] = useState<string | null>(null);
  const [searchEnabled, setSearchEnabled] = useState(false);
  const generatedId = useId();
  const sessionToken = useRef(generatedId);
  const mapKey = process.env.NEXT_PUBLIC_GOONG_MAP_KEY;

  useEffect(() => {
    onChangeRef.current = onChange;
    onResolvingChangeRef.current = onResolvingChange;
  }, [onChange, onResolvingChange]);

  useEffect(() => {
    hasSelectionRef.current = hasSelection;
  }, [hasSelection]);

  const syncZoneOverlay = useCallback((zone: TenantDeliveryZone | null | undefined) => {
    const instance = map.current;
    if (!instance || !mapLoadedRef.current) return;

    const hasActiveZone = Boolean(
      zone?.configured &&
        zone.is_active &&
        zone.center_lat !== null &&
        zone.center_lng !== null &&
        zone.radius_km !== null,
    );

    if (!hasActiveZone || !zone || zone.center_lat === null || zone.center_lng === null || zone.radius_km === null) {
      if (instance.getLayer(ZONE_CIRCLE_FILL_LAYER_ID)) {
        instance.setLayoutProperty(ZONE_CIRCLE_FILL_LAYER_ID, 'visibility', 'none');
      }
      if (instance.getLayer(ZONE_CIRCLE_LINE_LAYER_ID)) {
        instance.setLayoutProperty(ZONE_CIRCLE_LINE_LAYER_ID, 'visibility', 'none');
      }
      return;
    }

    const circleData = createCircleFeatureCollection(zone.center_lat, zone.center_lng, zone.radius_km);
    const zoneColor = zone.enforcement_mode === 'STRICT_BLOCK' ? '#17766c' : '#d97706';

    const existingSource = instance.getSource(ZONE_CIRCLE_SOURCE_ID) as GeoJSONSource | undefined;
    if (existingSource) {
      existingSource.setData(circleData);
    } else {
      instance.addSource(ZONE_CIRCLE_SOURCE_ID, {
        type: 'geojson',
        data: circleData,
      });
      instance.addLayer({
        id: ZONE_CIRCLE_FILL_LAYER_ID,
        type: 'fill',
        source: ZONE_CIRCLE_SOURCE_ID,
        paint: {
          'fill-color': zoneColor,
          'fill-opacity': 0.12,
        },
      });
      instance.addLayer({
        id: ZONE_CIRCLE_LINE_LAYER_ID,
        type: 'line',
        source: ZONE_CIRCLE_SOURCE_ID,
        paint: {
          'line-color': zoneColor,
          'line-width': 2,
          'line-dasharray': [2, 2],
        },
      });
    }

    if (instance.getLayer(ZONE_CIRCLE_FILL_LAYER_ID)) {
      instance.setPaintProperty(ZONE_CIRCLE_FILL_LAYER_ID, 'fill-color', zoneColor);
      instance.setLayoutProperty(ZONE_CIRCLE_FILL_LAYER_ID, 'visibility', 'visible');
    }
    if (instance.getLayer(ZONE_CIRCLE_LINE_LAYER_ID)) {
      instance.setPaintProperty(ZONE_CIRCLE_LINE_LAYER_ID, 'line-color', zoneColor);
      instance.setLayoutProperty(ZONE_CIRCLE_LINE_LAYER_ID, 'visibility', 'visible');
    }

    if (!zoneFittedRef.current && hasSelectionRef.current === false) {
      const bounds = getCircleBounds(zone.center_lat, zone.center_lng, zone.radius_km);
      instance.fitBounds(bounds, { padding: 40, maxZoom: 14, duration: 0 });
      zoneFittedRef.current = true;
    }
  }, []);

  useEffect(() => {
    deliveryZoneRef.current = deliveryZone;
    syncZoneOverlay(deliveryZone);
  }, [deliveryZone, syncZoneOverlay]);

  useEffect(() => {
    const markerElement = selectedMarker.current?.getElement();
    if (markerElement) {
      markerElement.style.background = markerColorForZoneStatus(zoneStatus);
    }
  }, [zoneStatus]);

  useEffect(() => {
    latestCoordinates.current = { lat, lng };
    selectedMarker.current?.setLngLat([lng, lat]);
    if (map.current && map.current.loaded()) {
      if (hasSelection === false && deliveryZoneRef.current?.configured && deliveryZoneRef.current.is_active) {
        if (!zoneFittedRef.current) {
          map.current.setCenter([lng, lat]);
        }
      } else {
        map.current.easeTo({ center: [lng, lat], zoom: Math.max(map.current.getZoom(), 15), duration: 500 });
      }
    }
  }, [hasSelection, lat, lng]);

  const resolveCoordinates = useCallback(async (nextLat: number, nextLng: number) => {
    setPredictions([]);
    setSearchEnabled(false);
    setResolving(true);
    setError(null);
    setQuery('');
    onChangeRef.current(nextLat, nextLng);
    onResolvingChangeRef.current?.(true);
    try {
      const { data: body } = await api.get<{ address?: string; error?: string }>('/api/maps/reverse', {
        baseURL: '',
        params: { lat: nextLat, lng: nextLng },
      });
      if (!body.address) throw new Error(body.error || t("Không xác định được địa chỉ."));
      setQuery(body.address);
      onChangeRef.current(nextLat, nextLng, body.address);
    } catch {
      setError('Đã chọn tọa độ, nhưng Goong chưa trả về địa chỉ. Hãy nhập địa chỉ chi tiết thủ công trước khi lưu.');
    } finally {
      setResolving(false);
      onResolvingChangeRef.current?.(false);
    }
  }, [t]);

  useEffect(() => {
    if (!mapContainer.current || map.current || !mapKey) return;

    const instance = new MapLibreMap({
      container: mapContainer.current,
      style: `https://tiles.goong.io/assets/goong_map_web.json?api_key=${encodeURIComponent(mapKey)}`,
      center: [latestCoordinates.current.lng, latestCoordinates.current.lat],
      zoom: 14,
      attributionControl: false,
    });
    const marker = new MapLibreMarker({ element: createMarkerElement(), draggable: true })
      .setLngLat([latestCoordinates.current.lng, latestCoordinates.current.lat])
      .addTo(instance);

    instance.addControl(new NavigationControl({ showCompass: false }), 'top-left');
    instance.addControl(new AttributionControl({ compact: true, customAttribution: '© Goong' }), 'bottom-right');
    instance.on('click', ({ lngLat }) => void resolveCoordinates(lngLat.lat, lngLat.lng));
    instance.on('load', () => {
      mapLoadedRef.current = true;
      setMapError(null);
      syncZoneOverlay(deliveryZoneRef.current);
    });
    instance.on('error', () => setMapError('Không tải được nền bản đồ Goong. Hãy kiểm tra Map Key hoặc kết nối mạng.'));
    marker.on('dragend', () => {
      const point = marker.getLngLat();
      void resolveCoordinates(point.lat, point.lng);
    });

    map.current = instance;
    selectedMarker.current = marker;
    const resize = new ResizeObserver(() => instance.resize());
    resize.observe(mapContainer.current);

    return () => {
      mapLoadedRef.current = false;
      zoneFittedRef.current = false;
      resize.disconnect();
      marker.remove();
      instance.remove();
      selectedMarker.current = null;
      map.current = null;
    };
  }, [mapKey, resolveCoordinates, syncZoneOverlay]);

  useEffect(() => {
    if (!searchEnabled || query.trim().length < 3) return;
    const controller = new AbortController();
    const timer = window.setTimeout(async () => {
      setSearching(true);
      setError(null);
      try {
        const { data: body } = await api.get<{ predictions?: Prediction[]; error?: string }>('/api/maps/search', {
          baseURL: '',
          params: { input: query.trim(), location: `${lat},${lng}`, sessiontoken: sessionToken.current },
          signal: controller.signal,
        });
        setPredictions(body.predictions ?? []);
      } catch (cause) {
        if (!controller.signal.aborted && !axios.isCancel(cause)) {
          setError(cause instanceof Error ? cause.message : t("Không thể tìm địa chỉ."));
        }
      } finally {
        if (!controller.signal.aborted) setSearching(false);
      }
    }, 350);
    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [lat, lng, query, searchEnabled, t]);

  async function choosePrediction(prediction: Prediction) {
    setSearching(true);
    setPredictions([]);
    setSearchEnabled(false);
    setError(null);
    try {
      const { data: body } = await api.get<{ address?: string; lat?: number; lng?: number; error?: string }>(
        '/api/maps/place-detail',
        {
          baseURL: '',
          params: { placeId: prediction.place_id, sessiontoken: sessionToken.current },
        },
      );
      if (body.lat == null || body.lng == null) throw new Error(body.error || t("Không lấy được vị trí đã chọn."));
      const resolvedAddress = body.address || prediction.description;
      setQuery(resolvedAddress);
      onChangeRef.current(body.lat, body.lng, resolvedAddress);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : t("Không lấy được vị trí đã chọn."));
    } finally {
      setSearching(false);
    }
  }

  function useCurrentLocation() {
    if (!navigator.geolocation) return setError(t("Trình duyệt không hỗ trợ định vị."));
    setResolving(true);
    setError(null);
    navigator.geolocation.getCurrentPosition(
      ({ coords }) => void resolveCoordinates(coords.latitude, coords.longitude),
      () => {
        setResolving(false);
        setError(t("Không lấy được vị trí hiện tại. Hãy kiểm tra quyền định vị."));
      },
      { enableHighAccuracy: true, timeout: 10000 },
    );
  }

  return <div className="relative bg-slate-100">
    <div className="absolute inset-x-3 top-3 z-10 flex gap-2">
      <div className="relative min-w-0 flex-1">
        <Search aria-hidden="true" className="pointer-events-none absolute left-3 top-3 h-4 w-4 text-slate-500" />
        <input value={query} onChange={(event) => { const value = event.target.value; setQuery(value); setPredictions([]); setSearchEnabled(value.trim().length >= 3); }} onFocus={() => { if (query.trim().length >= 3) setSearchEnabled(true); }} placeholder={t("Tìm số nhà, tên đường hoặc địa điểm")} aria-label={t("Tìm địa chỉ trên bản đồ")} className="h-10 w-full rounded-lg border border-slate-300 bg-white pl-9 pr-10 text-sm text-slate-900 shadow-md outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100" />
        {(searching || resolving) && <Loader2 aria-label={t("Đang xác định địa chỉ")} className="absolute right-3 top-3 h-4 w-4 animate-spin text-blue-600" />}
        {predictions.length > 0 && <ul className="absolute mt-2 max-h-56 w-full overflow-y-auto rounded-lg border border-slate-200 bg-white py-1 shadow-lg">
          {predictions.map((prediction) => <li key={prediction.place_id}><button type="button" onClick={() => void choosePrediction(prediction)} className="flex w-full gap-3 px-3 py-2.5 text-left hover:bg-slate-50 focus:bg-slate-50 focus:outline-none"><MapPin className="mt-0.5 h-4 w-4 shrink-0 text-blue-600"/><span className="min-w-0"><span className="block truncate text-sm font-medium text-slate-900">{prediction.structured_formatting?.main_text || prediction.description}</span>{prediction.structured_formatting?.secondary_text && <span className="block truncate text-xs text-slate-600">{prediction.structured_formatting.secondary_text}</span>}</span></button></li>)}
        </ul>}
      </div>
      <button type="button" onClick={useCurrentLocation} title={t("Dùng vị trí hiện tại")} aria-label={t("Dùng vị trí hiện tại")} className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg border border-slate-300 bg-white text-slate-700 shadow-md hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-blue-200"><LocateFixed className="h-4 w-4"/></button>
    </div>
    {mapKey ? <div ref={mapContainer} className="h-80 w-full" aria-label={t("Bản đồ chọn điểm lấy hàng")} /> : <div className="flex h-80 items-center justify-center px-6 text-center text-sm text-rose-700">Chưa cấu hình NEXT_PUBLIC_GOONG_MAP_KEY cho Web Admin.</div>}
    <div className="flex min-h-10 items-center justify-between gap-3 border-t border-slate-200 bg-white px-3 py-2 text-xs">
      <span className={error || mapError ? 'text-rose-700' : 'text-slate-600'}>{error || mapError || (resolving ? t("Đang lấy địa chỉ từ vị trí đã chọn…") : t("Nhấp hoặc kéo ghim; địa chỉ và tọa độ sẽ tự cập nhật."))}</span>
      <span className="shrink-0 tabular-nums text-slate-500">{lat.toFixed(6)}, {lng.toFixed(6)}</span>
    </div>
  </div>;
}
