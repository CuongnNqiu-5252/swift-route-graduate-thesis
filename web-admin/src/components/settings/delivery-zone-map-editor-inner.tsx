'use client';

import { useLocale } from '@/i18n/provider';

import { useCallback, useEffect, useId, useRef, useState } from 'react';
import axios from 'axios';
import {
  AttributionControl,
  type GeoJSONSource,
  Map as MapLibreMap,
  Marker as MapLibreMarker,
  NavigationControl,
  setWorkerUrl,
} from 'maplibre-gl';
import { Crosshair, Loader2, LocateFixed, MapPin, Maximize2, Search, Target } from 'lucide-react';
import 'maplibre-gl/dist/maplibre-gl.css';
import {
  DeliveryZoneCheckResult,
  DeliveryZoneEnforcementMode,
} from '@/domain/entities/delivery-zone.entity';
import { goongMapsClient, type MapPlacePrediction } from '@/infrastructure/maps/goong-maps.client';
import {
  createCircleFeatureCollection,
  createRadiusLineCollection,
  destinationPoint,
  getCircleBounds,
  haversineKm,
} from './delivery-zone-geometry';

if (typeof window !== 'undefined') {
  setWorkerUrl('/maplibre-gl-worker.mjs');
}

export type MapInteractionMode = 'edit' | 'test';

export interface DeliveryZoneMapEditorProps {
  centerLat: number;
  centerLng: number;
  radiusKm: number;
  isActive: boolean;
  enforcementMode: DeliveryZoneEnforcementMode;
  centerAddress: string;
  interactionMode: MapInteractionMode;
  onInteractionModeChange: (mode: MapInteractionMode) => void;
  onCenterChange: (lat: number, lng: number, resolvedAddress?: string) => void;
  onRadiusChange: (radiusKm: number) => void;
  onTestPoint: (lat: number, lng: number) => Promise<DeliveryZoneCheckResult | null>;
  isCheckingPoint: boolean;
  checkResult: DeliveryZoneCheckResult | null;
}

const CIRCLE_SOURCE_ID = 'delivery-zone-circle';
const CIRCLE_FILL_LAYER_ID = 'delivery-zone-circle-fill';
const CIRCLE_LINE_LAYER_ID = 'delivery-zone-circle-line';
const RADIUS_LINE_SOURCE_ID = 'delivery-zone-radius-line';
const RADIUS_LINE_LAYER_ID = 'delivery-zone-radius-line-layer';

function createCenterPinElement() {
  const wrap = document.createElement('div');
  wrap.setAttribute('aria-label', 'Tâm vùng giao hàng');
  wrap.style.width = '28px';
  wrap.style.height = '28px';
  wrap.style.borderRadius = '50%';
  wrap.style.background = '#17766c';
  wrap.style.border = '3px solid #ffffff';
  wrap.style.boxShadow = '0 4px 14px rgb(15 23 42 / 28%)';
  wrap.style.display = 'flex';
  wrap.style.alignItems = 'center';
  wrap.style.justifyContent = 'center';
  wrap.style.cursor = 'grab';

  const dot = document.createElement('div');
  dot.style.width = '8px';
  dot.style.height = '8px';
  dot.style.borderRadius = '50%';
  dot.style.background = '#ffffff';
  wrap.appendChild(dot);
  return wrap;
}

function createEdgeHandleElement() {
  const wrap = document.createElement('div');
  wrap.setAttribute('aria-label', 'Kéo để thay đổi bán kính vùng giao hàng');
  wrap.style.display = 'flex';
  wrap.style.alignItems = 'center';
  wrap.style.gap = '6px';
  wrap.style.cursor = 'ew-resize';

  const handle = document.createElement('div');
  handle.style.width = '18px';
  handle.style.height = '18px';
  handle.style.borderRadius = '50%';
  handle.style.background = '#ffffff';
  handle.style.border = '3px solid #17766c';
  handle.style.boxShadow = '0 2px 8px rgb(15 23 42 / 25%)';

  const badge = document.createElement('span');
  badge.dataset.role = 'radius-label';
  badge.style.background = '#0f172a';
  badge.style.color = '#ffffff';
  badge.style.fontSize = '11px';
  badge.style.fontWeight = '600';
  badge.style.padding = '3px 8px';
  badge.style.borderRadius = '9999px';
  badge.style.whiteSpace = 'nowrap';
  badge.style.fontVariantNumeric = 'tabular-nums';
  badge.style.boxShadow = '0 2px 8px rgb(15 23 42 / 20%)';

  wrap.appendChild(handle);
  wrap.appendChild(badge);
  return wrap;
}

function createTestPinElement(allowed: boolean | null) {
  const el = document.createElement('div');
  el.setAttribute('aria-label', 'Điểm kiểm tra phạm vi giao hàng');
  el.style.width = '18px';
  el.style.height = '18px';
  el.style.borderRadius = '50%';
  el.style.background = allowed === null ? '#2563eb' : allowed ? '#15803d' : '#dc2626';
  el.style.border = '3px solid #ffffff';
  el.style.boxShadow = '0 3px 10px rgb(15 23 42 / 32%)';
  return el;
}

export default function DeliveryZoneMapEditorInner({
  centerLat,
  centerLng,
  radiusKm,
  isActive,
  enforcementMode,
  centerAddress,
  interactionMode,
  onInteractionModeChange,
  onCenterChange,
  onRadiusChange,
  onTestPoint,
  isCheckingPoint,
  checkResult,
}: DeliveryZoneMapEditorProps) {
  const { t } = useLocale();
  const mapContainer = useRef<HTMLDivElement>(null);
  const mapRef = useRef<MapLibreMap | null>(null);
  const centerMarkerRef = useRef<MapLibreMarker | null>(null);
  const edgeMarkerRef = useRef<MapLibreMarker | null>(null);
  const testMarkerRef = useRef<MapLibreMarker | null>(null);
  const edgeBadgeRef = useRef<HTMLElement | null>(null);

  const isDraggingEdgeRef = useRef(false);
  const mapLoadedRef = useRef(false);
  const interactionModeRef = useRef(interactionMode);
  const coordsRef = useRef({ centerLat, centerLng, radiusKm });
  const callbacksRef = useRef({ onCenterChange, onRadiusChange, onTestPoint });

  const [searchDraft, setSearchDraft] = useState<string | null>(null);
  const [predictions, setPredictions] = useState<MapPlacePrediction[]>([]);
  const [searchOpen, setSearchOpen] = useState(false);
  const [searching, setSearching] = useState(false);
  const [resolvingAddress, setResolvingAddress] = useState(false);
  const [geoError, setGeoError] = useState<string | null>(null);
  const [mapError, setMapError] = useState<string | null>(null);
  const [testCoords, setTestCoords] = useState<{ lat: number; lng: number } | null>(null);

  const activeQuery = searchDraft ?? (interactionMode === 'test' ? '' : centerAddress);

  const generatedId = useId();
  const sessionToken = useRef(generatedId);
  const mapKey = process.env.NEXT_PUBLIC_GOONG_MAP_KEY;

  useEffect(() => {
    interactionModeRef.current = interactionMode;
  }, [interactionMode]);

  useEffect(() => {
    callbacksRef.current = { onCenterChange, onRadiusChange, onTestPoint };
  }, [onCenterChange, onRadiusChange, onTestPoint]);

  useEffect(() => {
    coordsRef.current = { centerLat, centerLng, radiusKm };
  }, [centerLat, centerLng, radiusKm]);

  const syncCircleLayers = useCallback(
    (lat: number, lng: number, radius: number, active: boolean, mode: DeliveryZoneEnforcementMode) => {
      const instance = mapRef.current;
      if (!instance || !mapLoadedRef.current) return;

      const circleSource = instance.getSource(CIRCLE_SOURCE_ID) as GeoJSONSource | undefined;
      circleSource?.setData(createCircleFeatureCollection(lat, lng, radius));

      const lineSource = instance.getSource(RADIUS_LINE_SOURCE_ID) as GeoJSONSource | undefined;
      lineSource?.setData(createRadiusLineCollection(lat, lng, radius));

      const primaryColor = !active ? '#64748b' : mode === 'STRICT_BLOCK' ? '#17766c' : '#d97706';
      if (instance.getLayer(CIRCLE_FILL_LAYER_ID)) {
        instance.setPaintProperty(CIRCLE_FILL_LAYER_ID, 'fill-color', primaryColor);
        instance.setPaintProperty(CIRCLE_FILL_LAYER_ID, 'fill-opacity', active ? 0.14 : 0.08);
      }
      if (instance.getLayer(CIRCLE_LINE_LAYER_ID)) {
        instance.setPaintProperty(CIRCLE_LINE_LAYER_ID, 'line-color', primaryColor);
      }
      if (instance.getLayer(RADIUS_LINE_LAYER_ID)) {
        instance.setPaintProperty(RADIUS_LINE_LAYER_ID, 'line-color', primaryColor);
      }
    },
    [],
  );

  const fitZoneToViewport = useCallback(() => {
    const instance = mapRef.current;
    if (!instance || !mapLoadedRef.current) return;
    const bounds = getCircleBounds(
      coordsRef.current.centerLat,
      coordsRef.current.centerLng,
      coordsRef.current.radiusKm,
    );
    instance.fitBounds(bounds, { padding: 56, maxZoom: 15, duration: 450 });
  }, []);

  const resolveAndUpdateCenter = useCallback(async (lat: number, lng: number) => {
    setPredictions([]);
    setSearchOpen(false);
    setSearchDraft(null);
    setResolvingAddress(true);
    setGeoError(null);
    callbacksRef.current.onCenterChange(lat, lng);
    try {
      const address = await goongMapsClient.reverseGeocode(lat, lng);
      if (address) {
        callbacksRef.current.onCenterChange(lat, lng, address);
      }
    } catch {
      setGeoError(t("Đã cập nhật tâm vùng giao hàng, nhưng chưa tra cứu được địa chỉ từ Goong."));
    } finally {
      setResolvingAddress(false);
    }
  }, [t]);

  const handleMapTestClick = useCallback(async (lat: number, lng: number) => {
    setTestCoords({ lat, lng });
    const instance = mapRef.current;
    if (instance) {
      if (testMarkerRef.current) {
        testMarkerRef.current.remove();
      }
      testMarkerRef.current = new MapLibreMarker({ element: createTestPinElement(null) })
        .setLngLat([lng, lat])
        .addTo(instance);
    }
    const result = await callbacksRef.current.onTestPoint(lat, lng);
    if (result && instance) {
      if (testMarkerRef.current) {
        testMarkerRef.current.remove();
      }
      testMarkerRef.current = new MapLibreMarker({ element: createTestPinElement(result.allowed) })
        .setLngLat([lng, lat])
        .addTo(instance);
    }
  }, []);

  useEffect(() => {
    if (!mapContainer.current || mapRef.current || !mapKey) return;

    const initialLat = coordsRef.current.centerLat;
    const initialLng = coordsRef.current.centerLng;
    const initialRadius = coordsRef.current.radiusKm;

    const instance = new MapLibreMap({
      container: mapContainer.current,
      style: `https://tiles.goong.io/assets/goong_map_web.json?api_key=${encodeURIComponent(mapKey)}`,
      center: [initialLng, initialLat],
      zoom: 12,
      attributionControl: false,
    });

    const centerMarker = new MapLibreMarker({
      element: createCenterPinElement(),
      draggable: true,
    })
      .setLngLat([initialLng, initialLat])
      .addTo(instance);

    const edgeEl = createEdgeHandleElement();
    edgeBadgeRef.current = edgeEl.querySelector('[data-role="radius-label"]') as HTMLElement | null;
    if (edgeBadgeRef.current) {
      edgeBadgeRef.current.textContent = `Bán kính: ${initialRadius.toFixed(1)} km`;
    }

    const edgeMarker = new MapLibreMarker({
      element: edgeEl,
      draggable: true,
      anchor: 'left',
    })
      .setLngLat(destinationPoint(initialLat, initialLng, initialRadius, 90))
      .addTo(instance);

    instance.addControl(new NavigationControl({ showCompass: false }), 'bottom-left');
    instance.addControl(new AttributionControl({ compact: true, customAttribution: '© Goong' }), 'bottom-right');

    instance.on('load', () => {
      mapLoadedRef.current = true;
      setMapError(null);

      instance.addSource(CIRCLE_SOURCE_ID, {
        type: 'geojson',
        data: createCircleFeatureCollection(
          coordsRef.current.centerLat,
          coordsRef.current.centerLng,
          coordsRef.current.radiusKm,
        ),
      });
      instance.addLayer({
        id: CIRCLE_FILL_LAYER_ID,
        type: 'fill',
        source: CIRCLE_SOURCE_ID,
        paint: {
          'fill-color': '#17766c',
          'fill-opacity': 0.14,
        },
      });
      instance.addLayer({
        id: CIRCLE_LINE_LAYER_ID,
        type: 'line',
        source: CIRCLE_SOURCE_ID,
        paint: {
          'line-color': '#17766c',
          'line-width': 2,
        },
      });

      instance.addSource(RADIUS_LINE_SOURCE_ID, {
        type: 'geojson',
        data: createRadiusLineCollection(
          coordsRef.current.centerLat,
          coordsRef.current.centerLng,
          coordsRef.current.radiusKm,
        ),
      });
      instance.addLayer({
        id: RADIUS_LINE_LAYER_ID,
        type: 'line',
        source: RADIUS_LINE_SOURCE_ID,
        paint: {
          'line-color': '#17766c',
          'line-width': 1.5,
          'line-dasharray': [3, 3],
        },
      });

      syncCircleLayers(
        coordsRef.current.centerLat,
        coordsRef.current.centerLng,
        coordsRef.current.radiusKm,
        isActive,
        enforcementMode,
      );
      const bounds = getCircleBounds(
        coordsRef.current.centerLat,
        coordsRef.current.centerLng,
        coordsRef.current.radiusKm,
      );
      instance.fitBounds(bounds, { padding: 56, maxZoom: 15, duration: 0 });
    });

    instance.on('error', () => {
      setMapError(t("Không tải được nền bản đồ Goong. Hãy kiểm tra NEXT_PUBLIC_GOONG_MAP_KEY."));
    });

    instance.on('click', ({ lngLat }) => {
      if (interactionModeRef.current === 'test') {
        void handleMapTestClick(lngLat.lat, lngLat.lng);
      } else {
        void resolveAndUpdateCenter(lngLat.lat, lngLat.lng);
      }
    });

    centerMarker.on('dragend', () => {
      const point = centerMarker.getLngLat();
      void resolveAndUpdateCenter(point.lat, point.lng);
    });

    edgeMarker.on('dragstart', () => {
      isDraggingEdgeRef.current = true;
    });

    edgeMarker.on('drag', () => {
      const point = edgeMarker.getLngLat();
      const rawKm = haversineKm(
        coordsRef.current.centerLat,
        coordsRef.current.centerLng,
        point.lat,
        point.lng,
      );
      const clampedKm = Math.min(50, Math.max(0.5, Math.round(rawKm * 10) / 10));
      if (edgeBadgeRef.current) {
        edgeBadgeRef.current.textContent = `Bán kính: ${clampedKm.toFixed(1)} km`;
      }
      syncCircleLayers(
        coordsRef.current.centerLat,
        coordsRef.current.centerLng,
        clampedKm,
        true,
        'STRICT_BLOCK',
      );
      callbacksRef.current.onRadiusChange(clampedKm);
    });

    edgeMarker.on('dragend', () => {
      const point = edgeMarker.getLngLat();
      const rawKm = haversineKm(
        coordsRef.current.centerLat,
        coordsRef.current.centerLng,
        point.lat,
        point.lng,
      );
      const clampedKm = Math.min(50, Math.max(0.5, Math.round(rawKm * 10) / 10));
      isDraggingEdgeRef.current = false;
      edgeMarker.setLngLat(
        destinationPoint(coordsRef.current.centerLat, coordsRef.current.centerLng, clampedKm, 90),
      );
      callbacksRef.current.onRadiusChange(clampedKm);
    });

    mapRef.current = instance;
    centerMarkerRef.current = centerMarker;
    edgeMarkerRef.current = edgeMarker;

    const resize = new ResizeObserver(() => instance.resize());
    resize.observe(mapContainer.current);

    return () => {
      resize.disconnect();
      mapLoadedRef.current = false;
      testMarkerRef.current?.remove();
      edgeMarker.remove();
      centerMarker.remove();
      instance.remove();
      testMarkerRef.current = null;
      edgeMarkerRef.current = null;
      centerMarkerRef.current = null;
      mapRef.current = null;
    };
    // Initial map lifecycle only; dynamic props synchronize via effect below.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mapKey, handleMapTestClick, resolveAndUpdateCenter, syncCircleLayers]);

  useEffect(() => {
    centerMarkerRef.current?.setLngLat([centerLng, centerLat]);
    if (!isDraggingEdgeRef.current) {
      edgeMarkerRef.current?.setLngLat(destinationPoint(centerLat, centerLng, radiusKm, 90));
    }
    if (edgeBadgeRef.current) {
      edgeBadgeRef.current.textContent = `Bán kính: ${radiusKm.toFixed(1)} km`;
    }
    syncCircleLayers(centerLat, centerLng, radiusKm, isActive, enforcementMode);
  }, [centerLat, centerLng, radiusKm, isActive, enforcementMode, syncCircleLayers]);

  useEffect(() => {
    if (interactionMode === 'edit' && testMarkerRef.current) {
      testMarkerRef.current.remove();
      testMarkerRef.current = null;
    }
  }, [interactionMode]);

  useEffect(() => {
    if (!searchOpen || activeQuery.trim().length < 3) return;
    const controller = new AbortController();
    const timer = window.setTimeout(async () => {
      setSearching(true);
      setGeoError(null);
      try {
        const items = await goongMapsClient.searchPlaces(
          activeQuery,
          { lat: coordsRef.current.centerLat, lng: coordsRef.current.centerLng },
          sessionToken.current,
          controller.signal,
        );
        setPredictions(items);
      } catch (err) {
        if (!axios.isCancel(err)) {
          setGeoError(t("Không thể tìm kiếm địa điểm qua Goong."));
        }
      } finally {
        if (!controller.signal.aborted) {
          setSearching(false);
        }
      }
    }, 350);
    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [activeQuery, searchOpen, t]);

  async function choosePrediction(prediction: MapPlacePrediction) {
    setSearching(true);
    setPredictions([]);
    setSearchOpen(false);
    setGeoError(null);
    try {
      const detail = await goongMapsClient.getPlaceDetail(prediction.place_id, sessionToken.current);
      if (!detail) {
        setGeoError(t("Không lấy được vị trí cho địa điểm đã chọn."));
        return;
      }
      if (interactionMode === 'test') {
        setSearchDraft(detail.address || prediction.description);
        await handleMapTestClick(detail.lat, detail.lng);
        mapRef.current?.easeTo({ center: [detail.lng, detail.lat], zoom: 13, duration: 400 });
        return;
      }
      const nextAddress = detail.address || prediction.description;
      setSearchDraft(null);
      onCenterChange(detail.lat, detail.lng, nextAddress);
      const bounds = getCircleBounds(detail.lat, detail.lng, radiusKm);
      mapRef.current?.fitBounds(bounds, { padding: 56, maxZoom: 15, duration: 450 });
    } catch {
      setGeoError(t("Không lấy được thông tin chi tiết địa điểm."));
    } finally {
      setSearching(false);
    }
  }

  function locateMe() {
    if (!navigator.geolocation) {
      setGeoError(t("Trình duyệt không hỗ trợ định vị GPS."));
      return;
    }
    setResolvingAddress(true);
    setGeoError(null);
    navigator.geolocation.getCurrentPosition(
      ({ coords }) => {
        void resolveAndUpdateCenter(coords.latitude, coords.longitude);
        const bounds = getCircleBounds(coords.latitude, coords.longitude, radiusKm);
        mapRef.current?.fitBounds(bounds, { padding: 56, maxZoom: 15, duration: 450 });
      },
      () => {
        setResolvingAddress(false);
        setGeoError(t("Không đọc được vị trí hiện tại từ trình duyệt."));
      },
      { enableHighAccuracy: true, timeout: 10000 },
    );
  }

  return (
    <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-200 bg-slate-50/70 px-3 py-2.5">
        <div className="inline-flex rounded-md border border-slate-200 bg-white p-0.5">
          <button
            type="button"
            onClick={() => {
              setTestCoords(null);
              setSearchDraft(null);
              onInteractionModeChange('edit');
            }}
            className={`inline-flex items-center gap-1.5 rounded px-2.5 py-1.5 text-xs font-medium transition-colors ${
              interactionMode === 'edit'
                ? 'bg-slate-900 text-white'
                : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            <Target className="h-3.5 w-3.5" />
            {t("Định tâm & bán kính")}
          </button>
          <button
            type="button"
            onClick={() => {
              setTestCoords(null);
              setSearchDraft(null);
              onInteractionModeChange('test');
            }}
            className={`inline-flex items-center gap-1.5 rounded px-2.5 py-1.5 text-xs font-medium transition-colors ${
              interactionMode === 'test'
                ? 'bg-slate-900 text-white'
                : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            <Crosshair className="h-3.5 w-3.5" />
            {t("Kiểm tra điểm giao")}
          </button>
        </div>

        <button
          type="button"
          onClick={fitZoneToViewport}
          className="inline-flex items-center gap-1.5 rounded-md border border-slate-200 bg-white px-2.5 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50"
        >
          <Maximize2 className="h-3.5 w-3.5" />
          {t("Canh vừa vùng")}
        </button>
      </div>

      <div className="relative bg-slate-100">
        <div className="absolute inset-x-3 top-3 z-10 flex gap-2">
          <div className="relative min-w-0 flex-1">
            <Search
              aria-hidden="true"
              className="pointer-events-none absolute left-3 top-3 h-4 w-4 text-slate-500"
            />
            <input
              value={activeQuery}
              onChange={(event) => {
                const next = event.target.value;
                setSearchDraft(next);
                setPredictions([]);
                setSearchOpen(next.trim().length >= 3);
              }}
              onFocus={() => {
                if (activeQuery.trim().length >= 3) setSearchOpen(true);
              }}
              placeholder={
                interactionMode === 'test'
                  ? t("Tìm địa chỉ giao hàng cần kiểm tra trên bản đồ")
                  : t("Tìm địa chỉ làm tâm vùng giao hàng")
              }
              aria-label={t("Tìm địa chỉ trên bản đồ")}
              className="h-10 w-full rounded-lg border border-slate-300 bg-white pl-9 pr-10 text-sm text-slate-900 outline-none focus:border-slate-500"
            />
            {(searching || resolvingAddress || isCheckingPoint) && (
              <Loader2 className="absolute right-3 top-3 h-4 w-4 animate-spin text-slate-600" />
            )}
            {predictions.length > 0 && (
              <ul className="absolute mt-1.5 max-h-56 w-full overflow-y-auto rounded-lg border border-slate-200 bg-white py-1">
                {predictions.map((prediction) => (
                  <li key={prediction.place_id}>
                    <button
                      type="button"
                      onClick={() => void choosePrediction(prediction)}
                      className="flex w-full items-start gap-2.5 px-3 py-2 text-left hover:bg-slate-50"
                    >
                      <MapPin className="mt-0.5 h-4 w-4 shrink-0 text-[#17766c]" />
                      <span className="min-w-0">
                        <span className="block truncate text-sm font-medium text-slate-900">
                          {prediction.structured_formatting?.main_text || prediction.description}
                        </span>
                        {prediction.structured_formatting?.secondary_text && (
                          <span className="block truncate text-xs text-slate-500">
                            {prediction.structured_formatting.secondary_text}
                          </span>
                        )}
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>

          {interactionMode === 'edit' && (
            <button
              type="button"
              onClick={locateMe}
              title={t("Lấy vị trí hiện tại")}
              aria-label={t("Lấy vị trí hiện tại")}
              className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg border border-slate-300 bg-white text-slate-700 hover:bg-slate-50"
            >
              <LocateFixed className="h-4 w-4" />
            </button>
          )}
        </div>

        {mapKey ? (
          <div
            ref={mapContainer}
            className={`h-[380px] w-full ${interactionMode === 'test' ? 'cursor-crosshair' : ''}`}
            aria-label={t("Bản đồ vùng giao hàng")}
          />
        ) : (
          <div className="flex h-[380px] items-center justify-center px-6 text-center text-sm text-rose-700">
            Chưa cấu hình NEXT_PUBLIC_GOONG_MAP_KEY cho Web Admin.
          </div>
        )}

        <div className="flex flex-wrap items-center justify-between gap-2 border-t border-slate-200 bg-white px-3 py-2 text-xs">
          <span className={geoError || mapError ? 'text-rose-700' : 'text-slate-600'}>
            {geoError ||
              mapError ||
              (interactionMode === 'test'
                ? t("Nhấp lên một vị trí bất kỳ trên bản đồ để kiểm tra đơn có được phép giao tới hay không.")
                : t("Kéo ghim xanh để đổi tâm vùng hoặc kéo điểm trắng trên viền tròn để thay đổi bán kính."))}
          </span>
          {testCoords && checkResult && interactionMode === 'test' && (
            <span
              className={`num rounded px-2 py-0.5 font-medium ${
                checkResult.allowed
                  ? checkResult.in_zone
                    ? 'bg-emerald-50 text-emerald-700'
                    : 'bg-amber-50 text-amber-800'
                  : 'bg-rose-50 text-rose-700'
              }`}
            >
              {t("Cách tâm")}{" "}{checkResult.distance_km.toFixed(2)} km
            </span>
          )}
        </div>
      </div>
    </div>
  );
}
