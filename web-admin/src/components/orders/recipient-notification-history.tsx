'use client';

import { useEffect, useState } from 'react';
import { Button } from '@/components/ui/button';
import { useLocale } from '@/i18n/provider';
import { orderRepository, type RecipientNotification } from '@/infrastructure/repositories/order.repository';

const titles = {
  STARTED: 'Đang giao',
  FAILED: 'Giao chưa thành công',
  COMPLETED: 'Đã giao',
};

const statuses = {
  PENDING: { label: 'Đang chờ xử lý', color: 'bg-slate-100 text-slate-700' },
  SIMULATED: { label: 'Đã giả lập gửi', color: 'bg-teal-50 text-teal-800' },
  SKIPPED: { label: 'Đã bỏ qua', color: 'bg-slate-100 text-slate-700' },
  FAILED: { label: 'Xử lý lỗi', color: 'bg-rose-50 text-rose-800' },
  PROCESSED: { label: 'Đã xử lý', color: 'bg-slate-100 text-slate-700' },
};

export function RecipientNotificationHistory({ orderId }: { orderId: string }) {
  const { t, intlLocale } = useLocale();
  const [revision, setRevision] = useState(0);
  const [result, setResult] = useState<{ revision: number; items?: RecipientNotification[] }>();

  useEffect(() => {
    const controller = new AbortController();
    orderRepository.getRecipientNotifications(orderId, controller.signal).then(
      (items) => { if (!controller.signal.aborted) setResult({ revision, items }); },
      () => { if (!controller.signal.aborted) setResult({ revision }); },
    );
    return () => controller.abort();
  }, [orderId, revision]);

  const current = result?.revision === revision ? result : undefined;
  const formatter = new Intl.DateTimeFormat(intlLocale, {
    dateStyle: 'short',
    timeStyle: 'short',
    timeZone: 'Asia/Ho_Chi_Minh',
  });

  return (
    <section aria-label={t('Thông báo người nhận')} className="space-y-3 rounded-xl border border-slate-200 p-3.5" data-ui>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-xs text-slate-600">{t('Kênh giả lập · Người nhận chưa nhận SMS.')}</p>
        <Button type="button" variant="outline" size="sm" disabled={!current}
          onClick={() => setRevision((value) => value + 1)}>
          {t(current && !current.items ? 'Thử lại' : 'Làm mới')}
        </Button>
      </div>
      {!current ? (
        <p role="status" className="min-h-14 border-t border-slate-100 pt-3 text-sm text-slate-600">
          {t('Đang tải lịch sử thông báo…')}
        </p>
      ) : !current.items ? (
        <p role="alert" className="border-t border-slate-100 pt-3 text-sm text-slate-700">
          {t('Không tải được lịch sử thông báo.')}
        </p>
      ) : current.items.length === 0 ? (
        <p className="border-t border-slate-100 pt-3 text-sm text-slate-600">
          {t('Chưa có thông báo cho người nhận của đơn này.')}
        </p>
      ) : (
        <ul className="divide-y divide-slate-100 border-t border-slate-100">
          {current.items.map((item) => {
            const status = statuses[item.status] ?? statuses.PROCESSED;
            return (
              <li key={item.event_id} className="flex flex-wrap items-start justify-between gap-x-3 gap-y-1.5 py-3">
                <div className="min-w-0">
                  <p className="text-sm font-medium text-slate-900">{t(titles[item.milestone])}</p>
                  <p className="mt-1 text-xs text-slate-600">
                    <time dateTime={item.occurred_at}>{formatter.format(new Date(item.occurred_at))}</time>
                    {item.attempt_number != null && <> · {t('Lần giao')} {item.attempt_number}</>}
                  </p>
                </div>
                <span className={`rounded px-2 py-1 text-xs font-medium ${status.color}`}>{t(status.label)}</span>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}
