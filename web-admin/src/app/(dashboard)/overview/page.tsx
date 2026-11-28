'use client';

import { useAuth } from '@/application/contexts/auth-context';
import { useDrivers } from '@/application/hooks/use-drivers';
import { useOperationsQueue } from '@/application/hooks/use-operations-queue';
import { OverviewDispatchMetrics } from '@/components/overview/dispatch-metrics';
import { Button } from '@/components/ui/button';
import type { DispatchPlanRecord } from '@/domain/entities/routing.entity';
import { useLocale } from '@/i18n/provider';
import { orderRepository } from '@/infrastructure/repositories/order.repository';
import { ArrowRight, RefreshCw, Route } from 'lucide-react';
import Link from 'next/link';
import { useEffect, useState } from 'react';

const textLink = 'rounded-sm text-sm font-medium text-blue-700 hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-700';
const panel = 'min-w-0 rounded-xl border border-slate-200 p-4 sm:p-6';

export default function DashboardOverviewPage() {
  const { user, hasPermission } = useAuth();
  const { t, intlLocale } = useLocale();
  const { queue, hasLoaded, isLoading, error, refetch } = useOperationsQueue();
  const { drivers, isLoading: driversLoading, error: driversError, refetch: refetchDrivers } = useDrivers();

  const number = new Intl.NumberFormat(intlLocale);
  const queueGroups = [
    { label: 'Chờ điều phối', count: queue.ready_for_dispatch.length },
    { label: 'Đã lên lịch hoặc đang giao', count: queue.active.length },
    { label: 'Cần can thiệp', count: queue.intervention.length },
  ];
  const queueTotal = queueGroups.reduce((total, group) => total + group.count, 0);
  const needsAttention = queue.intervention.length;

  const available = drivers.filter((driver) => driver.status === 'AVAILABLE').length;
  const awaitingApproval = drivers.filter((driver) => driver.onboarding_status === 'PENDING_APPROVAL').length;
  const availableShare = drivers.length ? available / drivers.length : 0;
  const failedCount = queue.intervention.filter((order) => order.status === 'FAILED').length;
  const canLoadQueue = Boolean(user?.tenantId);
  const showQueue = hasLoaded && !error;
  const showDrivers = !driversLoading && !driversError;

  const tenantId = user?.tenantId;
  const canReadPlans = Boolean(tenantId) && hasPermission(['dispatch:read', 'dispatch:execute']);
  const [plansResult, setPlansResult] = useState<{ tenantId: typeof tenantId; plans: DispatchPlanRecord[]; error: boolean }>();
  const [revision, setRevision] = useState(0);
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const update = () => {
      setNow(Date.now());
      setRevision((value) => value + 1);
    };
    const timer = window.setInterval(() => setNow(Date.now()), 60_000);
    window.addEventListener('swiftroute:realtime-update', update);
    return () => {
      window.clearInterval(timer);
      window.removeEventListener('swiftroute:realtime-update', update);
    };
  }, []);
  useEffect(() => {
    if (!canReadPlans) return;
    let active = true;
    orderRepository.getDispatchPlans()
      .then((plans) => { if (active) setPlansResult({ tenantId, plans, error: false }); })
      .catch(() => { if (active) setPlansResult({ tenantId, plans: [], error: true }); });
    return () => { active = false; };
  }, [canReadPlans, tenantId, revision]);
  const currentPlans = plansResult?.tenantId === tenantId ? plansResult : undefined;
  const planState = !canReadPlans ? 'restricted' : !currentPlans ? 'loading' : currentPlans.error ? 'error' : 'ready';

  return (
    <div className="mx-auto max-w-7xl space-y-6">
      <header className="flex flex-wrap items-center justify-between gap-4">
        <h1 className="text-2xl font-semibold tracking-tight text-slate-950">{t('Tổng quan vận hành')}</h1>
        <div className="flex flex-wrap gap-2">
          {canLoadQueue && (
            <Button variant="outline" onClick={() => {
              setNow(Date.now());
              setRevision((value) => value + 1);
              void Promise.all([refetch(), refetchDrivers()]);
            }} isLoading={isLoading || driversLoading}>
              <RefreshCw size={15} aria-hidden="true" />{t('Làm mới dữ liệu')}
            </Button>
          )}
          {(!canLoadQueue || canReadPlans) && (
            <Link href={canLoadQueue ? '/dispatch' : '/organizations'} className="inline-flex min-h-10 items-center gap-2 rounded-sm bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600">
              <Route size={16} aria-hidden="true" />{t(canLoadQueue ? 'Mở điều phối' : 'Chọn doanh nghiệp')}
            </Link>
          )}
        </div>
      </header>

      {canLoadQueue ? (
        <div className="grid gap-5 xl:grid-cols-2">
          <dl className="grid grid-cols-2 gap-px overflow-hidden rounded-xl border border-slate-200 bg-slate-200 sm:grid-cols-4 xl:col-span-2">
            {[{ label: 'Đơn đang vận hành', count: queueTotal }, ...queueGroups].map(({ label, count }) => (
              <div key={label} className="min-w-0 bg-slate-50 px-4 py-3">
                <dt className="text-xs font-medium text-slate-600">{t(label)}</dt>
                <dd className="mt-1 text-xl font-semibold tabular-nums text-slate-950">{showQueue ? number.format(count) : '—'}</dd>
              </div>
            ))}
          </dl>
          <OverviewDispatchMetrics
            queue={queue}
            queueState={showQueue ? 'ready' : error ? 'error' : 'loading'}
            plans={currentPlans?.plans ?? []}
            planState={planState}
            now={now}
          />

          <section aria-labelledby="exceptions-heading" className={panel}>
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h2 id="exceptions-heading" className="text-base font-semibold text-slate-950">{t('Đơn cần xử lý')}</h2>
              {showQueue && needsAttention > 0 && (
                <div className="flex flex-wrap gap-x-4 gap-y-2">
                  <Link href="/orders?status=FAILED" className={textLink}>{t('Sự cố')} ({number.format(failedCount)})</Link>
                  <Link href="/orders?status=RETURNING" className={textLink}>{t('Đang hoàn kho')} ({number.format(needsAttention - failedCount)})</Link>
                </div>
              )}
            </div>
            {showQueue && needsAttention > 0 ? (
              <ul className="mt-3 divide-y divide-slate-100">
                {queue.intervention.slice(0, 4).map((order) => {
                  const failed = order.status === 'FAILED';
                  return (
                    <li key={order.id} className="grid gap-x-4 gap-y-1 py-3 text-sm sm:grid-cols-[minmax(7rem,auto)_minmax(0,1fr)_auto] sm:items-center">
                      <span className="font-medium break-all text-slate-950">{order.order_code || order.id.slice(0, 8)}</span>
                      <span className="break-words text-slate-600">{order.delivery_address}</span>
                      <span className="font-medium text-amber-800">{t(failed ? 'Sự cố' : 'Đang hoàn kho')}</span>
                    </li>
                  );
                })}
              </ul>
            ) : (
              <p className="mt-3 text-sm text-slate-600" role="status">
                {t(error ? 'Chưa có dữ liệu đơn cần xử lý.' : showQueue ? 'Hiện không có đơn cần xử lý.' : 'Đang tải hàng đợi vận hành…')}
              </p>
            )}
          </section>
          <section aria-labelledby="fleet-heading" className={`${panel} xl:col-span-2`}>
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h2 id="fleet-heading" className="text-base font-semibold text-slate-950">{t('Đội xe')}</h2>
              <Link href="/drivers" className={textLink}>{t('Xem tài xế')} <ArrowRight size={14} className="inline" aria-hidden="true" /></Link>
            </div>
            {showDrivers ? (
              <div className="mt-5">
                <p className="text-sm text-slate-600">{t('Sẵn sàng nhận đơn')}</p>
                <p className="mt-1 flex flex-wrap items-baseline gap-x-2 tabular-nums">
                  <span className="text-3xl font-semibold leading-none text-slate-950">{number.format(available)}</span>
                  <span className="text-sm text-slate-600">/ {number.format(drivers.length)} {t('tài xế')}</span>
                </p>
                <div className="mt-3 h-2 overflow-hidden rounded-full bg-slate-100" aria-hidden="true">
                  <div className="h-full rounded-full bg-blue-600" style={{ width: `${availableShare * 100}%` }} />
                </div>
                <dl className="mt-4 divide-y divide-slate-100 text-sm text-slate-700">
                  <div className="flex justify-between gap-3 py-2">
                    <dt>{t('Chờ duyệt')}</dt><dd className="tabular-nums">{number.format(awaitingApproval)}</dd>
                  </div>
                  <div className="flex justify-between gap-3 py-2">
                    <dt>{t('Chưa thể nhận đơn khác')}</dt><dd className="tabular-nums">{number.format(drivers.length - available - awaitingApproval)}</dd>
                  </div>
                </dl>
              </div>
            ) : (
              <p className="mt-5 text-sm text-slate-600" role={driversError ? 'alert' : 'status'}>
                {t(driversError ? 'Không thể tải tài xế' : 'Đang tải tài xế…')}
              </p>
            )}
          </section>

        </div>
      ) : (
        <p className={`${panel} text-sm text-slate-600`}>{t('Chọn doanh nghiệp để xem dữ liệu vận hành.')}</p>
      )}
    </div>
  );
}
