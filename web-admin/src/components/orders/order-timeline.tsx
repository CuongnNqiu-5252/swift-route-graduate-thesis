'use client';

import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { ORDER_STATUS_COLOR, ORDER_STATUS_LABEL, type OrderStatus, type OrderTimeline, type OrderTimelineEvent } from '@/domain/entities/order.entity';
import { useLocale } from '@/i18n/provider';
import { cn } from '@/lib/utils';
import {
  AlertTriangle,
  ArrowRight,
  Banknote,
  CheckCircle2,
  ExternalLink,
  GitCommitHorizontal,
  PackageCheck,
  RotateCcw,
} from 'lucide-react';
import { useState } from 'react';

type CategoryFilter = 'ALL' | 'STATUS' | 'DELIVERY' | 'INCIDENT' | 'COD';

const FILTER_LABELS: Array<{ key: CategoryFilter; label: string }> = [
  { key: 'ALL', label: 'Tất cả' },
  { key: 'STATUS', label: 'Trạng thái' },
  { key: 'DELIVERY', label: 'Giao hàng' },
  { key: 'INCIDENT', label: 'Sự cố' },
  { key: 'COD', label: 'COD' },
];

function roleLabelKey(role?: string | null): string | null {
  if (!role) return null;
  switch (role.toUpperCase()) {
    case 'DRIVER':
      return 'Tài xế';
    case 'OPERATOR':
      return 'Điều phối viên';
    case 'OWNER':
    case 'TENANT_OWNER':
      return 'Chủ doanh nghiệp';
    case 'TENANT_ADMIN':
    case 'SYSTEM_ADMIN':
      return 'Quản trị viên';
    case 'USER':
      return 'Khách hàng';
    case 'SYSTEM':
      return 'Hệ thống';
    default:
      return role;
  }
}

function eventVisual(event: OrderTimelineEvent): {
  icon: typeof GitCommitHorizontal;
  dotClass: string;
} {
  switch (event.category) {
    case 'DELIVERY':
      if (event.event_type === 'DELIVERY_ATTEMPT_SUCCESS') {
        return { icon: CheckCircle2, dotClass: 'border-emerald-600 bg-emerald-50 text-emerald-700' };
      }
      return { icon: AlertTriangle, dotClass: 'border-rose-600 bg-rose-50 text-rose-700' };
    case 'INCIDENT':
      if (event.event_type === 'INCIDENT_RESOLVED') {
        return { icon: CheckCircle2, dotClass: 'border-teal-600 bg-teal-50 text-teal-700' };
      }
      return { icon: AlertTriangle, dotClass: 'border-amber-600 bg-amber-50 text-amber-700' };
    case 'COD':
      return { icon: Banknote, dotClass: 'border-emerald-600 bg-emerald-50 text-emerald-700' };
    default:
      if (event.to_status === 'DELIVERED') {
        return { icon: PackageCheck, dotClass: 'border-emerald-600 bg-emerald-50 text-emerald-700' };
      }
      if (event.to_status === 'RETURNING' || event.to_status === 'RETURNED') {
        return { icon: RotateCcw, dotClass: 'border-orange-600 bg-orange-50 text-orange-700' };
      }
      if (event.to_status === 'FAILED' || event.to_status === 'CANCELLED') {
        return { icon: AlertTriangle, dotClass: 'border-rose-600 bg-rose-50 text-rose-700' };
      }
      return { icon: GitCommitHorizontal, dotClass: 'border-blue-600 bg-blue-50 text-blue-700' };
  }
}

function formatStatusLabel(status?: string | null): string | null {
  if (!status) return null;
  return ORDER_STATUS_LABEL[status as OrderStatus] ?? status;
}

function StatusBadgeChip({ status }: { status: string }) {
  const { t } = useLocale();
  const label = formatStatusLabel(status);
  const color = ORDER_STATUS_COLOR[status as OrderStatus] ?? 'bg-slate-100 text-slate-700';
  return <Badge className={cn('text-[11px]', color)}>{label ? t(label) : status}</Badge>;
}

export function OrderTimelinePanel({
  timeline,
  loading,
  error,
  onRetry,
}: {
  timeline?: OrderTimeline;
  loading: boolean;
  error: boolean;
  onRetry: () => void;
}) {
  const { t, intlLocale } = useLocale();
  const [filter, setFilter] = useState<CategoryFilter>('ALL');

  if (loading) {
    return (
      <p role="status" className="py-6 text-sm text-slate-600">
        {t('Đang tải lịch sử đơn hàng…')}
      </p>
    );
  }

  if (error || !timeline) {
    return (
      <div className="space-y-3 py-4 text-sm">
        <p role="alert" className="text-slate-700">
          {t('Không thể tải lịch sử đơn hàng.')}
        </p>
        <Button variant="outline" size="sm" onClick={onRetry}>
          {t('Thử lại')}
        </Button>
      </div>
    );
  }

  const events = timeline.events ?? [];
  if (events.length === 0) {
    return (
      <p className="py-6 text-sm text-slate-500">
        {t('Chưa có sự kiện nào được ghi nhận cho đơn hàng này.')}
      </p>
    );
  }

  const availableCategories = new Set(events.map((event) => event.category));
  const categoryCounts = new Map<string, number>();
  for (const event of events) {
    categoryCounts.set(event.category, (categoryCounts.get(event.category) ?? 0) + 1);
  }
  const filteredEvents =
    filter === 'ALL' ? events : events.filter((event) => event.category === filter);

  const dateTime = new Intl.DateTimeFormat(intlLocale, {
    dateStyle: 'short',
    timeStyle: 'medium',
    timeZone: 'Asia/Ho_Chi_Minh',
  });

  return (
    <div className="space-y-4">
      {availableCategories.size >= 2 && (
        <div className="flex flex-wrap gap-1.5" role="group" aria-label={t('Trạng thái')}>
          {FILTER_LABELS.filter(
            (item) => item.key === 'ALL' || availableCategories.has(item.key),
          ).map((item) => {
            const count = item.key === 'ALL' ? events.length : (categoryCounts.get(item.key) ?? 0);
            const active = filter === item.key;
            return (
              <button
                key={item.key}
                type="button"
                aria-pressed={active}
                onClick={() => setFilter(item.key)}
                className={cn(
                  'inline-flex cursor-pointer items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-medium transition-colors duration-150',
                  active
                    ? 'border-slate-900 bg-slate-900 text-white'
                    : 'border-slate-200 bg-white text-slate-600 hover:border-slate-300 hover:bg-slate-50 hover:text-slate-900',
                )}
              >
                <span>{t(item.label)}</span>
                <span className={cn('tabular-nums text-[11px]', active ? 'text-slate-300' : 'text-slate-400')}>
                  {count}
                </span>
              </button>
            );
          })}
        </div>
      )}

      {filteredEvents.length === 0 ? (
        <p className="py-4 text-sm text-slate-500">{t('Không có sự kiện thuộc nhóm đã chọn.')}</p>
      ) : (
        <ol className="space-y-3.5">
          {filteredEvents.map((event, index) => {
            const { icon: Icon, dotClass } = eventVisual(event);
            const roleKey = roleLabelKey(event.actor_role);
            const images: Array<{ url: string; label: string }> = [];
            if (event.proof_url) {
              images.push({ url: event.proof_url, label: t('Xem ảnh giao hàng (POD)') });
            }
            for (const url of event.evidence_urls ?? []) {
              if (url && url !== event.proof_url) {
                images.push({ url, label: t('Xem bằng chứng') });
              }
            }

            return (
              <li key={event.id} className="relative flex gap-3">
                {index < filteredEvents.length - 1 && (
                  <span
                    aria-hidden="true"
                    className="absolute left-[13px] top-7 h-[calc(100%+6px)] w-px bg-slate-200"
                  />
                )}
                <span
                  aria-hidden="true"
                  className={cn(
                    'relative mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-full border transition-transform duration-150',
                    index === 0 && 'ring-4 ring-slate-100',
                    dotClass,
                  )}
                >
                  <Icon className="h-3.5 w-3.5" />
                </span>
                <div className="min-w-0 flex-1 rounded-xl border border-slate-200/80 bg-slate-50/50 p-3 transition-colors duration-150 hover:border-slate-300 hover:bg-white">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <p className="text-sm font-semibold text-slate-900">{event.title}</p>
                    <time
                      dateTime={event.occurred_at}
                      className="text-xs tabular-nums text-slate-500"
                    >
                      {dateTime.format(new Date(event.occurred_at))}
                    </time>
                  </div>

                  {(event.from_status || event.to_status) && (
                    <div className="mt-1.5 flex flex-wrap items-center gap-1.5">
                      {event.from_status && <StatusBadgeChip status={event.from_status} />}
                      {event.from_status && event.to_status && (
                        <ArrowRight className="h-3.5 w-3.5 text-slate-400" aria-hidden="true" />
                      )}
                      {event.to_status && <StatusBadgeChip status={event.to_status} />}
                    </div>
                  )}

                  {event.description && (
                    <p className="mt-1.5 whitespace-pre-wrap break-words text-xs leading-5 text-slate-700">
                      {event.description}
                    </p>
                  )}

                  {roleKey && (
                    <p className="mt-2 text-[11px] font-medium text-slate-500">
                      {t(roleKey)}
                      {event.actor_id ? <span className="font-mono text-slate-400"> · {event.actor_id.slice(0, 8)}</span> : null}
                    </p>
                  )}

                  {images.length > 0 && (
                    <div className="mt-2.5 flex flex-wrap gap-2">
                      {images.map((img, imgIdx) => (
                        <a
                          key={`${img.url}-${imgIdx}`}
                          href={img.url}
                          target="_blank"
                          rel="noopener noreferrer"
                          title={t('Mở ảnh kích thước đầy đủ')}
                          className="group relative block h-16 w-16 cursor-pointer overflow-hidden rounded-sm border border-slate-200 bg-white transition-colors hover:border-slate-300"
                        >
                          {/* eslint-disable-next-line @next/next/no-img-element */}
                          <img
                            src={img.url}
                            alt={img.label}
                            className="h-full w-full object-cover transition-transform duration-200 group-hover:scale-105"
                          />
                          <span className="absolute bottom-0.5 right-0.5 rounded bg-slate-900/75 p-0.5 text-white">
                            <ExternalLink className="h-2.5 w-2.5" aria-hidden="true" />
                          </span>
                        </a>
                      ))}
                    </div>
                  )}
                </div>
              </li>
            );
          })}
        </ol>
      )}
    </div>
  );
}
