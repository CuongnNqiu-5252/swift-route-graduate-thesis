'use client';

import { clockMinutes, dispatchDay, formatClockMinute, getPlanStartTime } from '@/application/dispatch-plan-clock';
import { DispatchRouteSequence, readPlanStopTimings } from '@/components/dispatch/dispatch-route-sequence';
import { PlanStatusBadge } from '@/components/dispatch/plan-status-badge';
import { Button } from '@/components/ui/button';
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
import { Check, Clock, Package, Play, Plus, RefreshCw, Truck, UserPlus, X } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';

interface PlanApprovalPanelProps {
  plans: DispatchPlanRecord[];
  drivers: Driver[];
  orders: Order[];
  routeColors: ReadonlyMap<string, string>;
  eligibleOrders: Order[];
  canExecute: boolean;
  isLoading: boolean;
  isTriggering: boolean;
  selectedPlanId: string | null;
  editingPlanId: string | null;
  initialFilter?: PlanFilter;
  onSelectPlan: (planId: string | null) => void;
  onHighlightPlan: (highlight: MapPlanHighlight | null) => void;
  onClose: () => void;
  onRefresh: () => void;
  onTriggerPlanning: () => Promise<boolean>;
  onApprovePlan: (planId: string, driverId: string) => Promise<boolean>;
  onDispatchPlan: (planId: string) => Promise<void>;
  onCancelPlan: (planId: string, reason: string) => Promise<void>;
  onInsertOrderIntoPlan: (planId: string, orderId: string) => Promise<void>;
  onReorderStops: (plan: DispatchPlanRecord, ids: string[]) => Promise<DispatchPlanRecord>;
  onMoveStop: (plan: DispatchPlanRecord, orderId: string, targetPlanId: string, targetIndex: number) => Promise<void>;
}

type PlanFilter = 'CURRENT' | 'ACTION' | 'ACTIVE' | 'COMPLETED' | 'ALL';

const FILTERS: Array<{ key: PlanFilter; label: string }> = [
  { key: 'CURRENT', label: 'Đang vận hành' },
  { key: 'ACTION', label: 'Cần xử lý' },
  { key: 'ACTIVE', label: 'Tuyến đang thực hiện' },
  { key: 'COMPLETED', label: 'Hoàn thành' },
  { key: 'ALL', label: 'Tất cả' },
];

const GRID = 'grid grid-cols-[15rem_minmax(42rem,1fr)] md:grid-cols-[19rem_minmax(42rem,1fr)]';

function matchesFilter(plan: DispatchPlanRecord, filter: PlanFilter): boolean {
  switch (filter) {
    case 'CURRENT': return plan.plan_status === 'DRAFT' || plan.plan_status === 'APPROVED' || plan.plan_status === 'DISPATCHED';
    case 'ACTION': return plan.plan_status === 'DRAFT' || plan.plan_status === 'APPROVED';
    case 'ACTIVE': return plan.plan_status === 'DISPATCHED';
    case 'COMPLETED': return plan.plan_status === 'COMPLETED';
    default: return true;
  }
}

export function PlanApprovalPanel({
  plans, drivers, orders, routeColors, eligibleOrders, canExecute, isLoading, isTriggering,
  selectedPlanId, editingPlanId, initialFilter, onSelectPlan, onHighlightPlan, onClose, onRefresh, onTriggerPlanning,
  onApprovePlan, onDispatchPlan, onCancelPlan, onInsertOrderIntoPlan, onReorderStops, onMoveStop,
}: PlanApprovalPanelProps) {
  const { t } = useLocale();
  const [filter, setFilter] = useState<PlanFilter | null>(initialFilter ?? null);
  const [busyPlanId, setBusyPlanId] = useState<string | null>(null);
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 15_000);
    return () => window.clearInterval(timer);
  }, []);

  const ordersById = useMemo(() => new Map(orders.map((order) => [order.id, order])), [orders]);
  const counts = useMemo(() => ({
    CURRENT: plans.filter((p) => matchesFilter(p, 'CURRENT')).length,
    ACTION: plans.filter((p) => matchesFilter(p, 'ACTION')).length,
    ACTIVE: plans.filter((p) => matchesFilter(p, 'ACTIVE')).length,
    COMPLETED: plans.filter((p) => matchesFilter(p, 'COMPLETED')).length,
    ALL: plans.length,
  }), [plans]);
  const activeFilter = filter ?? (counts.CURRENT > 0 ? 'CURRENT' : 'ALL');
  const rows = useMemo(() => plans.filter((plan) => matchesFilter(plan, activeFilter))
    .sort((a, b) => Date.parse(b.created_at) - Date.parse(a.created_at))
    .map((plan) => {
      const timings = readPlanStopTimings(plan.route_waypoints);
      const stops = (plan.ordered_stop_ids ?? []).map((id) => {
        const order = ordersById.get(id);
        return {
          id, label: order?.order_code || id.slice(0, 8), address: order?.delivery_address,
          eta: timings.get(id), delivered: order?.status === 'DELIVERED'
        };
      });
      const completeData = stops.length === plan.order_count && stops.every((stop) => ordersById.has(stop.id));
      const startsAt = getPlanStartTime(plan);
      const departureMinute = startsAt === undefined ? undefined : clockMinutes(startsAt);
      const lastEta = stops.reduce<number | undefined>((latest, stop) =>
        stop.eta === undefined ? latest : Math.max(latest ?? 0, stop.eta), undefined);
      const duration = plan.estimated_duration_minutes ?? lastEta;
      return {
        plan,
        stops,
        departureMinute,
        day: startsAt === undefined ? undefined : dispatchDay(startsAt),
        arrivalMinute: departureMinute === undefined || duration === undefined ? undefined : departureMinute + duration,
        color: routeColors.get(plan.plan_id) ?? '#475569',
        delivered: completeData || plan.plan_status === 'DRAFT' ? stops.filter((stop) => stop.delivered).length : null,
      };
    }), [plans, activeFilter, ordersById, routeColors]);

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

  async function triggerPlanning() {
    onHighlightPlan(null);
    if (await onTriggerPlanning()) setFilter('CURRENT');
  }

  async function runAction(planId: string, action: () => Promise<unknown>) {
    setBusyPlanId(planId);
    try { await action(); } finally { setBusyPlanId(null); }
  }

  return (
    <div className="min-w-0 bg-white">
      <div className="flex flex-wrap items-center gap-x-2 px-3 py-1.5 sm:px-4">
        <h2 id="dispatch-tool-heading-plans" className="mr-auto text-base font-semibold text-slate-950">{t('Kế hoạch điều phối')}</h2>
        <Button variant="ghost" size="sm" className="size-10 px-0" onClick={onRefresh} isLoading={isLoading}
          aria-label={t('Làm mới kế hoạch')} title={t('Làm mới kế hoạch')}><RefreshCw size={15} aria-hidden="true" /></Button>
        {canExecute && (
          <Button size="sm" onClick={triggerPlanning} isLoading={isTriggering}>
            <Play size={14} aria-hidden="true" />{t('Chạy lập kế hoạch ngay')}
          </Button>
        )}
        <Button type="button" variant="ghost" size="sm" className="size-10 px-0" onClick={onClose}
          aria-label={t('Đóng kế hoạch')} title={t('Đóng kế hoạch')}><X size={17} aria-hidden="true" /></Button>
      </div>

      {plans.length > 0 && (
        <div role="group" aria-label={t('Lọc kế hoạch theo trạng thái')} className="flex gap-1 overflow-x-auto border-b border-slate-200 px-3 pb-2 sm:px-4">
          {FILTERS.map(({ key, label }) => (
            <button key={key} type="button" aria-pressed={activeFilter === key}
              className={`shrink-0 whitespace-nowrap rounded-md px-2 py-1.5 text-xs font-medium ${activeFilter === key ? 'bg-slate-900 text-white' : 'text-slate-700 hover:bg-slate-100'}`}
              onClick={() => { onHighlightPlan(null); setFilter(key); }}>{t(label)} <span className="tabular-nums">({counts[key]})</span></button>
          ))}
        </div>
      )}

      {rows.length === 0 ? (
        <p className="px-4 py-6 text-center text-sm text-slate-600" role="status">
          {t(isLoading ? 'Đang tải kế hoạch…' : plans.length ? 'Không có kế hoạch nào trong nhóm này.' : 'Chưa có kế hoạch điều phối nào.')}
        </p>
      ) : (
        <div className="max-h-[min(35dvh,20rem)] overflow-auto overscroll-contain" data-ui>
          <div className="min-w-[54.5rem] md:min-w-[60rem]">
            <div className={`${GRID} sticky top-0 z-20 items-stretch bg-slate-50 text-xs text-slate-600`}>
              <div className="sticky left-0 z-10 bg-slate-50 px-3 py-1 font-medium">
                {t('Tuyến')} <span className="ml-1 tabular-nums">({rows.length})</span>
                <p className="mt-0.5 text-xs font-normal">{t('Số đơn')} · ETA · {t('Đã giao')}</p>
              </div>
              <div className="min-w-0 px-4 py-1">
                <p className="text-center font-medium">{t('Giờ Việt Nam')}{nowPosition !== undefined ? ` · ${t('Bây giờ')} ${formatClockMinute(nowMinute)}` : ''}</p>
                <div className="relative h-5 tabular-nums">
                  {ticks.map(({ minute, position }) => (
                    <span key={minute} className={`absolute bottom-0 whitespace-nowrap ${position === 100 ? '-translate-x-full' : position > 0 ? '-translate-x-1/2' : ''}`}
                      style={{ left: `${position}%` }}>{formatClockMinute(minute)}</span>
                  ))}
                </div>
              </div>
            </div>

            {rows.map(({ plan, stops, color, delivered, departureMinute, arrivalMinute, day }) => {
              const id = plan.plan_id;
              const selected = selectedPlanId === id;
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
                  className={`${GRID} border-b border-slate-100 transition-colors hover:bg-slate-50/90 ${selected ? 'bg-slate-50' : 'bg-white'}`}>
                  <div className={`sticky left-0 z-10 border-r border-slate-100 px-3.5 py-2 ${selected ? 'bg-slate-50' : 'bg-white'}`}>
                    <span aria-hidden="true" className="absolute inset-y-2 left-0 w-1 rounded-r" style={{ backgroundColor: color }} />
                    <div className="flex min-w-0 items-center justify-between gap-2">
                      <button type="button" aria-pressed={selected} className="shrink-0 font-mono text-sm font-semibold text-slate-950 underline-offset-4 hover:underline"
                        onClick={() => onSelectPlan(selected ? null : id)}>#{id.slice(0, 8)}</button>
                      {draft && canExecute ? (
                        <DropdownMenu>
                          <DropdownMenuTrigger asChild>
                            <button
                              type="button"
                              disabled={busy || driverChoices.length === 0}
                              aria-label={`${t('Gán tài xế')} #${id.slice(0, 8)}`}
                              title={driverChoices.length === 0 ? t('Chưa có tài xế hoạt động') : t('Chọn tài xế để gán ngay')}
                              className="inline-flex h-7 shrink-0 items-center gap-1 rounded-full border border-slate-200 bg-white px-2 text-xs font-medium text-slate-700 shadow-2xs transition-colors hover:border-blue-300 hover:bg-blue-50 hover:text-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
                            >
                              <Plus size={13} aria-hidden="true" />
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
                      ) : plan.driver_id ? (
                        <span
                          className="inline-flex max-w-[9.5rem] items-center gap-1 truncate rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-700"
                          title={driver?.name ?? plan.driver_id}
                        >
                          <Truck size={11} className="shrink-0 text-slate-500" aria-hidden="true" />
                          <span className="truncate">{driver?.name ?? `#${plan.driver_id.slice(0, 8)}`}</span>
                        </span>
                      ) : null}
                    </div>
                    <div className="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs tabular-nums text-slate-600">
                      <span className="inline-flex items-center gap-1" title={t('Số đơn')} aria-label={`${t('Số đơn')}: ${plan.order_count}`}><Package size={12} aria-hidden="true" />{plan.order_count}</span>
                      <span className="inline-flex items-center gap-1 whitespace-nowrap" title="ETA" aria-label={`ETA: ${etaLabel}`}><Clock size={12} aria-hidden="true" />{etaLabel}</span>
                      <span className="inline-flex items-center gap-1 whitespace-nowrap" title={t('Đơn đã giao')} aria-label={`${t('Đơn đã giao')}: ${delivered ?? '—'}`}><Check size={13} aria-hidden="true" />{delivered ?? '—'}</span>
                      {plan.total_distance_km != null && (
                        <span className="whitespace-nowrap text-slate-500">{Number(plan.total_distance_km).toFixed(1)} km</span>
                      )}
                    </div>
                    {day && day !== today && <p className="mt-1 text-[11px] tabular-nums text-slate-500">{day}</p>}
                  </div>

                  <div className="relative min-w-0">
                    <div aria-hidden="true" className="pointer-events-none absolute inset-y-0 left-4 right-4">
                      {ticks.map(({ minute, position }) => <span key={minute} className="absolute inset-y-0 border-l border-slate-200/65" style={{ left: `${position}%` }} />)}
                      {nowPosition !== undefined && day === today && <span className="absolute inset-y-0 border-l border-sky-600" style={{ left: `${nowPosition}%` }} />}
                    </div>
                    <DispatchRouteSequence plan={plan} stops={stops} color={color} departureMinute={departureMinute}
                      axisStart={axisStart} axisMinutes={axisMinutes} onSelectRoute={() => onSelectPlan(id)}
                      onHighlightStop={(stopId) => onHighlightPlan({ planId: id, stopId })}
                      onReorder={canEdit ? (ids) => onReorderStops(plan, ids) : undefined}
                      acceptsDrops={canMove}
                      onMoveStop={canMove
                        ? (orderId, targetPlanId, targetIndex) => onMoveStop(plan, orderId, targetPlanId, targetIndex)
                        : undefined} />
                  </div>

                  {selected && (
                    <div className="col-span-2 flex flex-wrap items-center justify-between gap-2 border-t border-slate-200/80 bg-slate-50/80 px-3.5 py-2 text-xs text-slate-600">
                      <div className="flex flex-wrap items-center gap-2">
                        <PlanStatusBadge status={plan.plan_status} acknowledgedAt={plan.acknowledged_at} />
                        {plan.total_distance_km != null && <span className="font-medium tabular-nums text-slate-800">{Number(plan.total_distance_km).toFixed(1)} km</span>}
                        {plan.declined_at && <span className="text-rose-700">{t('Tài xế từ chối tuyến')}{plan.decline_reason && ` · ${plan.decline_reason}`}</span>}
                        {Boolean(plan.reassignment_count) && <span>{t('Số lần gán lại')} · {plan.reassignment_count}</span>}
                        {canExecute && stops.length > 0 && (
                          <span className="text-slate-500">{t(isDraftEditable
                            ? 'Giữ và kéo điểm giao để đổi thứ tự hoặc chuyển sang tuyến khác.'
                            : draft && !plan.driver_id
                              ? 'Hãy tải lại tuyến trước khi chỉnh sửa.'
                              : 'Chỉ sửa thứ tự trên tuyến nháp chưa gán tài xế.')}</span>
                        )}
                      </div>
                      <div className="flex flex-wrap items-center gap-2">
                        {canExecute && plan.plan_status === 'APPROVED' && (
                          <Button size="sm" disabled={busy} onClick={() => void runAction(id, () => onDispatchPlan(id))}>{t('Giao tuyến')}</Button>
                        )}
                        {canExecute && plan.plan_status === 'DISPATCHED' && eligibleOrders.length > 0 && (
                          <DropdownMenu>
                            <DropdownMenuTrigger asChild>
                              <Button size="sm" variant="outline" disabled={busy}>
                                <UserPlus size={13} aria-hidden="true" />
                                {t('Chèn đơn')} ({eligibleOrders.length})
                              </Button>
                            </DropdownMenuTrigger>
                            <DropdownMenuContent align="end">
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
                        {canExecute && plan.plan_status !== 'COMPLETED' && (
                          <Button size="sm" variant="ghost" className="text-rose-700 hover:bg-rose-50 hover:text-rose-800" disabled={busy}
                            onClick={() => void runAction(id, () => onCancelPlan(id, 'Điều phối viên hủy kế hoạch để lập lại lộ trình'))}>{t('Hủy tuyến')}</Button>
                        )}
                      </div>
                    </div>
                  )}
                </section>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}
