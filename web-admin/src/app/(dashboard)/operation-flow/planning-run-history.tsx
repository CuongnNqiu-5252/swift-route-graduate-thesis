'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { Button } from '@/components/ui/button';
import type { PickupLocation } from '@/domain/entities/pickup-location.entity';
import { useLocale } from '@/i18n/provider';
import { operationFlowRepository, type PlanningRunPage } from '@/infrastructure/repositories/operation-flow.repository';

const outcomeLabels = {
  PLANNED: 'Đã tạo tuyến',
  NO_ORDERS: 'Không có đơn đủ điều kiện',
  NO_DRIVERS: 'Không có tài xế khả dụng',
  PICKUP_UNAVAILABLE: 'Điểm lấy hàng không còn hoạt động',
  NO_ROUTES: 'Không tạo được tuyến',
  FAILED: 'Lỗi khi lập kế hoạch',
} as const;

export function PlanningRunHistory({ pickups }: { pickups: PickupLocation[] }) {
  const { t, intlLocale } = useLocale();
  const [page, setPage] = useState(0);
  const [request, setRequest] = useState(0);
  const [result, setResult] = useState<{ key: string; data?: PlanningRunPage }>();
  const key = `${page}:${request}`;
  const data = result?.key === key ? result.data : undefined;
  const loading = result?.key !== key;
  const dateFormat = new Intl.DateTimeFormat(intlLocale, {
    timeZone: 'Asia/Ho_Chi_Minh', dateStyle: 'short', timeStyle: 'short',
  });

  useEffect(() => {
    const controller = new AbortController();
    operationFlowRepository.getPlanningRuns(page, controller.signal).then(
      (data) => { if (!controller.signal.aborted) setResult({ key, data }); },
      () => { if (!controller.signal.aborted) setResult({ key }); },
    );
    return () => controller.abort();
  }, [key, page]);

  return (
    <section className="overflow-hidden rounded-xl border border-slate-200 bg-white" aria-label={t('Lịch sử chạy')}>
      <header className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 px-4 py-3">
        <h2 className="text-sm font-semibold text-slate-900">{t('Lịch sử chạy')}</h2>
        <Button size="sm" variant="outline" disabled={loading}
          onClick={() => { setPage(0); setRequest((current) => current + 1); }}>{t('Làm mới')}</Button>
      </header>
      {loading ? <p role="status" className="p-4 text-sm text-slate-600">{t('Đang tải lịch sử chạy…')}</p>
        : !data ? <p role="alert" className="p-4 text-sm text-rose-700">{t('Không thể tải lịch sử chạy.')}</p>
          : data.content.length === 0 ? <p className="p-4 text-sm text-slate-600">{t('Chưa có lần chạy nào.')}</p>
            : <div className="overflow-x-auto">
                <table className="w-full min-w-[36rem] text-left text-sm">
                  <thead className="border-b border-slate-200 text-xs text-slate-600">
                    <tr>
                      <th className="px-4 py-2 font-medium">{t('Thời điểm chạy')} (UTC+7)</th>
                      <th className="px-4 py-2 font-medium">{t('Điểm lấy hàng')}</th>
                      <th className="px-4 py-2 font-medium">{t('Kết quả')}</th>
                      <th className="px-4 py-2 font-medium">{t('Tuyến đã tạo')}</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {data.content.map((run) => (
                      <tr key={run.id}>
                        <td className="px-4 py-3 align-top">
                          {dateFormat.format(new Date(run.started_at))}
                          <p className="text-xs text-slate-600">{t(run.source === 'MANUAL' ? 'Chạy thủ công' : 'Chạy theo lịch')}</p>
                        </td>
                        <td className="px-4 py-3 align-top">{run.pickup_location_id
                          ? pickups.find((pickup) => pickup.id === run.pickup_location_id)?.name ?? t('Điểm lấy hàng đã cấu hình')
                          : t('Toàn doanh nghiệp')}</td>
                        <td className="px-4 py-3 align-top">
                          {t(outcomeLabels[run.outcome])}
                          {run.eligible_orders > 0 && <p className="text-xs text-slate-600">{run.eligible_orders} {t('đơn đủ điều kiện')}</p>}
                        </td>
                        <td className="px-4 py-3 align-top">
                          <div className="flex flex-wrap gap-2">
                            {run.plan_ids.length ? run.plan_ids.map((id) => <Link key={id}
                              className="font-medium text-blue-700 underline underline-offset-2"
                              href={`/dispatch?plan=${encodeURIComponent(id)}`}>
                              {t('Tuyến')} #{id.slice(0, 8)}
                            </Link>) : '—'}
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>}
      {(page > 0 || data?.has_more) && <div className="flex justify-end gap-2 border-t border-slate-200 p-3">
        <Button size="sm" variant="outline" disabled={page === 0 || loading} onClick={() => setPage(page - 1)}>{t('Trang trước')}</Button>
        <Button size="sm" variant="outline" disabled={!data?.has_more || loading} onClick={() => setPage(page + 1)}>{t('Trang sau')}</Button>
      </div>}
    </section>
  );
}
