import { getPlanStartTime, readPlanStopTimings } from '@/application/dispatch-plan-clock';
import type { Order } from '@/domain/entities/order.entity';
import type { DispatchPlanRecord } from '@/domain/entities/routing.entity';

const minute = 60_000;

export function getWarehouseBacklog(orders: readonly Order[], now: number) {
  const groups = new Map<string, { key: string; name?: string; counts: [number, number, number, number]; oldest?: number; total: number }>();
  for (const order of orders) {
    const key = order.pickup_location_id || order.pickup_location_name || order.pickup_address || '';
    const name = order.pickup_location_name || order.pickup_address;
    const group = groups.get(key) ?? { key, name, counts: [0, 0, 0, 0], total: 0 };
    const age = Math.floor((now - Date.parse(order.created_at)) / minute);
    const bucket = !Number.isFinite(age) ? 3 : age < 15 ? 0 : age < 45 ? 1 : 2;
    group.counts[bucket]++;
    group.total++;
    if (Number.isFinite(age)) group.oldest = Math.max(group.oldest ?? 0, age);
    groups.set(key, group);
  }
  return [...groups.values()].sort((a, b) => (b.oldest ?? -1) - (a.oldest ?? -1) || b.total - a.total);
}

export function getAppointmentSlack(orders: readonly Order[], plans: readonly DispatchPlanRecord[], now: number) {
  const schedule = new Map<string, { planId: string; eta?: number }>();
  for (const plan of plans) {
    if (plan.plan_status === 'COMPLETED') continue;
    const start = getPlanStartTime(plan, now);
    const timings = readPlanStopTimings(plan.route_waypoints);
    for (const id of plan.ordered_stop_ids ?? []) {
      const timing = timings.get(id);
      schedule.set(id, {
        planId: plan.plan_id,
        eta: start === undefined || !timing ? undefined : start + timing.eta * minute,
      });
    }
  }

  let withoutEta = 0;
  const points = [];
  for (const order of orders) {
    const start = Date.parse(order.time_window_start ?? '');
    const end = Date.parse(order.time_window_end ?? '');
    if (!Number.isFinite(start) || !Number.isFinite(end) || end <= start) continue;
    const stop = schedule.get(order.id);
    if (stop?.eta === undefined) {
      withoutEta++;
      continue;
    }
    const buffer = end - Math.max(now, stop.eta);
    const minutes = Math.floor(buffer / minute);
    points.push({ id: order.id, label: order.order_code || order.id.slice(0, 8), planId: stop.planId, minutes });
  }
  points.sort((a, b) => a.minutes - b.minutes);
  return { points, withoutEta, late: points.filter((point) => point.minutes < 0).length };
}

export function getRouteStages(plans: readonly DispatchPlanRecord[], now: number) {
  const stages: Array<{ count: number; oldest?: number; planId?: string }> = Array.from({ length: 3 }, () => ({ count: 0 }));
  for (const plan of plans) {
    if (plan.plan_status === 'COMPLETED') continue;
    const stage = plan.plan_status === 'DISPATCHED' ? 2 : plan.driver_id ? 1 : 0;
    const entry = stages[stage];
    entry.count++;
    const started = stage === 1 ? plan.updated_at ?? plan.created_at : plan.created_at;
    if (stage === 2 || !started) continue;
    const age = Math.max(0, Math.floor((now - Date.parse(started)) / minute));
    if (Number.isFinite(age) && (entry.oldest === undefined || age > entry.oldest)) {
      entry.oldest = age;
      entry.planId = plan.plan_id;
    }
  }
  return stages;
}
