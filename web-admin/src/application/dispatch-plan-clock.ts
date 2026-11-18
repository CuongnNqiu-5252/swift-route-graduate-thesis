import type { DispatchPlanRecord } from '@/domain/entities/routing.entity';

const zone = 'Asia/Ho_Chi_Minh';
const clock = new Intl.DateTimeFormat('en-GB', { timeZone: zone, hour: '2-digit', minute: '2-digit', hourCycle: 'h23' });
const day = new Intl.DateTimeFormat('en-GB', { timeZone: zone, day: '2-digit', month: '2-digit', year: 'numeric' });

export function getPlanStartTime(plan: DispatchPlanRecord): number | undefined {
  for (const value of [plan.dispatched_at, plan.planned_at, plan.created_at]) {
    const ms = value ? Date.parse(value) : NaN;
    if (Number.isFinite(ms)) return ms;
  }
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
