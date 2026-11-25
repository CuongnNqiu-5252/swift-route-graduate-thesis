import type { DispatchPlanRecord } from '@/domain/entities/routing.entity';

const zone = 'Asia/Ho_Chi_Minh';
const clock = new Intl.DateTimeFormat('en-GB', { timeZone: zone, hour: '2-digit', minute: '2-digit', hourCycle: 'h23' });
const day = new Intl.DateTimeFormat('en-GB', { timeZone: zone, day: '2-digit', month: '2-digit', year: 'numeric' });

export function getPlanStartTime(plan: DispatchPlanRecord, nowMs?: number): number | undefined {
  const dispatchedMs = plan.dispatched_at ? Date.parse(plan.dispatched_at) : NaN;
  if (Number.isFinite(dispatchedMs)) return dispatchedMs;

  const createdMs = plan.created_at ? Date.parse(plan.created_at) : NaN;
  if (!Number.isFinite(createdMs)) return undefined;

  if (plan.plan_status === 'DRAFT' || plan.plan_status === 'APPROVED') {
    const referenceMs = nowMs !== undefined && Number.isFinite(nowMs) ? nowMs : Date.now();
    return Math.max(createdMs, referenceMs);
  }
  return createdMs;
}

export function clockMinutes(ms: number): number {
  const [hours, minutes] = clock.format(ms).split(':').map(Number);
  return hours * 60 + minutes;
}

export function dispatchDay(ms: number): string {
  return day.format(ms);
}

export function formatClockMinute(totalMinutes: number): string {
  const minutes = Math.round(totalMinutes);
  const wrapped = minutes % 1440;
  const time = `${String(Math.floor(wrapped / 60)).padStart(2, '0')}:${String(wrapped % 60).padStart(2, '0')}`;
  return minutes >= 1440 ? `${time} (+${Math.floor(minutes / 1440)})` : time;
}

export function readPlanStopTimings(raw?: string | null): Map<string, { eta: number; lateBy?: number }> {
  const timings = new Map<string, { eta: number; lateBy?: number }>();
  if (!raw) return timings;
  try {
    const points: unknown = JSON.parse(raw);
    if (!Array.isArray(points)) return timings;
    let previousEta = 0;
    for (const point of points) {
      const eta: unknown = point?.estimated_arrival_minutes;
      if (typeof point?.stop_id === 'string' && typeof eta === 'number' && Number.isFinite(eta) && eta >= previousEta) {
        const lateBy = point.late_by_minutes;
        timings.set(point.stop_id, { eta,
          lateBy: typeof lateBy === 'number' && Number.isFinite(lateBy) && lateBy >= 0 ? lateBy : undefined });
        previousEta = eta;
      }
    }
  } catch {
    // A legacy snapshot can omit usable times. Those stops are displayed in sequence below the axis.
  }
  return timings;
}
