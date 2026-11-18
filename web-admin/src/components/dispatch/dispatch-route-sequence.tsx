'use client';

import { type PointerEvent, useRef, useState } from 'react';
import { ArrowLeft, ArrowRight, Check, Undo2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import type { DispatchPlanRecord } from '@/domain/entities/routing.entity';
import { useLocale } from '@/i18n/provider';
import { formatClockMinute } from '@/application/dispatch-plan-clock';

export interface PlanTimelineStop {
  id: string;
  label: string;
  address?: string;
  eta?: number;
  delivered: boolean;
}

export function readPlanStopTimings(raw?: string | null): Map<string, number> {
  const timings = new Map<string, number>();
  if (!raw) return timings;
  try {
    const points: unknown = JSON.parse(raw);
    if (!Array.isArray(points)) return timings;
    let previousEta = 0;
    for (const point of points) {
      const eta: unknown = point?.estimated_arrival_minutes;
      if (typeof point?.stop_id === 'string' && typeof eta === 'number' && Number.isFinite(eta) && eta >= previousEta) {
        timings.set(point.stop_id, eta);
        previousEta = eta;
      }
    }
  } catch {
    // A legacy snapshot can omit usable times. Those stops are displayed in sequence below the axis.
  }
  return timings;
}

export function DispatchRouteSequence({
  plan,
  stops,
  color,
  departureMinute,
  axisStart,
  axisMinutes,
  onSelectRoute,
  onHighlightStop,
  onReorder,
  acceptsDrops = false,
  onMoveStop,
}: {
  plan: DispatchPlanRecord;
  stops: PlanTimelineStop[];
  color: string;
  departureMinute?: number;
  axisStart: number;
  axisMinutes: number;
  onSelectRoute: () => void;
  onHighlightStop: (stopId?: string) => void;
  onReorder?: (ids: string[]) => Promise<DispatchPlanRecord>;
  /** True when stops from other routes may be dropped on this route. */
  acceptsDrops?: boolean;
  /** Moves a stop of this route into another route at a zero-based position. */
  onMoveStop?: (orderId: string, targetPlanId: string, targetIndex: number) => Promise<void>;
}) {
  const { t } = useLocale();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [draggedId, setDraggedId] = useState<string | null>(null);
  const [targetId, setTargetId] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const saving = useRef(false);
  const railRef = useRef<HTMLDivElement>(null);
  const suppressClick = useRef(false);
  const pointerDrag = useRef<{ id: number; index: number; x: number; y: number; active: boolean } | null>(null);
  const [error, setError] = useState('');
  const [previous, setPrevious] = useState<{ ids: string[]; version?: number } | null>(null);
  const selectedIndex = stops.findIndex((stop) => stop.id === selectedId);
  const selectedStop = stops[selectedIndex];
  const draggedIndex = stops.findIndex((stop) => stop.id === draggedId);
  const editable = Boolean(onReorder && stops.length > 1);
  const canDrag = editable || Boolean(onMoveStop);
  const formatEta = (minutes?: number) => minutes === undefined || departureMinute === undefined
    ? t('Chưa có ETA')
    : formatClockMinute(departureMinute + minutes);
  const positionAt = (minute: number) => (minute - axisStart) / axisMinutes * 100;

  // Keep all stops aligned on a single route bar; slightly separate stops whose ETAs sit on top of each other.
  const timedStops = stops.reduce<Array<{ stop: PlanTimelineStop; index: number; position: number }>>(
    (acc, stop, index) => {
      if (stop.eta === undefined || departureMinute === undefined) return acc;
      const raw = positionAt(departureMinute + stop.eta);
      const prev = acc.at(-1)?.position;
      const position = prev === undefined ? raw : Math.min(100, Math.max(raw, prev + 2.6));
      acc.push({ stop, index, position });
      return acc;
    },
    [],
  );
  const unknownStops = stops.flatMap((stop, index) =>
    stop.eta === undefined || departureMinute === undefined ? [{ stop, index }] : [],
  );
  const firstPosition = departureMinute === undefined ? 0 : positionAt(departureMinute);
  const lastEta = timedStops.at(-1)?.position ?? 0;

  async function save(ids: string[], undo = false) {
    if (!onReorder || saving.current) return;
    saving.current = true;
    setIsSaving(true);
    setError('');
    try {
      const updated = await onReorder(ids);
      setPrevious(undo ? null : { ids: stops.map((stop) => stop.id), version: updated.version });
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : t('Không thể cập nhật thứ tự điểm giao.'));
    } finally {
      saving.current = false;
      setIsSaving(false);
    }
  }

  async function moveAcross(orderId: string, targetPlanId: string, targetIndex: number) {
    if (!onMoveStop || saving.current) return;
    saving.current = true;
    setIsSaving(true);
    setError('');
    try {
      await onMoveStop(orderId, targetPlanId, targetIndex);
      setSelectedId(null);
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : t('Không thể chuyển điểm giao sang tuyến khác.'));
    } finally {
      saving.current = false;
      setIsSaving(false);
    }
  }

  /** Finds another route under the pointer and the position inside it where the stop would land. */
  function foreignDrop(x: number, y: number): { planId: string; index: number } | null {
    const rail = document.elementFromPoint(x, y)?.closest<HTMLElement>('[data-drop-plan]');
    const planId = rail?.dataset.dropPlan;
    if (!rail || !planId || planId === plan.plan_id) return null;
    const hit = document.elementFromPoint(x, y)?.closest<HTMLElement>('[data-stop-index]');
    if (hit && rail.contains(hit)) return { planId, index: Number(hit.dataset.stopIndex) };
    // Dropped on empty rail space: insert before the first stop that is to the right of the pointer.
    let index = 0;
    for (const button of rail.querySelectorAll<HTMLElement>('[data-stop-index]')) {
      const rect = button.getBoundingClientRect();
      if (rect.left + rect.width / 2 < x) index = Number(button.dataset.stopIndex) + 1;
    }
    return { planId, index };
  }

  function move(from: number, to: number) {
    if (!Number.isInteger(from) || !Number.isInteger(to)
      || from === to || from < 0 || to < 0 || from >= stops.length || to >= stops.length) return;
    const ids = stops.map((stop) => stop.id);
    const [moved] = ids.splice(from, 1);
    ids.splice(to, 0, moved);
    setSelectedId(moved);
    onSelectRoute();
    void save(ids);
  }

  function dropIndex(x: number, y: number): number {
    const rail = railRef.current;
    if (!rail) return -1;
    const bounds = rail.getBoundingClientRect();
    if (x < bounds.left || x > bounds.right || y < bounds.top - 10 || y > bounds.bottom + 10) return -1;
    const hit = document.elementFromPoint(x, y)?.closest<HTMLElement>('[data-stop-index]');
    if (hit && rail.contains(hit)) return Number(hit.dataset.stopIndex);

    let nearest = -1;
    let distance = Infinity;
    for (const button of rail.querySelectorAll<HTMLElement>('[data-stop-index]')) {
      const rect = button.getBoundingClientRect();
      const offset = Math.hypot(x - (rect.left + rect.width / 2), y - (rect.top + rect.height / 2));
      if (offset < distance) {
        distance = offset;
        nearest = Number(button.dataset.stopIndex);
      }
    }
    return nearest;
  }

  function clearDrag() {
    pointerDrag.current = null;
    setDraggedId(null);
    setTargetId(null);
  }

  function updateDrag(event: PointerEvent<HTMLButtonElement>) {
    const drag = pointerDrag.current;
    if (!drag || drag.id !== event.pointerId) return;
    if (!drag.active && Math.hypot(event.clientX - drag.x, event.clientY - drag.y) < 5) return;
    drag.active = true;
    setDraggedId(stops[drag.index]?.id ?? null);
    const target = stops[dropIndex(event.clientX, event.clientY)]?.id;
    setTargetId(target ?? null);
    onHighlightStop(target);
  }

  function stopButton(stop: PlanTimelineStop, index: number) {
    const label = `${index + 1}. ${stop.label} · ${formatEta(stop.eta)}`;
    const selected = selectedId === stop.id;
    const target = targetId === stop.id && draggedIndex !== index;
    return (
      <button
        type="button"
        data-stop-index={index}
        aria-label={label}
        title={stop.address ? `${label} · ${stop.address}` : label}
        aria-pressed={selected}
        onPointerEnter={(event) => { if (event.pointerType !== 'touch') onHighlightStop(stop.id); }}
        onPointerLeave={(event) => { if (event.pointerType !== 'touch' && !pointerDrag.current?.active) onHighlightStop(); }}
        onFocus={(event) => { event.stopPropagation(); onHighlightStop(stop.id); }}
        onBlur={() => onHighlightStop()}
        onClick={(event) => {
          if (suppressClick.current && event.detail > 0) {
            suppressClick.current = false;
            return;
          }
          setSelectedId(selected ? null : stop.id);
          onSelectRoute();
        }}
        onPointerDown={(event) => {
          if (!canDrag || saving.current || !event.isPrimary || event.button !== 0) return;
          suppressClick.current = false;
          pointerDrag.current = { id: event.pointerId, index, x: event.clientX, y: event.clientY, active: false };
          event.currentTarget.setPointerCapture(event.pointerId);
        }}
        onPointerMove={updateDrag}
        onPointerUp={(event) => {
          const drag = pointerDrag.current;
          if (!drag || drag.id !== event.pointerId) return;
          clearDrag();
          if (!drag.active) return;
          suppressClick.current = true;
          const foreign = foreignDrop(event.clientX, event.clientY);
          if (foreign) {
            void moveAcross(stops[drag.index].id, foreign.planId, foreign.index);
            return;
          }
          const target = dropIndex(event.clientX, event.clientY);
          onHighlightStop(target >= 0 ? stops[target].id : undefined);
          if (editable) move(drag.index, target);
        }}
        onPointerCancel={(event) => { if (pointerDrag.current?.id === event.pointerId) clearDrag(); }}
        onLostPointerCapture={(event) => { if (pointerDrag.current?.id === event.pointerId) clearDrag(); }}
        className={`relative grid size-8 shrink-0 place-items-center rounded-md focus-visible:outline-2 focus-visible:outline-blue-700
          ${canDrag ? 'touch-none cursor-grab select-none active:cursor-grabbing' : 'cursor-pointer'}
          ${draggedId === stop.id ? 'opacity-50' : ''}`}
      >
        {target && (
          <span aria-hidden="true" className={`absolute top-1 h-6 w-0.5 rounded-full bg-slate-900 ${draggedIndex < index ? 'right-0' : 'left-0'}`} />
        )}
        <span
          aria-hidden="true"
          className={`grid size-4 place-items-center rounded-full border-2 ${selected ? 'ring-2 ring-slate-600 ring-offset-2' : ''} ${stop.delivered ? 'text-white' : 'bg-white'}`}
          style={{ borderColor: color, backgroundColor: stop.delivered || selected ? color : undefined }}
        >
          {stop.delivered && <Check size={10} strokeWidth={3} />}
        </span>
      </button>
    );
  }

  return (
    <div className="min-w-0 py-1" aria-busy={isSaving}>
      <div ref={railRef} data-drop-plan={acceptsDrops ? plan.plan_id : undefined}>
      {timedStops.length > 0 && (
        <div className="relative mx-4 h-8">
          <div aria-hidden="true" className="absolute top-2 h-4 rounded-full"
            style={{ left: `${firstPosition}%`, width: `${Math.max(0, lastEta - firstPosition)}%`, backgroundColor: `color-mix(in srgb, ${color} 22%, white)` }} />
          {timedStops.map(({ stop, index, position }) => (
            <div key={stop.id} className={`absolute top-0 -translate-x-1/2 ${selectedId === stop.id ? 'z-20' : 'z-10 hover:z-20'}`} style={{ left: `${position}%` }}>
              {stopButton(stop, index)}
            </div>
          ))}
        </div>
      )}
      {unknownStops.length > 0 && (
        <div className="flex flex-wrap items-center gap-1 px-4 text-xs text-slate-600">
          <span>{t('Chưa có ETA')}</span>
          {unknownStops.map(({ stop, index }) => <div key={stop.id}>{stopButton(stop, index)}</div>)}
        </div>
      )}
      </div>
      {selectedStop && (
        <div className="flex min-w-0 flex-wrap items-center gap-2 px-4 pb-1 text-xs text-slate-700">
          <span className="min-w-0 break-words">
            <strong>{selectedIndex + 1}. {selectedStop.label}</strong>
            {selectedStop.address && ` · ${selectedStop.address}`}
            {selectedStop.eta !== undefined && ` · ${formatEta(selectedStop.eta)}`}
            {selectedStop.delivered && ` · ${t('Đã giao')}`}
          </span>
          {editable && (
            <div className="flex items-center gap-1">
              <Button type="button" size="sm" variant="ghost" className="size-10 px-0"
                aria-label={t('Dời điểm lên trước')} disabled={isSaving || selectedIndex <= 0}
                onClick={() => move(selectedIndex, selectedIndex - 1)}><ArrowLeft size={15} aria-hidden="true" /></Button>
              <Button type="button" size="sm" variant="ghost" className="size-10 px-0"
                aria-label={t('Dời điểm ra sau')} disabled={isSaving || selectedIndex >= stops.length - 1}
                onClick={() => move(selectedIndex, selectedIndex + 1)}><ArrowRight size={15} aria-hidden="true" /></Button>
              {previous && previous.version === plan.version && (
                <Button type="button" size="sm" variant="ghost" disabled={isSaving} onClick={() => void save(previous.ids, true)}>
                  <Undo2 size={14} aria-hidden="true" />{t('Hoàn tác')}
                </Button>
              )}
            </div>
          )}
        </div>
      )}
      {isSaving && <p role="status" className="px-4 text-xs text-slate-600">{t('Đang tính lại đường đi và lưu thứ tự…')}</p>}
      {error && <p role="alert" className="px-4 text-xs text-rose-800">{error}</p>}
    </div>
  );
}
