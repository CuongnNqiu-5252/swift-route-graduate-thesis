'use client';

import { useEffect, useState, type ReactElement } from 'react';
import { OrderDetailModal } from '@/components/orders/order-detail-modal';
import { Button } from '@/components/ui/button';
import { Modal } from '@/components/ui/modal';
import { useLocale } from '@/i18n/provider';
import {
  deliveryReportRepository,
  type DeliveryReportDetailFilter,
  type DeliveryReportFilters,
} from '@/infrastructure/repositories/delivery-report.repository';

const zone = 'Asia/Ho_Chi_Minh';

export function DeliveryReportDetails({ title, filters, detail, trigger }: {
  title: string;
  filters: DeliveryReportFilters;
  detail: DeliveryReportDetailFilter;
  trigger: ReactElement;
}) {
  const [open, setOpen] = useState(false);

  return (
    <Modal open={open} onOpenChange={setOpen} title={title} trigger={trigger}
      className="max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-2xl overflow-y-auto">
      {open && <DetailsList filters={filters} detail={detail} />}
    </Modal>
  );
}

function DetailsList({ filters, detail }: { filters: DeliveryReportFilters; detail: DeliveryReportDetailFilter }) {
  const { t, intlLocale } = useLocale();
  const [page, setPage] = useState(0);
  const [revision, setRevision] = useState(0);
  const [result, setResult] = useState<{
    page: number;
    revision: number;
    data?: Awaited<ReturnType<typeof deliveryReportRepository.details>>;
  }>();
  const { from, to, warehouse } = filters;
  const { kind } = detail;
  const reason = kind === 'FAILURE' ? detail.reason : undefined;

  useEffect(() => {
    const controller = new AbortController();
    const selection = kind === 'FAILURE' ? { kind, reason: reason ?? '' } : { kind };
    deliveryReportRepository.details({ from, to, warehouse }, selection, page, controller.signal).then(
      (data) => { if (!controller.signal.aborted) setResult({ page, revision, data }); },
      () => { if (!controller.signal.aborted) setResult({ page, revision }); },
    );
    return () => controller.abort();
  }, [from, to, warehouse, kind, reason, page, revision]);

  const current = result?.page === page && result.revision === revision ? result : undefined;
  const dateTime = new Intl.DateTimeFormat(intlLocale, { dateStyle: 'short', timeStyle: 'short', timeZone: zone });

  if (!current) return <p role="status" className="text-sm text-slate-600">{t('Đang tải vận đơn…')}</p>;
  if (!current.data) {
    return (
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p role="alert" className="text-sm text-slate-700">{t('Không tải được danh sách vận đơn.')}</p>
        <Button variant="outline" onClick={() => setRevision((value) => value + 1)}>{t('Thử lại')}</Button>
      </div>
    );
  }

  const { content, total_pages: totalPages } = current.data;
  if (!content.length && page === 0) {
    return <p className="text-sm text-slate-600">{t('Không có vận đơn trong mục này.')}</p>;
  }

  return (
    <div className="space-y-4">
      <ul className="divide-y divide-slate-200">
        {content.map((row) => (
          <li key={`${row.order_id}-${row.attempt_number}`} className="flex flex-wrap items-start justify-between gap-x-4 gap-y-1 py-3">
            <OrderDetailModal orderId={row.order_id} orderCode={row.order_code} shortCode={row.order_code} />
            <div className="text-sm tabular-nums text-slate-900">
              <time dateTime={row.completed_at}>{dateTime.format(new Date(row.completed_at))}</time>
              <p className="mt-1 text-xs text-slate-600">
                {kind === 'LATE' && row.deadline
                  ? <>{t('Hạn giao')} · {dateTime.format(new Date(row.deadline))}</>
                  : <>{t('Lần giao')} {row.attempt_number}</>}
              </p>
            </div>
          </li>
        ))}
      </ul>
      {(totalPages > 1 || page > 0) && (
        <nav aria-label={t('Phân trang vận đơn')} className="flex flex-wrap items-center justify-between gap-2 border-t border-slate-200 pt-3">
          <Button variant="outline" disabled={page === 0} onClick={() => setPage(page - 1)}>{t('Trang trước')}</Button>
          <span className="text-sm tabular-nums text-slate-600">{page + 1} / {Math.max(totalPages, 1)}</span>
          <Button variant="outline" disabled={page + 1 >= totalPages} onClick={() => setPage(page + 1)}>{t('Trang sau')}</Button>
        </nav>
      )}
    </div>
  );
}
