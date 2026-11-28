import booleanPointInPolygon from '@turf/boolean-point-in-polygon';
import type { FeatureCollection, Position } from 'geojson';
import type { GeoJSONSource, Map as MapLibreMap } from 'maplibre-gl';
import { useEffect, useRef, useState } from 'react';
import type { MapOverviewRoute } from '@/domain/entities/routing.entity';
import { useLocale } from '@/i18n/provider';
import { DispatchDragPreview } from './dispatch-drag-preview';

export type MapStopTarget = { planId: string; stopId?: string };
export type MapDraggedOrders = { orderIds: string[]; sourcePlanId: string };
export type MapStopDrop = MapDraggedOrders & { target: MapStopTarget };

export interface DispatchMapEditControls {
  drawing: boolean;
  selectedIds: ReadonlySet<string>;
  editableIds: ReadonlySet<string>;
  onSelection: (ids: ReadonlySet<string>) => void;
  onDrawingEnd: () => void;
  onDragOrders: (orders: MapDraggedOrders | null) => void;
  onDragTarget: (target: MapStopTarget | null) => void;
  getDropError: (drop: MapStopDrop) => string | null;
  onDrop: (drop: MapStopDrop) => void;
}

const SOURCE = 'dispatch-polygon-selection';
const FILL = `${SOURCE}-fill`;
const OUTLINE = `${SOURCE}-outline`;
const VERTICES = `${SOURCE}-vertices`;
type Coordinate = [number, number];

export function DispatchMapEditing({ map, styleVersion, routes, controls }: {
  map: MapLibreMap;
  styleVersion: number;
  routes: MapOverviewRoute[];
  controls: DispatchMapEditControls;
}) {
  const { t } = useLocale();
  const latest = useRef({ controls, routes, t });
  const [preview, setPreview] = useState<{ x: number; y: number; color: string; label: string } | null>(null);

  useEffect(() => { latest.current = { controls, routes, t }; }, [controls, routes, t]);

  useEffect(() => {
    if (!controls.drawing) return;
    const root = map.getContainer();
    const canvas = map.getCanvas();
    const restoreZoom = map.doubleClickZoom.isEnabled();
    const vertices: Coordinate[] = [];
    let press: { x: number; y: number } | null = null;
    map.doubleClickZoom.disable();
    canvas.style.cursor = 'crosshair';
    map.addSource(SOURCE, { type: 'geojson', data: { type: 'FeatureCollection', features: [] } });
    map.addLayer({ id: FILL, type: 'fill', source: SOURCE, paint: { 'fill-color': '#0f766e', 'fill-opacity': 0.2 } });
    map.addLayer({ id: OUTLINE, type: 'line', source: SOURCE, paint: { 'line-color': '#0f766e', 'line-width': 2.5 } });
    map.addLayer({ id: VERTICES, type: 'circle', source: SOURCE, paint: { 'circle-color': '#0f766e', 'circle-radius': 4.5, 'circle-stroke-color': '#fff', 'circle-stroke-width': 2 } });
    root.dataset.drawingDispatchPolygon = 'true';

    function redraw(next?: Coordinate) {
      const line = next ? [...vertices, next] : vertices;
      const data: FeatureCollection = { type: 'FeatureCollection', features: vertices.map((point) => ({
        type: 'Feature', properties: {}, geometry: { type: 'Point', coordinates: point },
      })) };
      if (line.length >= 3) {
        data.features.push({ type: 'Feature', properties: {}, geometry: { type: 'Polygon', coordinates: [[...line, line[0]]] } });
      } else if (line.length === 2) {
        data.features.push({ type: 'Feature', properties: {}, geometry: { type: 'LineString', coordinates: line } });
      }
      map.getSource<GeoJSONSource>(SOURCE)?.setData(data);
    }

    function position(event: MouseEvent): Coordinate {
      const { left, top } = canvas.getBoundingClientRect();
      return map.unproject([event.clientX - left, event.clientY - top]).toArray();
    }

    function onClick(event: MouseEvent) {
      if ((event.target as Element | null)?.closest('.maplibregl-control-container')) return;
      event.stopImmediatePropagation();
      if (event.detail === 0 || (press && Math.hypot(event.clientX - press.x, event.clientY - press.y) > 5)) return;
      const point = position(event);
      const first = vertices[0];
      const closesPolygon = vertices.length >= 3 && (event.detail > 1 || (first && map.project(first).dist(map.project(point)) <= 12));
      if (!closesPolygon) {
        const last = vertices.at(-1);
        if (!last || map.project(last).dist(map.project(point)) > 4) vertices.push(point);
        redraw();
        return;
      }
      const ring: Position[] = [...vertices, vertices[0]];
      const polygon = { type: 'Polygon' as const, coordinates: [ring] };
      const current = latest.current;
      const selected = current.routes.flatMap((route) => route.stops.filter((stop) =>
        current.controls.editableIds.has(stop.id)
        && booleanPointInPolygon([stop.location.lng, stop.location.lat], polygon)).map((stop) => stop.id));
      current.controls.onSelection(new Set(selected));
      current.controls.onDrawingEnd();
    }

    function onDown(event: PointerEvent) { press = { x: event.clientX, y: event.clientY }; }
    function onMove(event: MouseEvent) { redraw(position(event)); }
    function onKey(event: KeyboardEvent) {
      if (event.key !== 'Escape') return;
      event.preventDefault();
      latest.current.controls.onDrawingEnd();
    }
    root.addEventListener('click', onClick, true);
    root.addEventListener('pointerdown', onDown, true);
    root.addEventListener('mousemove', onMove);
    document.addEventListener('keydown', onKey, true);
    return () => {
      delete root.dataset.drawingDispatchPolygon;
      canvas.style.cursor = '';
      if (restoreZoom) map.doubleClickZoom.enable();
      root.removeEventListener('click', onClick, true);
      root.removeEventListener('pointerdown', onDown, true);
      root.removeEventListener('mousemove', onMove);
      document.removeEventListener('keydown', onKey, true);
      if (!map.getStyle()) return;
      for (const layer of [VERTICES, OUTLINE, FILL]) {
        if (map.getLayer(layer)) map.removeLayer(layer);
      }
      if (map.getSource(SOURCE)) map.removeSource(SOURCE);
    };
  }, [map, styleVersion, controls.drawing]);

  useEffect(() => {
    const root = map.getContainer();
    let drag: {
      pointerId: number; x: number; y: number; pin: HTMLElement; active: boolean;
      orderIds: string[]; sourcePlanId: string; color: string; label: string;
    } | null = null;
    let suppressClick = false;
    let restorePan = false;
    let lastTarget: MapStopTarget | null = null;

    function getTarget(x: number, y: number): MapStopTarget | null {
      if (!drag) return null;
      const hit = document.elementFromPoint(x, y);
      const row = hit?.closest<HTMLElement>('[data-dispatch-drop-plan]')?.dataset.dispatchDropPlan;
      if (row) return { planId: row };
      if (!hit || !root.contains(hit) || hit.closest('.maplibregl-control-container')) return null;
      const marker = hit.closest<HTMLElement>('[data-dispatch-map-stop]');
      if (marker?.dataset.dispatchMapPlan && !drag.orderIds.includes(marker.dataset.dispatchMapStop ?? '')) {
        return { planId: marker.dataset.dispatchMapPlan, stopId: marker.dataset.dispatchMapStop };
      }
      const layerIds = latest.current.routes.map((route) => `overview-hit-${route.id}`).filter((id) => map.getLayer(id));
      if (layerIds.length === 0) return null;
      const { left, top } = map.getCanvas().getBoundingClientRect();
      const feature = map.queryRenderedFeatures([x - left, y - top], { layers: layerIds })
        .find((item) => item.properties?.planId !== drag?.sourcePlanId);
      return feature?.properties?.planId ? { planId: feature.properties.planId } : null;
    }

    function setTarget(target: MapStopTarget | null) {
      if (target?.planId === lastTarget?.planId && target?.stopId === lastTarget?.stopId) return;
      lastTarget = target;
      latest.current.controls.onDragTarget(target);
    }

    function onDown(event: PointerEvent) {
      if (!event.isPrimary || event.button !== 0) return;
      suppressClick = false;
      const { controls, routes, t } = latest.current;
      if (controls.drawing) return;
      const pin = (event.target as Element | null)?.closest<HTMLElement>('[data-dispatch-map-stop]');
      const stopId = pin?.dataset.dispatchMapStop;
      if (!pin || !stopId || !controls.editableIds.has(stopId)) return;
      const route = routes.find((item) => item.id === pin.dataset.dispatchMapPlan);
      if (!route) return;
      const orderIds = controls.selectedIds.has(stopId) ? [...controls.selectedIds] : [stopId];
      event.stopPropagation();
      drag = { pointerId: event.pointerId, x: event.clientX, y: event.clientY, pin, active: false,
        orderIds, sourcePlanId: route.id, color: route.color, label: orderIds.length > 1
          ? `${orderIds.length} ${t('đơn hàng')}`
          : route.stops.find((stop) => stop.id === stopId)?.order?.order_code ?? t('Điểm giao hàng') };
    }

    function onMove(event: PointerEvent) {
      if (!drag || event.pointerId !== drag.pointerId) return;
      if (!drag.active) {
        if (Math.hypot(event.clientX - drag.x, event.clientY - drag.y) < 5) return;
        drag.active = true;
        restorePan = map.dragPan.isEnabled();
        map.dragPan.disable();
        map.stop();
        drag.pin.setPointerCapture(event.pointerId);
        drag.pin.dataset.dispatchDragging = 'true';
        latest.current.controls.onDragOrders({ orderIds: drag.orderIds, sourcePlanId: drag.sourcePlanId });
      }
      setPreview({ x: event.clientX, y: event.clientY, color: drag.color, label: drag.label });
      const target = getTarget(event.clientX, event.clientY);
      setTarget(target && !latest.current.controls.getDropError({ ...drag, target }) ? target : null);
    }

    function clearDrag() {
      if (!drag?.active) { drag = null; return; }
      delete drag.pin.dataset.dispatchDragging;
      if (restorePan) map.dragPan.enable();
      drag = null;
      suppressClick = true;
      setPreview(null);
      setTarget(null);
      latest.current.controls.onDragOrders(null);
    }

    function onUp(event: PointerEvent) {
      if (!drag || event.pointerId !== drag.pointerId) return;
      const active = drag;
      const target = drag.active ? getTarget(event.clientX, event.clientY) : null;
      clearDrag();
      if (target) latest.current.controls.onDrop({ orderIds: active.orderIds, sourcePlanId: active.sourcePlanId, target });
    }

    function onCancel(event: PointerEvent) { if (drag?.pointerId === event.pointerId) clearDrag(); }
    function onKey(event: KeyboardEvent) {
      if (event.key !== 'Escape' || !drag?.active) return;
      event.preventDefault();
      clearDrag();
    }
    function onClick(event: MouseEvent) {
      if (!suppressClick || event.detail === 0) return;
      suppressClick = false;
      event.stopImmediatePropagation();
    }

    root.addEventListener('pointerdown', onDown, true);
    root.addEventListener('click', onClick, true);
    document.addEventListener('pointermove', onMove, true);
    document.addEventListener('pointerup', onUp, true);
    document.addEventListener('pointercancel', onCancel, true);
    root.addEventListener('lostpointercapture', onCancel, true);
    document.addEventListener('keydown', onKey, true);
    return () => {
      clearDrag();
      root.removeEventListener('pointerdown', onDown, true);
      root.removeEventListener('click', onClick, true);
      document.removeEventListener('pointermove', onMove, true);
      document.removeEventListener('pointerup', onUp, true);
      document.removeEventListener('pointercancel', onCancel, true);
      root.removeEventListener('lostpointercapture', onCancel, true);
      document.removeEventListener('keydown', onKey, true);
    };
  }, [map]);

  return preview && <DispatchDragPreview {...preview} />;
}
