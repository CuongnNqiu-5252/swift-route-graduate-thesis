'use client';

import { AlertCircle, Check, CircleAlert, ClipboardList, MapPin, Search } from 'lucide-react';
import { Fragment, useState, type ReactNode } from 'react';
import { Button } from '@/components/ui/button';
import { ORDER_PRIORITY_BADGE, ORDER_PRIORITY_LABEL, type OrderPriority } from '@/domain/entities/order.entity';
import { useLocale } from '@/i18n/provider';
import type { ImportResult, ImportRow } from '@/infrastructure/repositories/order-import.repository';

type ReviewStatus = ImportResult['status'] | 'COORDINATES' | 'GEOCODED' | 'CHECKING';

export interface ImportReviewRow {
  line: number;
  externalOrderCode: string;
  status: ReviewStatus;
  data?: ImportRow;
  matchedAddress?: string;
  message?: string;
}

const statusStyle = {
  COORDINATES: 'text-slate-700',
  GEOCODED: 'text-emerald-800',
  CHECKING: 'text-slate-600',
  CREATED: 'text-emerald-800',
  NEEDS_ADDRESS: 'text-amber-800',
  DUPLICATE: 'text-amber-800',
  INVALID: 'text-red-700',
  CANCELLED: 'text-slate-600',
};

const statusIcon = {
  COORDINATES: MapPin, GEOCODED: Check, CHECKING: MapPin, CREATED: Check,
  NEEDS_ADDRESS: CircleAlert, DUPLICATE: CircleAlert, INVALID: AlertCircle, CANCELLED: CircleAlert,
};

export function OrderImportReview({
  fileName, rows, saved, checking, saving, blocked, onSave, onDiscard, onPriorityChange, renderPending,
}: {
  fileName: string;
  rows: ImportReviewRow[];
  saved: boolean;
  checking: boolean;
  saving: boolean;
  blocked: boolean;
  onSave: () => void;
  onDiscard: () => void;
  onPriorityChange?: (line: number, priority: OrderPriority) => void;
  renderPending: (row: ImportRow) => ReactNode;
}) {
  const { t, intlLocale } = useLocale();
  const [filter, setFilter] = useState<ReviewStatus | 'ALL'>('ALL');
  const [query, setQuery] = useState('');
  const [expanded, setExpanded] = useState<number>();
  const labels: Record<ReviewStatus, string> = {
    COORDINATES: t('Tọa độ trong file'),
    GEOCODED: t('Đã khớp địa chỉ'),
    CHECKING: t('Đang kiểm tra địa chỉ'),
    CREATED: t('Đã tạo'),
    NEEDS_ADDRESS: t('Cần bổ sung địa chỉ'),
    DUPLICATE: t('Trùng mã nguồn'),
    INVALID: t('Có lỗi'),
    CANCELLED: t('Đã hủy'),
  };
  const counts = rows.reduce<Partial<Record<ReviewStatus, number>>>((acc, row) => {
    acc[row.status] = (acc[row.status] ?? 0) + 1;
    return acc;
  }, {});
  const activeFilter = filter !== 'ALL' && counts[filter] ? filter : 'ALL';
  const search = query.trim().toLocaleLowerCase(intlLocale);
  const visible = rows.filter((row) => (activeFilter === 'ALL' || row.status === activeFilter)
    && (!search || [row.externalOrderCode, row.data?.recipientName, row.data?.deliveryAddress, row.matchedAddress]
      .some((value) => value?.toLocaleLowerCase(intlLocale).includes(search))));
  const saveCount = rows.length - (counts.INVALID ?? 0);
  const headings = [t('Dòng / Mã đơn'), t('Người nhận'), t('Địa chỉ giao hàng'), t('Tọa độ'), t('Trạng thái')];
  const columns: Record<string, string> = {
    externalOrderCode: t('Mã đơn nguồn'),
    recipientName: t('Người nhận'),
    recipientPhone: t('Số điện thoại'),
    weightKg: t('Khối lượng'),
    codAmount: t('Tiền thu hộ'),
    deliveryLat: t('Vĩ độ'),
    deliveryLng: t('Kinh độ'),
    timeWindowStart: t('Giờ hẹn bắt đầu'),
    timeWindowEnd: t('Giờ hẹn kết thúc'),
    priority: t('Mức độ ưu tiên'),
  };
  const dateTime = new Intl.DateTimeFormat(intlLocale, {
    timeZone: 'Asia/Ho_Chi_Minh', day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit',
  });

  return (
    <div className="min-w-0 overflow-hidden rounded-xl border border-slate-200 bg-white">
      <div className="space-y-4 px-4 py-4 sm:px-5">
        <div className="flex items-start gap-3">
          <ClipboardList size={20} aria-hidden="true" className="mt-0.5 shrink-0 text-slate-600" />
          <div className="min-w-0">
            <h3 className="text-base font-semibold text-slate-950">
              {saved ? t('Kết quả nhập vận đơn') : t('Rà soát đơn nhập')}
            </h3>
            <p className="mt-0.5 break-all text-sm text-slate-600">{fileName}</p>
          </div>
        </div>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex flex-wrap gap-1.5" aria-label={t('Lọc theo trạng thái nhập')}>
            <button type="button" aria-pressed={activeFilter === 'ALL'} onClick={() => setFilter('ALL')}
              className="min-h-9 rounded-md border border-slate-200 px-2.5 text-xs font-semibold text-slate-700 hover:bg-slate-50 aria-pressed:border-slate-400 aria-pressed:bg-slate-100 focus-visible:outline-2 focus-visible:outline-blue-600">
              {t('Tất cả')} <span className="ms-0.5 tabular-nums">{rows.length}</span>
            </button>
            {(Object.keys(labels) as ReviewStatus[]).filter((status) => counts[status]).map((status) => (
              <button key={status} type="button" aria-pressed={activeFilter === status} onClick={() => setFilter(status)}
                className={`min-h-9 rounded-md border border-slate-200 px-2.5 text-xs font-semibold hover:bg-slate-50 aria-pressed:border-slate-400 aria-pressed:bg-slate-100 focus-visible:outline-2 focus-visible:outline-blue-600 ${statusStyle[status]}`}>
                {labels[status]} <span className="ms-0.5 tabular-nums">{counts[status]}</span>
              </button>
            ))}
          </div>
          <label className="relative w-full sm:w-60">
            <Search aria-hidden="true" size={15} className="pointer-events-none absolute start-2.5 top-1/2 -translate-y-1/2 text-slate-500" />
            <input type="search" aria-label={t('Tìm trong đơn nhập')} placeholder={t('Mã đơn, người nhận, địa chỉ')}
              value={query} onChange={(event) => setQuery(event.target.value)}
              className="min-h-9 w-full rounded-md border border-slate-300 py-1.5 pe-2 ps-8 text-sm focus-visible:outline-2 focus-visible:outline-blue-600" />
          </label>
        </div>
        {checking && <p role="status" className="text-sm text-slate-600">{t('Đang dò tọa độ cho các dòng chưa có điểm giao…')}</p>}
        {!saved && !checking && !blocked && Boolean(counts.NEEDS_ADDRESS) && <p className="text-sm text-slate-600">
          {t('Dòng cần bổ sung địa chỉ sẽ vào hàng đợi sau khi lưu.')}
        </p>}
        {blocked && <p role="alert" className="text-sm text-red-700">
          {t('Đang bật dừng khi CSV có lỗi. Hãy sửa file hoặc đổi tùy chọn trong node Nhập vận đơn.')}
        </p>}
      </div>
      <div role="region" aria-label={t('Bảng đơn nhập')} tabIndex={0}
        className="max-h-[min(54dvh,36rem)] overflow-auto border-y border-slate-200 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-blue-600">
        <table className="w-full min-w-[780px] table-fixed text-left text-sm">
          <colgroup><col className="w-[15%]" /><col className="w-[17%]" /><col className="w-[31%]" /><col className="w-[13%]" /><col className="w-[24%]" /></colgroup>
          <thead className="sticky top-0 z-10 bg-white text-xs text-slate-600 shadow-[inset_0_-1px_0_var(--color-slate-200)]">
            <tr>{headings.map((heading, index) => (
              <th key={heading} scope="col" className={`px-3 py-2.5 font-semibold ${index === 3 ? 'text-right' : ''}`}>{heading}</th>
            ))}</tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {visible.map((row) => {
              const { data } = row;
              const Icon = statusIcon[row.status];
              const pending = saved && row.status === 'NEEDS_ADDRESS' ? data : undefined;
              const start = Date.parse(data?.timeWindowStart ?? '');
              const end = Date.parse(data?.timeWindowEnd ?? '');
              const appointment = Number.isFinite(start) && Number.isFinite(end) && start <= end
                ? dateTime.formatRange(start, end) : null;
              return (
                <Fragment key={row.line}>
                  <tr className="align-top">
                    <td className="px-3 py-3">
                      <span className="block break-words font-medium text-slate-950">{row.externalOrderCode || '—'}</span>
                      <span className="block text-xs tabular-nums text-slate-600">{t('Dòng')} {row.line}</span>
                      {data && (
                        !saved && onPriorityChange ? (
                          <select
                            aria-label={`${t('Mức độ ưu tiên')} ${row.externalOrderCode || row.line}`}
                            value={data.priority ?? 'NORMAL'}
                            disabled={saving}
                            onChange={(event) => onPriorityChange(row.line, event.target.value as OrderPriority)}
                            className={`mt-1.5 h-7 cursor-pointer rounded-md px-1.5 text-[11px] font-semibold focus-visible:outline-2 focus-visible:outline-blue-600 ${ORDER_PRIORITY_BADGE[data.priority ?? 'NORMAL']}`}
                          >
                            {(['NORMAL', 'HIGH', 'URGENT'] as OrderPriority[]).map((item) => (
                              <option key={item} value={item} className="bg-white text-slate-900">
                                {t(ORDER_PRIORITY_LABEL[item])}
                              </option>
                            ))}
                          </select>
                        ) : (
                          <span className={`mt-1.5 inline-block rounded-md px-1.5 py-0.5 text-[11px] font-semibold ${ORDER_PRIORITY_BADGE[data.priority ?? 'NORMAL']}`}>
                            {t(ORDER_PRIORITY_LABEL[data.priority ?? 'NORMAL'])}
                          </span>
                        )
                      )}
                    </td>
                    <td className="break-words px-3 py-3 text-slate-800">
                      {data?.recipientName || '—'}
                      {appointment && <span className="mt-0.5 block text-xs tabular-nums text-slate-600">
                        {appointment}
                      </span>}
                    </td>
                    <td className="break-words px-3 py-3 text-slate-800">
                      {data?.deliveryAddress || '—'}
                      {row.matchedAddress && <p className="mt-1 text-xs text-emerald-800">{t('Địa chỉ trên bản đồ')}: {row.matchedAddress}</p>}
                    </td>
                    <td className="px-3 py-3 text-right text-xs tabular-nums text-slate-700">
                      {data?.deliveryLat != null && data.deliveryLng != null ? <>{data.deliveryLat.toFixed(6)}<br />{data.deliveryLng.toFixed(6)}</> : '—'}
                    </td>
                    <td className="break-words px-3 py-3">
                      <span className={`flex items-start gap-1.5 font-medium ${statusStyle[row.status]}`}>
                        <Icon size={15} aria-hidden="true" className="mt-0.5 shrink-0" />{labels[row.status]}
                      </span>
                      {row.message && <p className="mt-1 text-xs text-slate-600">
                        {Object.hasOwn(columns, row.message) ? `${t('Kiểm tra cột')}: ${columns[row.message]}` : t(row.message)}
                      </p>}
                      {pending && <button type="button" onClick={() => setExpanded(expanded === row.line ? undefined : row.line)}
                        aria-expanded={expanded === row.line} className="mt-1 min-h-8 rounded text-xs font-semibold text-blue-700 underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-blue-600">
                        {expanded === row.line ? t('Ẩn thao tác') : t('Xử lý địa chỉ')}
                      </button>}
                    </td>
                  </tr>
                  {pending && expanded === row.line && <tr><td colSpan={5} className="bg-slate-50 px-4 pt-3">{renderPending(pending)}</td></tr>}
                </Fragment>
              );
            })}
            {!visible.length && <tr><td colSpan={5} className="px-3 py-8 text-sm text-slate-600">{t('Không có dòng phù hợp với bộ lọc.')}</td></tr>}
          </tbody>
        </table>
      </div>
      <div className="flex flex-wrap items-center justify-between gap-3 px-4 py-3 sm:px-5">
        <p className="text-xs text-slate-600" role="status">{visible.length}/{rows.length} {t('dòng đang hiển thị')}</p>
        <div className="flex flex-wrap gap-2">
          <Button size="sm" variant="outline" disabled={saving} onClick={onDiscard}>
            {saved ? t('Đóng') : t('Bỏ lần nhập')}
          </Button>
          {!saved && <Button size="sm" isLoading={saving} disabled={checking || blocked || saveCount === 0} onClick={onSave}>
            {t('Lưu lần nhập')} ({saveCount})
          </Button>}
        </div>
      </div>
    </div>
  );
}
