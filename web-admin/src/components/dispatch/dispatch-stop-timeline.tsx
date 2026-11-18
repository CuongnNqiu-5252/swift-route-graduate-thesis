'use client';

import { useMemo } from 'react';
import { Clock, MapPin, Package, Route, UserRound, Warehouse, X } from 'lucide-react';
import { Driver } from '@/domain/entities/driver.entity';
import { ORDER_STATUS_LABEL, Order, OrderStatus } from '@/domain/entities/order.entity';
import { DispatchDepot, DispatchPlanRecord, OptimizationStop } from '@/domain/entities/routing.entity';
import { PlanStatusBadge } from '@/components/dispatch/plan-status-badge';
import { Button } from '@/components/ui/button';
import { useLocale } from '@/i18n/provider';

interface DispatchStopTimelineProps {
  plan: DispatchPlanRecord;
  depot: DispatchDepot | null;
  stops: OptimizationStop[];
  orders: Order[];
  driver?: Driver;
  routeColor?: string;
  onClose: () => void;
}

interface WaypointTiming {
  cumulativeDistanceKm?: number;
  estimatedArrivalMinutes?: number;
}

const ORDER_STATUS_STYLES: Record<OrderStatus, string> = {
  READY_FOR_DISPATCH: 'bg-slate-100 text-slate-700',
  PLANNED: 'bg-sky-50 text-sky-800',
  IN_TRANSIT: 'bg-amber-50 text-amber-800',
  DELIVERED: 'bg-emerald-50 text-emerald-800',
  FAILED: 'bg-rose-50 text-rose-800',
  RETURNING: 'bg-orange-50 text-orange-800',
  CANCELLED: 'bg-neutral-100 text-neutral-600',
  RETURNED: 'bg-purple-50 text-purple-800',
};

function parseWaypointTimings(raw?: string | null): Map<string, WaypointTiming> {
  const map = new Map<string, WaypointTiming>();
  if (!raw) return map;
  try {
    const parsed = JSON.parse(raw) as Array<{
      stop_id?: string;
      cumulative_distance_km?: number;
      estimated_arrival_minutes?: number;
    }>;
    if (!Array.isArray(parsed)) return map;
    parsed.forEach((wp, index) => {
      const timing: WaypointTiming = {
        cumulativeDistanceKm:
          typeof wp.cumulative_distance_km === 'number' && Number.isFinite(wp.cumulative_distance_km)
            ? wp.cumulative_distance_km
            : undefined,
        estimatedArrivalMinutes:
          typeof wp.estimated_arrival_minutes === 'number' && Number.isFinite(wp.estimated_arrival_minutes)
            ? wp.estimated_arrival_minutes
            : undefined,
      };
      if (wp.stop_id) map.set(wp.stop_id, timing);
      map.set(`idx:${index}`, timing);
    });
  } catch {
    // Ignore malformed snapshot JSON
  }
  return map;
}

export function DispatchStopTimeline({
  plan,
  depot,
  stops,
  orders,
  driver,
  routeColor = '#2563eb',
  onClose,
}: DispatchStopTimelineProps) {
  const { t, intlLocale } = useLocale();

  const ordersById = useMemo(() => new Map(orders.map((o) => [o.id, o])), [orders]);
  const waypointTimings = useMemo(() => parseWaypointTimings(plan.route_waypoints), [plan.route_waypoints]);

  const departureMs = useMemo(() => {
    const raw = plan.dispatched_at;
    const ms = raw ? Date.parse(raw) : NaN;
    return Number.isFinite(ms) ? ms : null;
  }, [plan.dispatched_at]);

  const formatTime = (ms: number) =>
    new Date(ms).toLocaleTimeString(intlLocale, { hour: '2-digit', minute: '2-digit', timeZone: 'Asia/Ho_Chi_Minh' });

  return (
    <aside
      aria-label={`${t('Chi tiết lộ trình')} #${plan.plan_id.slice(0, 8)}`}
      className="pointer-events-auto absolute bottom-12 right-2 top-3 z-30 flex w-[23rem] max-w-[calc(100%-1rem)] flex-col overflow-hidden rounded-xl bg-white shadow-xl sm:right-14"
    >
      {/* Header */}
      <div className="flex items-center justify-between gap-2 border-b border-slate-200 bg-slate-50 px-3 py-3 sm:px-4">
        <div className="min-w-0 space-y-1.5">
          <div className="flex items-center gap-2">
            <span
              className="size-2.5 shrink-0 rounded-full"
              style={{ backgroundColor: routeColor }}
              aria-hidden="true"
            />
            <h2 className="truncate font-mono text-sm font-semibold text-slate-950">
              {t('Kế hoạch')} #{plan.plan_id.slice(0, 8)}
            </h2>
          </div>
          {(plan.plan_status !== 'DRAFT' || plan.driver_id) && (
            <PlanStatusBadge status={plan.plan_status} acknowledgedAt={plan.acknowledged_at} />
          )}
        </div>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          className="size-11 shrink-0 px-0 text-slate-500 hover:text-slate-900"
          onClick={onClose}
          aria-label={t('Đóng')}
          title={t('Đóng')}
        >
          <X size={18} aria-hidden="true" />
        </Button>
      </div>

      {/* Metrics & Driver */}
      <div className="space-y-2.5 border-b border-slate-200 px-4 py-3">
        <div className="grid grid-cols-3 gap-2">
          <div className="rounded-lg bg-slate-50 px-2.5 py-2">
            <p className="text-[11px] font-medium text-slate-500">{t('Điểm giao')}</p>
            <p className="mt-0.5 text-sm font-semibold tabular-nums text-slate-950">{stops.length}</p>
          </div>
          <div className="rounded-lg bg-slate-50 px-2.5 py-2">
            <p className="text-[11px] font-medium text-slate-500">{t('Quãng đường')}</p>
            <p className="mt-0.5 text-sm font-semibold tabular-nums text-slate-950">
              {plan.total_distance_km != null ? `${Number(plan.total_distance_km).toFixed(1)} km` : '—'}
            </p>
          </div>
          <div className="rounded-lg bg-slate-50 px-2.5 py-2">
            <p className="text-[11px] font-medium text-slate-500">{t('Thời gian')}</p>
            <p className="mt-0.5 text-sm font-semibold tabular-nums text-slate-950">
              {plan.estimated_duration_minutes != null ? `${plan.estimated_duration_minutes} ${t('phút')}` : '—'}
            </p>
          </div>
        </div>

        <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-slate-600">
          <span className="flex items-center gap-1.5 font-medium text-slate-800">
            <UserRound size={14} className="shrink-0 text-slate-500" aria-hidden="true" />
            {driver
              ? `${driver.name}${driver.vehicle_plate ? ` · ${driver.vehicle_plate}` : ''}`
              : t('Chưa gán tài xế')}
          </span>
          {plan.algorithm_used && (
            <span className="rounded bg-slate-100 px-1.5 py-0.5 font-mono text-[11px] text-slate-600">
              {plan.algorithm_used}
            </span>
          )}
        </div>
      </div>

      {/* Timeline */}
      <div className="min-h-0 flex-1 overflow-y-auto px-4 py-3">
        <ol className="relative space-y-4">
          {depot && (
            <li className="relative flex gap-3">
              <div className="flex flex-col items-center">
                <span className="flex size-6 shrink-0 items-center justify-center rounded-md bg-slate-900 text-[11px] font-bold text-white">
                  <Warehouse size={13} aria-hidden="true" />
                </span>
                {stops.length > 0 && <span className="mt-1 w-0.5 flex-1 bg-slate-200" aria-hidden="true" />}
              </div>
              <div className="min-w-0 pb-1">
                <div className="flex items-center gap-2">
                  <p className="text-xs font-semibold text-slate-900">{depot.name}</p>
                  {departureMs !== null && (
                    <span className="text-[11px] tabular-nums text-slate-500">
                      {formatTime(departureMs)}
                    </span>
                  )}
                </div>
                <p className="mt-0.5 text-xs leading-relaxed text-slate-500">{depot.address}</p>
              </div>
            </li>
          )}

          {stops.map((stop, index) => {
            const matchedOrder = ordersById.get(stop.id);
            const timing = waypointTimings.get(stop.id) ?? waypointTimings.get(`idx:${index}`);
            const etaMinutes = timing?.estimatedArrivalMinutes;
            const etaClock =
              departureMs !== null && etaMinutes !== undefined
                ? formatTime(departureMs + Math.round(etaMinutes) * 60_000)
                : null;
            const isLast = index === stops.length - 1;

            return (
              <li key={stop.id} className="relative flex gap-3">
                <div className="flex flex-col items-center">
                  <span
                    className="flex size-6 shrink-0 items-center justify-center rounded-full text-[11px] font-bold text-white"
                    style={{ backgroundColor: routeColor }}
                  >
                    {index + 1}
                  </span>
                  {!isLast && <span className="mt-1 w-0.5 flex-1 bg-slate-200" aria-hidden="true" />}
                </div>

                <div className="min-w-0 flex-1 rounded-lg border border-slate-200 bg-white p-2.5">
                  <div className="flex flex-wrap items-center justify-between gap-1.5">
                    <span className="flex items-center gap-1 font-mono text-xs font-semibold text-slate-950">
                      <Package size={12} className="shrink-0 text-slate-400" aria-hidden="true" />
                      {stop.order?.order_code || `#${index + 1}`}
                    </span>
                    {matchedOrder && (
                      <span
                        className={`rounded px-1.5 py-0.5 text-[10px] font-medium ${ORDER_STATUS_STYLES[matchedOrder.status]}`}
                      >
                        {t(ORDER_STATUS_LABEL[matchedOrder.status])}
                      </span>
                    )}
                  </div>

                  <p className="mt-1 flex items-start gap-1 text-xs leading-5 text-slate-600">
                    <MapPin size={12} className="mt-1 shrink-0 text-slate-400" aria-hidden="true" />
                    <span>{stop.order?.delivery_address || t('Chưa có địa chỉ')}</span>
                  </p>

                  <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 border-t border-slate-100 pt-1.5 text-[11px] text-slate-500">
                    <span className="flex items-center gap-1 tabular-nums">
                      <Clock size={11} aria-hidden="true" />
                      {etaMinutes !== undefined
                        ? `ETA ${etaClock ? `${etaClock} (+${Math.round(etaMinutes)}p)` : `+${Math.round(etaMinutes)} ${t('phút')}`}`
                        : `ETA ${t('Chưa có dữ liệu')}`}
                    </span>
                    {timing?.cumulativeDistanceKm !== undefined && (
                      <span className="flex items-center gap-1 tabular-nums">
                        <Route size={11} aria-hidden="true" />
                        {timing.cumulativeDistanceKm.toFixed(1)} km
                      </span>
                    )}
                    {matchedOrder?.time_window_start && matchedOrder?.time_window_end && (
                      <span className="tabular-nums text-slate-600">
                        {t('Khung giờ')}: {matchedOrder.time_window_start}–{matchedOrder.time_window_end}
                      </span>
                    )}
                  </div>
                </div>
              </li>
            );
          })}
        </ol>
      </div>
    </aside>
  );
}
