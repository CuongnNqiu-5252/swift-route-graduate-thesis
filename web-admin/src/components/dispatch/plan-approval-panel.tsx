'use client';

import { clockMinutes, dispatchDay, formatClockMinute, getPlanStartTime, readPlanStopTimings } from '@/application/dispatch-plan-clock';
import { useRouteWeather } from '@/application/use-route-weather';
import { DispatchRouteSequence } from '@/components/dispatch/dispatch-route-sequence';
import { PlanStatusBadge } from '@/components/dispatch/plan-status-badge';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import type { Driver } from '@/domain/entities/driver.entity';
import type { Order } from '@/domain/entities/order.entity';
import type { DispatchPlanRecord, MapPlanHighlight } from '@/domain/entities/routing.entity';
import { useLocale } from '@/i18n/provider';
import { formatVNDate } from '@/lib/utils';
import { Check, Clock, Package, Play, Truck, UserPlus } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { RouteRainDetails, WeatherCoverage } from './route-weather-risk';

interface PlanApprovalPanelProps {
  plans: DispatchPlanRecord[];
  drivers: Driver[];
  orders: Order[];
  routeColors: ReadonlyMap<string, string>;
  eligibleOrders: Order[];
  canExecute: boolean;
  isLoading: boolean;
  selectedPlanId: string | null;
  highlightedPlan?: MapPlanHighlight | null;
  editingPlanId: string | null;
  maxOrdersPerDriver?: number;
  initialFilter?: PlanFilter;
  onSelectPlan: (planId: string | null) => void;
  onHighlightPlan: (highlight: MapPlanHighlight | null) => void;
  onApprovePlan: (planId: string, driverId: string) => Promise<boolean>;
  onDispatchPlan: (planId: string) => Promise<void>;
  onCancelPlan: (planId: string, reason: string) => Promise<void>;
  onInsertOrderIntoPlan: (planId: string, orderId: string) => Promise<void>;
  onReorderStops: (plan: DispatchPlanRecord, ids: string[]) => Promise<DispatchPlanRecord>;
  onMoveStop: (plan: DispatchPlanRecord, orderId: string, targetPlanId: string, targetIndex: number) => Promise<void>;
}

type PlanFilter = 'CURRENT' | 'DRAFT' | 'APPROVED' | 'DISPATCHED';

const FILTERS: Array<{ key: PlanFilter; label: string }> = [
  { key: 'CURRENT', label: 'Đang vận hành' },
  { key: 'DRAFT', label: 'Chờ gán tài xế' },
  { key: 'APPROVED', label: 'Chờ giao tuyến' },
  { key: 'DISPATCHED', label: 'Đang giao' },
];

const GRID = 'grid grid-cols-[19rem_minmax(38rem,1fr)] md:grid-cols-[22rem_minmax(40rem,1fr)]';

function matchesFilter(plan: DispatchPlanRecord, filter: PlanFilter): boolean {
  switch (filter) {
    case 'DRAFT': return plan.plan_status === 'DRAFT';
    case 'APPROVED': return plan.plan_status === 'APPROVED';
    case 'DISPATCHED': return plan.plan_status === 'DISPATCHED';
    default: return plan.plan_status === 'DRAFT' || plan.plan_status === 'APPROVED' || plan.plan_status === 'DISPATCHED';
  }
}

export function PlanApprovalPanel({
  plans, drivers, orders, routeColors, eligibleOrders, canExecute, isLoading,
  selectedPlanId, highlightedPlan, editingPlanId, maxOrdersPerDriver, initialFilter, onSelectPlan, onHighlightPlan,
  onApprovePlan, onDispatchPlan, onCancelPlan, onInsertOrderIntoPlan, onReorderStops, onMoveStop,
}: PlanApprovalPanelProps) {
  const { t } = useLocale();
  const weather = useRouteWeather([
    ...plans.map((plan) => `${plan.plan_id}:${plan.version}:${plan.plan_status}`),
    ...orders.map((order) => `${order.id}:${order.status}`),
  ].join(','));
  const [filter, setFilter] = useState<PlanFilter>(initialFilter ?? 'CURRENT');
  const [busyPlanId, setBusyPlanId] = useState<string | null>(null);
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 15_000);
    return () => window.clearInterval(timer);
  }, []);

  const ordersById = useMemo(() => new Map(orders.map((order) => [order.id, order])), [orders]);
  const counts = useMemo(() => ({
    CURRENT: plans.filter((p) => matchesFilter(p, 'CURRENT')).length,
    DRAFT: plans.filter((p) => matchesFilter(p, 'DRAFT')).length,
    APPROVED: plans.filter((p) => matchesFilter(p, 'APPROVED')).length,
    DISPATCHED: plans.filter((p) => matchesFilter(p, 'DISPATCHED')).length,
  }), [plans]);
  const activeFilter = filter;
  const rows = useMemo(() => plans.filter((plan) => matchesFilter(plan, activeFilter))
    .sort((a, b) => Date.parse(b.created_at) - Date.parse(a.created_at))
    .map((plan) => {
      const timings = readPlanStopTimings(plan.route_waypoints);
      const startsAt = getPlanStartTime(plan, now);
      const stops = (plan.ordered_stop_ids ?? []).map((id) => {
        const order = ordersById.get(id);
        const timing = timings.get(id);
        const deadline = order?.time_window_end ? Date.parse(order.time_window_end) : NaN;
        const legacyLateness = Number.isFinite(deadline) && startsAt !== undefined && timing
          ? Math.max(0, Math.ceil((startsAt + timing.eta * 60_000 - deadline) / 60_000)) : undefined;
        const appointment = order && (order.time_window_start && order.time_window_end
          ? `${formatVNDate(order.time_window_start)} – ${formatVNDate(order.time_window_end)}` : null);
        return {
          id, label: order?.order_code || id.slice(0, 8), address: order?.delivery_address,
          eta: timing?.eta, lateBy: timing?.lateBy ?? legacyLateness, appointment,
          delivered: order?.status === 'DELIVERED'
        };
      });
      const completeData = stops.length === plan.order_count && stops.every((stop) => ordersById.has(stop.id));
      const departureMinute = startsAt === undefined ? undefined : clockMinutes(startsAt);
      const lastEta = stops.reduce<number | undefined>((latest, stop) =>
        stop.eta === undefined ? latest : Math.max(latest ?? 0, stop.eta), undefined);
      const duration = plan.estimated_duration_minutes ?? lastEta;
      return {
        plan,
        stops,
        lateStops: stops.filter((stop) => !stop.delivered && Boolean(stop.lateBy)).length,
        departureMinute,
        day: startsAt === undefined ? undefined : dispatchDay(startsAt),
        arrivalMinute: departureMinute === undefined || duration === undefined ? undefined : departureMinute + duration,
        color: routeColors.get(plan.plan_id) ?? '#475569',
        delivered: completeData || plan.plan_status === 'DRAFT' ? stops.filter((stop) => stop.delivered).length : null,
      };
    }), [plans, activeFilter, ordersById, routeColors, now]);

  const nowMinute = clockMinutes(now);
  const today = dispatchDay(now);
  const hasToday = rows.some((row) => row.day === today);
  const starts = rows.flatMap((row) => row.departureMinute === undefined ? [] : [row.departureMinute]);
  const axisStart = Math.floor(Math.min(...(starts.length ? starts : [nowMinute])) / 60) * 60;
  const latest = rows.reduce((end, row) => Math.max(end, row.arrivalMinute ?? row.departureMinute ?? end), axisStart);
  const tickMinutes = Math.max(60, Math.ceil((latest - axisStart) / 480) * 60);
  const tickCount = Math.max(3, Math.ceil((latest - axisStart) / tickMinutes));
  const axisMinutes = tickMinutes * tickCount;
  const ticks = Array.from({ length: tickCount + 1 }, (_, index) => ({
    minute: axisStart + index * tickMinutes,
    position: index / tickCount * 100,
  }));
  const nowPosition = hasToday && nowMinute >= axisStart && nowMinute <= axisStart + axisMinutes
    ? (nowMinute - axisStart) / axisMinutes * 100 : undefined;

  async function runAction(planId: string, action: () => Promise<unknown>) {
    setBusyPlanId(planId);
    try { await action(); } finally { setBusyPlanId(null); }
  }

  return (
    <div className="min-w-0">
      {plans.length > 0 && (
        <div role="group" aria-label={t('Lọc kế hoạch theo trạng thái')} className="flex gap-1.5 overflow-x-auto border-b border-slate-200/80 bg-slate-50/70 px-3.5 py-2 sm:px-4">
          {FILTERS.map(({ key, label }) => {
            const active = activeFilter === key;
            return (
              <button
                key={key}
                type="button"
                aria-pressed={active}
                className={`shrink-0 cursor-pointer whitespace-nowrap rounded-lg px-2.5 py-1 text-xs font-medium transition-colors duration-150 ${active
                    ? 'bg-slate-900 text-white shadow-2xs'
                    : 'text-slate-700 hover:bg-white hover:text-slate-950'
                  }`}
                onClick={() => { onHighlightPlan(null); setFilter(key); }}
              >
                {t(label)} <span className={`tabular-nums ${active ? 'text-slate-300' : 'text-slate-500'}`}>({counts[key]})</span>
              </button>
            );
          })}
        </div>
      )}

      {weather.allowed && (
        <div className="border-b border-slate-100 px-4 py-1.5">
          {weather.data ? <WeatherCoverage report={weather.data} />
            : <p role="status" className="text-xs text-slate-600">{t(weather.loading ? 'Đang kiểm tra dự báo theo tuyến…' : 'Chưa kiểm tra được thời tiết. Không thể kết luận tuyến an toàn.')}</p>}
        </div>
      )}
      {rows.length === 0 ? (
        <p className="px-4 py-8 text-center text-sm text-slate-600" role="status">
          {t(isLoading ? 'Đang tải kế hoạch…' : plans.length ? 'Không có kế hoạch nào trong nhóm này.' : 'Chưa có kế hoạch điều phối nào.')}
        </p>
      ) : (
        <div className="max-h-[min(36dvh,22rem)] overflow-auto overscroll-contain" data-ui>
          <div className="min-w-[57rem] md:min-w-[62rem]">
            <div className={`${GRID} sticky top-0 z-20 items-stretch border-b border-slate-200/80 bg-slate-50/95 text-xs text-slate-600 backdrop-blur-xs`}>
              <div className="sticky left-0 z-10 border-r border-slate-200/70 bg-slate-50/95 px-3.5 py-1.5 font-medium">
                {t('Tuyến')} <span className="ml-1 tabular-nums">({rows.length})</span>
                <p className="mt-0.5 text-[11px] font-normal text-slate-500">{t('Số đơn')} · ETA · {t('Đã giao')}</p>
              </div>
              <div className="min-w-0 px-4 py-1.5">
                <p className="text-center font-medium">{t('Giờ Việt Nam')}{nowPosition !== undefined ? ` · ${t('Bây giờ')} ${formatClockMinute(nowMinute)}` : ''}</p>
                <div className="relative h-5 tabular-nums">
                  {ticks.map(({ minute, position }) => (
                    <span key={minute} className={`absolute bottom-0 whitespace-nowrap ${position === 100 ? '-translate-x-full' : position > 0 ? '-translate-x-1/2' : ''}`}
                      style={{ left: `${position}%` }}>{formatClockMinute(minute)}</span>
                  ))}
                </div>
              </div>
            </div>

            {rows.map(({ plan, stops, color, delivered, lateStops, departureMinute, arrivalMinute, day }) => {
              const id = plan.plan_id;
              const selected = selectedPlanId === id;
              const isHovered = highlightedPlan?.planId === id;
              const busy = busyPlanId !== null || editingPlanId === id;
              const draft = plan.plan_status === 'DRAFT';
              const driver = plan.driver_id ? drivers.find((item) => item.id === plan.driver_id || item.user_id === plan.driver_id) : undefined;
              const isDraftEditable = draft && !plan.driver_id && typeof plan.version === 'number';
              const canEdit = canExecute && isDraftEditable && stops.length > 1
                && busyPlanId !== id && (editingPlanId === null || editingPlanId === id);
              const canMove = canExecute && isDraftEditable && busyPlanId === null && editingPlanId === null;
              const etaLabel = arrivalMinute === undefined ? '—' : formatClockMinute(arrivalMinute);
              const assignableDrivers = drivers.filter((item) => item.onboarding_status === 'ACTIVE' && (item.user_id || item.id));
              const driverChoices = assignableDrivers.length > 0 ? assignableDrivers : drivers.filter((item) => Boolean(item.user_id || item.id));
              return (
                <section key={id} id={`dispatch-plan-${id}`} aria-label={`${t('Kế hoạch')} #${id.slice(0, 8)}`}
                  onPointerEnter={(event) => { if (event.pointerType !== 'touch') onHighlightPlan({ planId: id }); }}
                  onPointerLeave={(event) => { if (event.pointerType !== 'touch') onHighlightPlan(null); }}
                  onFocus={() => onHighlightPlan({ planId: id })}
                  onBlur={(event) => { if (!event.currentTarget.contains(event.relatedTarget)) onHighlightPlan(null); }}
                  className={`${GRID} border-b border-slate-100 transition-colors duration-150 ${isHovered
                      ? 'bg-blue-50/40'
                      : selected
                        ? 'bg-slate-50'
                        : 'bg-white hover:bg-slate-50/80'
                    }`}>
                  <div className={`sticky left-0 z-10 border-r border-slate-100 px-3.5 py-2.5 transition-colors duration-150 ${isHovered ? 'bg-blue-50/90' : selected ? 'bg-slate-50' : 'bg-white'
                    }`}>
                    <span
                      aria-hidden="true"
                      className={`absolute inset-y-2 left-0 rounded-r transition-all duration-150 ${isHovered || selected ? 'w-1.5' : 'w-1'}`}
                      style={{ backgroundColor: color }}
                    />

                    {/* Row 1: Plan ID + Status Badge */}
                    <div className="flex min-w-0 items-center justify-between gap-2">
                      <button
                        type="button"
                        aria-pressed={selected}
                        className="shrink-0 cursor-pointer font-mono text-sm font-semibold text-slate-950 underline-offset-4 hover:underline"
                        onClick={() => onSelectPlan(selected ? null : id)}
                      >
                        #{id.slice(0, 8)}
                      </button>
                      <PlanStatusBadge status={plan.plan_status} />
                    </div>

                    {/* Row 2: Metrics */}
                    <div className="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs tabular-nums text-slate-600">
                      <span className="inline-flex items-center gap-1" title={t('Số đơn')} aria-label={`${t('Số đơn')}: ${plan.order_count}`}><Package size={12} aria-hidden="true" />{plan.order_count}</span>
                      <span className="inline-flex items-center gap-1 whitespace-nowrap" title="ETA" aria-label={`ETA: ${etaLabel}`}><Clock size={12} aria-hidden="true" />{etaLabel}</span>
                      <span className="inline-flex items-center gap-1 whitespace-nowrap" title={t('Đơn đã giao')} aria-label={`${t('Đơn đã giao')}: ${delivered ?? '—'}`}><Check size={13} aria-hidden="true" />{delivered ?? '—'}</span>
                      {plan.total_distance_km != null && (
                        <span className="whitespace-nowrap text-slate-500">{Number(plan.total_distance_km).toFixed(1)} km</span>
                      )}
                    </div>

                    {lateStops > 0 && <p className="mt-1 text-xs font-semibold text-amber-800">{t('Điểm dự kiến trễ')} · {lateStops}</p>}
                    {weather.data?.routes.filter((risk) => risk.plan_id === id).map((risk) => (
                      <RouteRainDetails key={id} risk={risk} onSelect={() => onSelectPlan(id)} />
                    ))}

                    {/* Row 3: Organized Driver Assignment / Approve / Dispatch / Cancel Action Bar */}
                    <div className="mt-2 flex flex-wrap items-center gap-1.5">
                      {plan.driver_id && (
                        <span
                          className="inline-flex max-w-[10rem] items-center gap-1 truncate rounded-md border border-slate-200 bg-slate-50 px-2 py-1 text-xs font-medium text-slate-700"
                          title={driver?.name ?? plan.driver_id}
                        >
                          <Truck size={12} className="shrink-0 text-slate-500" aria-hidden="true" />
                          <span className="truncate">{driver?.name ?? `#${plan.driver_id.slice(0, 8)}`}</span>
                        </span>
                      )}

                      {draft && canExecute && (
                        <DropdownMenu>
                          <DropdownMenuTrigger asChild>
                            <button
                              type="button"
                              disabled={busy || driverChoices.length === 0}
                              aria-label={`${t('Gán tài xế')} #${id.slice(0, 8)}`}
                              title={driverChoices.length === 0 ? t('Chưa có tài xế hoạt động') : t('Chọn tài xế để gán ngay')}
                              className="inline-flex h-7 shrink-0 cursor-pointer items-center gap-1.5 rounded-lg bg-slate-900 px-2.5 text-xs font-medium text-white transition-colors duration-150 hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-50"
                            >
                              <UserPlus size={12} aria-hidden="true" />
                              <span>{t('Gán tài xế & duyệt')}</span>
                            </button>
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="start">
                            <DropdownMenuLabel>{t('Chọn tài xế cho tuyến')} #{id.slice(0, 8)}</DropdownMenuLabel>
                            {driverChoices.map((item) => {
                              const driverKey = item.user_id || item.id;
                              return (
                                <DropdownMenuItem
                                  key={driverKey}
                                  onSelect={() => void runAction(id, () => onApprovePlan(id, driverKey))}
                                >
                                  <span
                                    aria-hidden="true"
                                    className={`size-2 shrink-0 rounded-full ${item.is_available ? 'bg-emerald-500' : 'bg-slate-300'}`}
                                  />
                                  <div className="min-w-0 flex-1">
                                    <p className="truncate font-medium text-slate-900">{item.name}</p>
                                    <p className="truncate text-xs text-slate-500">
                                      {item.vehicle_plate || item.vehicle_type || t('Chưa có biển số')}
                                    </p>
                                  </div>
                                </DropdownMenuItem>
                              );
                            })}
                          </DropdownMenuContent>
                        </DropdownMenu>
                      )}

                      {canExecute && plan.plan_status === 'APPROVED' && (
                        <button
                          type="button"
                          disabled={busy}
                          onClick={() => void runAction(id, () => onDispatchPlan(id))}
                          className="inline-flex h-7 shrink-0 cursor-pointer items-center gap-1.5 rounded-lg bg-[#17766c] px-2.5 text-xs font-medium text-white transition-colors duration-150 hover:bg-[#136058] disabled:cursor-not-allowed disabled:opacity-50"
                        >
                          <Play size={11} aria-hidden="true" />
                          <span>{t('Giao tuyến')}</span>
                        </button>
                      )}

                      {canExecute && plan.plan_status === 'DISPATCHED' && eligibleOrders.length > 0 && (
                        <DropdownMenu>
                          <DropdownMenuTrigger asChild>
                            <button
                              type="button"
                              disabled={busy || (typeof maxOrdersPerDriver === 'number' && plan.order_count >= maxOrdersPerDriver)}
                              title={typeof maxOrdersPerDriver === 'number' && plan.order_count >= maxOrdersPerDriver ? t('Tuyến đích đã đạt số đơn gộp tối đa mỗi chuyến.') : undefined}
                              className="inline-flex h-7 shrink-0 cursor-pointer items-center gap-1 rounded-lg border border-slate-200 bg-white px-2 text-xs font-medium text-slate-700 transition-colors hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
                            >
                              <UserPlus size={12} aria-hidden="true" />
                              <span>{t('Chèn đơn')} ({eligibleOrders.length})</span>
                            </button>
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="start">
                            <DropdownMenuLabel>{t('Chọn đơn chèn vào tuyến')}</DropdownMenuLabel>
                            {eligibleOrders.map((order) => (
                              <DropdownMenuItem
                                key={order.id}
                                onSelect={() => void runAction(id, () => onInsertOrderIntoPlan(id, order.id))}
                              >
                                <div className="min-w-0 flex-1">
                                  <p className="font-mono text-xs font-semibold text-slate-900">{order.order_code}</p>
                                  <p className="truncate text-xs text-slate-500">{order.delivery_address}</p>
                                </div>
                              </DropdownMenuItem>
                            ))}
                          </DropdownMenuContent>
                        </DropdownMenu>
                      )}

                      {canExecute && (
                        <button
                          type="button"
                          disabled={busy}
                          onClick={() => void runAction(id, () => onCancelPlan(id, 'Điều phối viên hủy kế hoạch để lập lại lộ trình'))}
                          className="ml-auto inline-flex h-7 shrink-0 cursor-pointer items-center rounded-lg px-2 text-xs font-medium text-rose-700 transition-colors duration-150 hover:bg-rose-50 hover:text-rose-800 disabled:cursor-not-allowed disabled:opacity-50"
                        >
                          {t('Hủy')}
                        </button>
                      )}
                    </div>
                  </div>

                  <div className="relative min-w-0">
                    <div aria-hidden="true" className="pointer-events-none absolute inset-y-0 left-4 right-4">
                      {ticks.map(({ minute, position }) => <span key={minute} className="absolute inset-y-0 border-l border-slate-200/65" style={{ left: `${position}%` }} />)}
                      {nowPosition !== undefined && day === today && <span className="absolute inset-y-0 border-l border-sky-600" style={{ left: `${nowPosition}%` }} />}
                    </div>
                    <DispatchRouteSequence
                      plan={plan}
                      stops={stops}
                      color={color}
                      departureMinute={departureMinute}
                      axisStart={axisStart}
                      axisMinutes={axisMinutes}
                      highlightedStopId={highlightedPlan?.planId === id ? highlightedPlan.stopId : undefined}
                      isRouteHighlighted={isHovered || selected}
                      onSelectRoute={() => onSelectPlan(id)}
                      onHighlightStop={(stopId) => onHighlightPlan({ planId: id, stopId })}
                      onReorder={canEdit ? (ids) => onReorderStops(plan, ids) : undefined}
                      acceptsDrops={canMove}
                      onMoveStop={canMove
                        ? (orderId, targetPlanId, targetIndex) => onMoveStop(plan, orderId, targetPlanId, targetIndex)
                        : undefined}
                    />
                  </div>

                </section>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}
