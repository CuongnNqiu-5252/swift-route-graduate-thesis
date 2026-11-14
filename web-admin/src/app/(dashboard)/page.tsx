'use client';

import Link from 'next/link';
import { ArrowRight, RefreshCw, Route } from 'lucide-react';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { useAuth } from '@/application/contexts/auth-context';
import { useDrivers } from '@/application/hooks/use-drivers';
import { useOperationsQueue } from '@/application/hooks/use-operations-queue';
import { Button } from '@/components/ui/button';
import { useLocale } from '@/i18n/provider';
import type { OperationsQueue } from '@/infrastructure/repositories/order.repository';

const groups = [
  { key: 'ready_for_dispatch', label: 'Chờ điều phối' },
  { key: 'active', label: 'Đang thực hiện' },
  { key: 'intervention', label: 'Cần can thiệp' },
] as const satisfies readonly { key: keyof OperationsQueue; label: string }[];

export default function DashboardOverviewPage() {
  const { user } = useAuth();
  const { t, intlLocale } = useLocale();
  const { queue, hasLoaded, isLoading, error, refetch } = useOperationsQueue();
  const { drivers, isLoading: driversLoading, error: driversError, refetch: refetchDrivers } = useDrivers();

  const number = new Intl.NumberFormat(intlLocale);
  const chartData = groups.map(({ key, label }) => ({ label: t(label), count: queue[key].length }));
  const canLoadQueue = Boolean(user?.tenantId);
  const showQueue = canLoadQueue && !error && hasLoaded;

  return (
    <div className="mx-auto max-w-7xl space-y-6">
      <header className="flex flex-wrap items-end justify-between gap-4 border-b border-slate-200 pb-5">
        <div>
          <h1 className="text-2xl font-semibold text-slate-950">{t('Tổng quan vận hành')}</h1>
        </div>
        <div className="flex flex-wrap gap-2">
          {canLoadQueue && (
            <Button variant="outline" onClick={() => void Promise.all([refetch(), refetchDrivers()])} isLoading={isLoading || driversLoading}>
              <RefreshCw size={15} aria-hidden="true" />{t('Làm mới dữ liệu')}
            </Button>
          )}
          <Link href={canLoadQueue ? '/dispatch' : '/organizations'} className="inline-flex min-h-10 items-center gap-2 rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600">
            <Route size={16} aria-hidden="true" />{t(canLoadQueue ? 'Mở điều phối' : 'Chọn doanh nghiệp')}
          </Link>
        </div>
      </header>

      {canLoadQueue ? (
        <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_15rem]">
          <section aria-labelledby="queue-chart-heading" className="min-w-0 rounded-xl border border-slate-200 p-4 sm:p-5">
            <h2 id="queue-chart-heading" className="font-semibold text-slate-950">{t('Số đơn theo nhóm vận hành')}</h2>
            {error ? (
              <p className="mt-5 text-sm text-rose-700" role="alert">{t('Không thể tải hàng đợi vận hành')}</p>
            ) : showQueue ? (
              <>
                <dl className="mt-4 grid grid-cols-3 gap-2 border-b border-slate-200 pb-4 sm:gap-4">
                  {chartData.map(({ label, count }) => (
                    <div key={label} className="min-w-0">
                      <dt className="text-xs font-medium text-slate-600 sm:text-sm">{label}</dt>
                      <dd className="mt-1 text-2xl font-semibold tabular-nums text-slate-950">{number.format(count)}</dd>
                    </div>
                  ))}
                </dl>
                {chartData.some(({ count }) => count > 0) && (
                  <div className="mt-4 h-56 min-w-0">
                    <ResponsiveContainer width="100%" height="100%" minWidth={0} initialDimension={{ width: 320, height: 208 }}>
                      <BarChart data={chartData} layout="vertical" margin={{ top: 6, right: 12, bottom: 0, left: 0 }}>
                        <CartesianGrid horizontal={false} stroke="#e2e8f0" />
                        <YAxis type="category" dataKey="label" width={110} tickLine={false} axisLine={false} tick={{ fill: '#475569', fontSize: 11 }} />
                        <XAxis type="number" allowDecimals={false} tickLine={false} axisLine={false} tick={{ fill: '#475569', fontSize: 12 }} tickFormatter={(value: number) => number.format(value)} />
                        <Tooltip cursor={{ fill: '#eff6ff' }} formatter={(value) => [number.format(Number(value)), t('Số đơn')]} />
                        <Bar dataKey="count" fill="#2563eb" radius={[0, 4, 4, 0]} maxBarSize={26} isAnimationActive="auto" animationDuration={400} />
                      </BarChart>
                    </ResponsiveContainer>
                  </div>
                )}
              </>
            ) : (
              <p className="mt-5 text-sm text-slate-600" role="status">{t('Đang tải hàng đợi vận hành…')}</p>
            )}
          </section>

          <section aria-labelledby="driver-count-heading" className="flex flex-col justify-between gap-5 rounded-xl border border-slate-200 bg-slate-50 p-4 sm:p-5">
            <div>
              <h2 id="driver-count-heading" className="text-sm font-medium text-slate-700">{t('Hồ sơ tài xế')}</h2>
              {driversError ? (
                <p className="mt-2 text-sm text-rose-700" role="status">{t('Không thể tải tài xế')}</p>
              ) : (
                <p className="mt-2 text-3xl font-semibold tabular-nums text-slate-950" aria-live="polite">
                  {driversLoading ? '—' : number.format(drivers.length)}
                </p>
              )}
            </div>
            <Link href="/drivers" className="inline-flex w-fit items-center gap-1.5 text-sm font-medium text-blue-700 hover:underline">
              {t('Xem danh sách tài xế')}<ArrowRight size={15} aria-hidden="true" />
            </Link>
          </section>
        </div>
      ) : (
        <p className="rounded-xl border border-slate-200 p-5 text-sm text-slate-600">{t('Chọn doanh nghiệp để xem dữ liệu vận hành.')}</p>
      )}
    </div>
  );
}
