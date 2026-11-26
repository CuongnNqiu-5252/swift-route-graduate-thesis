'use client';

import { CloudRain, Globe, Layers, Map as MapIcon, Navigation, Satellite, Sun, TrafficCone } from 'lucide-react';
import { Marker, Popup, type Map as MapLibreMap, type RasterTileSource } from 'maplibre-gl';
import { useEffect, useMemo, useRef, useState } from 'react';
import { forecastAt, forecastPoints, travelSecondsToPoint } from '@/application/dispatch-weather';
import { useLocale } from '@/i18n/provider';
import { dispatchLayersClient, type RainForecast } from '@/infrastructure/maps/dispatch-layers.client';
import type { LocationPoint } from '@/domain/entities/routing.entity';
import { BASEMAP_OPTIONS, type MapBasemapId } from './route-map-styles';

export const TRAFFIC_LAYER_ID = 'dispatch-traffic';

const forecastRefreshMs = 10 * 60_000;
const minimumRainProbability = 40;

function weatherMarker(probability: number, label: string) {
  const button = document.createElement('button');
  button.type = 'button';
  const highlighted = probability >= minimumRainProbability;
  button.className = [
    'flex h-8 min-w-11 items-center justify-center gap-1 rounded-md border px-1.5 text-xs font-semibold tabular-nums shadow-sm focus-visible:outline-2 focus-visible:outline-blue-700',
    highlighted ? 'border-blue-800 bg-blue-700 text-white hover:bg-blue-800' : 'border-slate-300 bg-white text-slate-800 hover:bg-slate-100',
  ].join(' ');
  button.setAttribute('aria-label', label);
  const icon = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  icon.setAttribute('viewBox', '0 0 24 24');
  icon.setAttribute('width', '14');
  icon.setAttribute('height', '14');
  icon.setAttribute('fill', 'none');
  icon.setAttribute('stroke', 'currentColor');
  icon.setAttribute('stroke-width', '2');
  icon.setAttribute('aria-hidden', 'true');
  const shape = document.createElementNS(icon.namespaceURI, 'path');
  shape.setAttribute('d', 'M20 16a4 4 0 0 0-2-7h-2A6 6 0 1 0 4 15m4 1v4m4-6v4m4-2v4');
  icon.append(shape);
  button.append(icon, document.createTextNode(`${probability}%`));
  return button;
}

const basemapIcons: Record<MapBasemapId, typeof MapIcon> = {
  streets: MapIcon,
  light: Sun,
  satellite: Satellite,
  osm: Globe,
};

export function RouteMapLayers({
  map,
  styleVersion,
  basemap,
  onBasemapChange,
  showRouteDirection,
  onShowRouteDirectionChange,
  stops,
  plannedLegs,
  firstStopIndex,
  showStopMarkers,
}: {
  map: MapLibreMap;
  styleVersion: number;
  basemap: MapBasemapId;
  onBasemapChange: (basemap: MapBasemapId) => void;
  showRouteDirection: boolean;
  onShowRouteDirectionChange: (show: boolean) => void;
  stops: LocationPoint[];
  plannedLegs: readonly (number | null)[];
  firstStopIndex: number;
  showStopMarkers: boolean;
}) {
  const { t, intlLocale } = useLocale();
  const [open, setOpen] = useState(false);
  const trigger = useRef<HTMLButtonElement>(null);
  const panel = useRef<HTMLFieldSetElement>(null);
  const [weather, setWeather] = useState(false);
  const [traffic, setTraffic] = useState(false);
  const [forecast, setForecast] = useState<{ points: string; data?: RainForecast[][]; now: number }>();
  const [trafficStatus, setTrafficStatus] = useState<'idle' | 'loading' | 'ready' | 'unconfigured' | 'unavailable'>('idle');

  useEffect(() => {
    if (!open) return;
    const dismiss = (event: PointerEvent) => {
      const target = event.target;
      if (target instanceof Node && !panel.current?.contains(target) && !trigger.current?.contains(target)) {
        setOpen(false);
      }
    };
    document.addEventListener('pointerdown', dismiss);
    return () => document.removeEventListener('pointerdown', dismiss);
  }, [open]);

  const samples = useMemo(() => forecastPoints(stops), [stops]);
  const pointKey = samples.map((sample) => sample.coordinate).join(';');
  const timeFormat = useMemo(() => new Intl.DateTimeFormat(intlLocale, {
    hour: '2-digit', minute: '2-digit', timeZone: 'Asia/Ho_Chi_Minh',
  }), [intlLocale]);

  useEffect(() => {
    if (!weather || !pointKey) return;
    const controller = new AbortController();
    async function refresh() {
      try {
        const data = await dispatchLayersClient.getWeather(pointKey, controller.signal);
        if (!controller.signal.aborted) setForecast({ points: pointKey, data, now: Date.now() / 1000 });
      } catch {
        if (!controller.signal.aborted) setForecast({ points: pointKey, now: Date.now() / 1000 });
      }
    }
    void refresh();
    const refreshTimer = window.setInterval(refresh, forecastRefreshMs);
    return () => {
      controller.abort();
      window.clearInterval(refreshTimer);
    };
  }, [map, weather, pointKey]);

  const visibleForecast = weather && forecast?.points === pointKey ? forecast : undefined;
  const rain = useMemo(() => {
    if (!visibleForecast?.data) return [];
    return samples.flatMap((sample, index) => {
      const travel = travelSecondsToPoint(plannedLegs, firstStopIndex + sample.index);
      const arrival = visibleForecast.now + (travel ?? 0);
      const series = visibleForecast.data?.[index] ?? [];
      const upcoming = travel !== null ? forecastAt(series, arrival) : undefined;
      const item = upcoming ?? forecastAt(series, visibleForecast.now);
      return item ? [{ ...sample, ...item, arrival: upcoming ? arrival : null }] : [];
    });
  }, [visibleForecast, samples, plannedLegs, firstStopIndex]);
  useEffect(() => {
    if (!weather || !showStopMarkers) return;
    const markers = rain.map((point) => {
      const hour = timeFormat.format((point.arrival ?? point.timestamp) * 1000);
      const title = `${t('Điểm giao')} ${point.index + 1} · ${point.probability}% ${t('khả năng mưa')}`;
      const detail = document.createElement('div');
      const heading = document.createElement('strong');
      heading.textContent = title;
      const note = document.createElement('p');
      note.textContent = point.arrival === null
        ? `${t('Giờ dự báo')}: ${hour}`
        : `${t('Nếu xuất phát bây giờ, dự kiến qua điểm lúc')}: ${hour}`;
      detail.append(heading, note);
      const element = weatherMarker(point.probability, title);
      return new Marker({ element, anchor: 'bottom', offset: [22, -21] })
        .setLngLat([point.lng, point.lat])
        .setPopup(new Popup({ offset: 48 }).setDOMContent(detail))
        .addTo(map);
    });
    return () => markers.forEach((marker) => marker.remove());
  }, [map, weather, rain, showStopMarkers, t, timeFormat]);

  useEffect(() => {
    if (!traffic) return;
    const controller = new AbortController();
    let refreshTimer: number | undefined;
    const unsuccessful = (event: object) => {
      if ('sourceId' in event && event.sourceId === TRAFFIC_LAYER_ID) setTrafficStatus('unavailable');
    };
    async function start() {
      try {
        const available = await dispatchLayersClient.hasTraffic(controller.signal);
        if (controller.signal.aborted || !map.getStyle()) return;
        if (!available) {
          setTrafficStatus('unconfigured');
          return;
        }
        if (map.getLayer(TRAFFIC_LAYER_ID)) map.removeLayer(TRAFFIC_LAYER_ID);
        if (map.getSource(TRAFFIC_LAYER_ID)) map.removeSource(TRAFFIC_LAYER_ID);
        map.addSource(TRAFFIC_LAYER_ID, {
          type: 'raster',
          tiles: [dispatchLayersClient.trafficTiles()],
          tileSize: 256,
          minzoom: 8,
          maxzoom: 19,
          attribution: '© TomTom Traffic',
        });
        const firstRouteGlowLayer = map.getStyle()?.layers?.find((layer) => layer.id.startsWith('overview-glow-'))?.id;
        const beforeId = firstRouteGlowLayer ?? (map.getLayer('dispatch-route-line') ? 'dispatch-route-line' : undefined);
        map.addLayer({
          id: TRAFFIC_LAYER_ID,
          type: 'raster',
          source: TRAFFIC_LAYER_ID,
          minzoom: 8,
          paint: { 'raster-opacity': 0.85, 'raster-fade-duration': 0 },
        }, beforeId);
        setTrafficStatus('ready');
        refreshTimer = window.setInterval(() => {
          if (!map.getStyle()) return;
          const source = map.getSource<RasterTileSource>(TRAFFIC_LAYER_ID);
          if (!source) return;
          setTrafficStatus('ready');
          source.setTiles([dispatchLayersClient.trafficTiles()]);
        }, 120_000);
      } catch {
        if (!controller.signal.aborted) setTrafficStatus('unavailable');
      }
    }
    map.on('error', unsuccessful);
    void start();
    return () => {
      controller.abort();
      window.clearInterval(refreshTimer);
      map.off('error', unsuccessful);
      if (!map.getStyle()) return;
      if (map.getLayer(TRAFFIC_LAYER_ID)) map.removeLayer(TRAFFIC_LAYER_ID);
      if (map.getSource(TRAFFIC_LAYER_ID)) map.removeSource(TRAFFIC_LAYER_ID);
    };
  }, [map, traffic, styleVersion]);

  const weatherStatus = !showStopMarkers ? t('Điểm giao đang ẩn.')
    : !pointKey ? t('Chọn tuyến có điểm giao để xem dự báo.')
    : !visibleForecast ? t('Đang tải dự báo…')
    : !visibleForecast.data || !rain.length ? t('Chưa tải được dự báo cho tuyến này.')
    : t('Nhãn % trên bản đồ là khả năng mưa. Màu xanh đánh dấu mức từ 40%.');

  const activeCount = Number(weather) + Number(traffic) + Number(showRouteDirection) + Number(basemap !== 'streets');
  return (
    <div className="pointer-events-auto relative w-fit shrink-0" onKeyDown={(event) => {
      if (open && event.key === 'Escape') {
        event.preventDefault();
        setOpen(false);
        trigger.current?.focus();
      }
    }}>
      <button ref={trigger} type="button" aria-expanded={open} aria-controls={open ? 'dispatch-map-layers' : undefined}
        onClick={() => setOpen((value) => !value)}
        className="inline-flex min-h-11 cursor-pointer items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 text-sm font-semibold text-slate-900 transition-colors hover:bg-slate-50 focus-visible:outline-2 focus-visible:outline-blue-700">
        <Layers size={16} aria-hidden="true" />
        {t('Lớp bản đồ')}{activeCount > 0 && <span className="tabular-nums text-slate-600">({activeCount})</span>}
      </button>
      {open && (
        <fieldset ref={panel} id="dispatch-map-layers" className="absolute bottom-full right-0 mb-1 max-h-[min(28rem,65dvh)] w-72 max-w-[calc(100vw-2rem)] overflow-y-auto rounded-xl border border-slate-200 bg-white p-3 text-slate-900 shadow-lg">
          <legend className="sr-only">{t('Lớp bản đồ')}</legend>

          <div>
            <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-600">
              {t('Nền bản đồ')}
            </p>
            <div className="grid grid-cols-2 gap-1.5" role="radiogroup" aria-label={t('Nền bản đồ')}>
              {BASEMAP_OPTIONS.map((option) => {
                const Icon = basemapIcons[option.id];
                const selected = basemap === option.id;
                return (
                  <label
                    key={option.id}
                    className={`flex min-h-10 cursor-pointer items-center gap-2 rounded-lg border px-2.5 py-1.5 text-xs font-medium transition-colors ${
                      selected
                        ? 'border-blue-600 bg-blue-50 text-blue-900'
                        : 'border-slate-200 bg-white text-slate-700 hover:bg-slate-50'
                    }`}
                  >
                    <input
                      type="radio"
                      name="dispatch-map-basemap"
                      value={option.id}
                      checked={selected}
                      onChange={() => onBasemapChange(option.id)}
                      className="sr-only"
                    />
                    <Icon size={14} className={selected ? 'shrink-0 text-blue-700' : 'shrink-0 text-slate-500'} aria-hidden="true" />
                    <span className="min-w-0 truncate">{t(option.label)}</span>
                  </label>
                );
              })}
            </div>
          </div>

          <div className="mt-3 border-t border-slate-200 pt-2">
            <p className="mb-1 text-xs font-semibold uppercase tracking-wide text-slate-600">
              {t('Lớp hiển thị')}
            </p>
            <label className="flex min-h-11 cursor-pointer items-center gap-2 text-sm font-medium">
              <Navigation size={16} className="text-emerald-700" aria-hidden="true" />
              <span className="flex-1">{t('Hướng tuyến')}</span>
              <input
                type="checkbox"
                checked={showRouteDirection}
                onChange={(event) => onShowRouteDirectionChange(event.target.checked)}
                className="size-4 cursor-pointer accent-blue-700"
              />
            </label>
            <label className="flex min-h-11 cursor-pointer items-center gap-2 border-t border-slate-200 text-sm font-medium">
              <CloudRain size={16} className="text-blue-700" aria-hidden="true" />
              <span className="flex-1">{t('Thời tiết')}</span>
              <input type="checkbox" checked={weather} onChange={(event) => {
                setForecast(undefined);
                setWeather(event.target.checked);
              }} className="size-4 cursor-pointer accent-blue-700" />
            </label>
            {weather && <p role="status" className="mb-2 text-xs leading-5 text-slate-600">{weatherStatus} · Open-Meteo</p>}
            <label className="flex min-h-11 cursor-pointer items-center gap-2 border-t border-slate-200 text-sm font-medium">
              <TrafficCone size={16} className="text-slate-600" aria-hidden="true" />
              <span className="flex-1">{t('Giao thông')}</span>
              <input type="checkbox" checked={traffic} onChange={(event) => {
                setTrafficStatus(event.target.checked ? 'loading' : 'idle');
                setTraffic(event.target.checked);
              }} className="size-4 cursor-pointer accent-blue-700" />
            </label>
            {traffic && (
              <div role="status" className="text-xs leading-5 text-slate-600">
                {trafficStatus === 'unconfigured' ? t('Nguồn giao thông chưa được cấu hình.')
                  : trafficStatus === 'unavailable' ? t('Chưa tải được dữ liệu giao thông.')
                  : trafficStatus === 'ready' ? (
                    <>
                      <div className="flex flex-wrap gap-x-2">
                        <span><i className="mr-1 inline-block h-0.5 w-3 bg-[#118845] align-middle" />{t('Thông thoáng')}</span>
                        <span><i className="mr-1 inline-block h-0.5 w-3 bg-[#db9200] align-middle" />{t('Chậm')}</span>
                        <span><i className="mr-1 inline-block h-0.5 w-3 bg-[#e22503] align-middle" />{t('Ùn tắc')}</span>
                      </div>
                      <p>{t('Chỉ tô màu đoạn có dữ liệu.')} · TomTom</p>
                    </>
                  ) : t('Đang tải giao thông…')}
              </div>
            )}
          </div>
        </fieldset>
      )}
    </div>
  );
}
