'use client';

import * as Tabs from '@radix-ui/react-tabs';
import { Check, Copy, ExternalLink, Link2 } from 'lucide-react';
import { useEffect, useState, type ReactNode } from 'react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Modal } from '@/components/ui/modal';
import {
  ORDER_PRIORITY_BADGE,
  ORDER_PRIORITY_LABEL,
  type Order,
  type OrderPriority,
  type OrderTimeline,
} from '@/domain/entities/order.entity';
import { useLocale } from '@/i18n/provider';
import { orderRepository } from '@/infrastructure/repositories/order.repository';
import { OrderStatusBadge } from './order-status-badge';
import { OrderTimelinePanel } from './order-timeline';
import { RecipientNotificationHistory } from './recipient-notification-history';
import { useAuth } from '@/application/contexts/auth-context';

const codStatusLabel: Record<string, string> = {
  NOT_APPLICABLE: 'Không COD',
  COD_PENDING: 'Chờ thu COD',
  IN_CUSTODY: 'Đã thu, chờ bàn giao',
  HANDED_OVER: 'Đã bàn giao COD',
};

function DetailField({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <dt className="text-xs font-medium text-slate-500">{label}</dt>
      <dd className="mt-1 break-words text-sm font-medium text-slate-900">{children || '—'}</dd>
    </div>
  );
}

function OrderDetail({ orderId }: { orderId: string }) {
  const { user, hasPermission } = useAuth();
  const canReadRecipientNotifications = hasPermission('order:read')
    && ['OWNER', 'TENANT_ADMIN', 'OPERATOR'].includes(user?.tenantRole ?? '');
  const canEditPriority = hasPermission('order:create');
  const { t, intlLocale } = useLocale();
  const [attempt, setAttempt] = useState(0);
  const [timelineAttempt, setTimelineAttempt] = useState(0);
  const [updatingPriority, setUpdatingPriority] = useState(false);
  const [copiedToken, setCopiedToken] = useState(false);
  const [copiedLink, setCopiedLink] = useState(false);
  const [result, setResult] = useState<{ order: Order } | { error: true }>();
  const [timelineState, setTimelineState] = useState<{
    loading: boolean;
    timeline?: OrderTimeline;
    error: boolean;
  }>({ loading: true, error: false });

  useEffect(() => {
    const controller = new AbortController();
    orderRepository.getOrder(orderId, controller.signal).then(
      (order) => {
        if (!controller.signal.aborted) setResult({ order });
      },
      () => {
        if (!controller.signal.aborted) setResult({ error: true });
      },
    );
    return () => controller.abort();
  }, [attempt, orderId]);

  useEffect(() => {
    const controller = new AbortController();
    orderRepository.getOrderTimeline(orderId, controller.signal).then(
      (timeline) => {
        if (!controller.signal.aborted) {
          setTimelineState({ loading: false, timeline, error: false });
        }
      },
      () => {
        if (!controller.signal.aborted) {
          setTimelineState({ loading: false, error: true });
        }
      },
    );
    return () => controller.abort();
  }, [timelineAttempt, orderId]);

  if (!result) {
    return (
      <div className="space-y-4 py-2" role="status">
        <div className="h-6 w-36 animate-pulse rounded-full bg-slate-200" />
        <div className="grid gap-3 sm:grid-cols-2">
          <div className="h-32 animate-pulse rounded-xl bg-slate-100" />
          <div className="h-32 animate-pulse rounded-xl bg-slate-100" />
        </div>
        <p className="text-xs text-slate-500">{t('Đang tải chi tiết đơn hàng…')}</p>
      </div>
    );
  }
  if ('error' in result) {
    return (
      <div className="space-y-3 rounded-xl border border-rose-200 bg-rose-50/60 p-4 text-sm">
        <p role="alert" className="font-medium text-rose-900">{t('Không thể tải chi tiết đơn hàng.')}</p>
        <Button variant="outline" size="sm" onClick={() => {
          setResult(undefined);
          setAttempt((value) => value + 1);
        }}>{t('Thử lại')}</Button>
      </div>
    );
  }

  const { order } = result;
  const { timeline } = timelineState;
  const money = new Intl.NumberFormat(intlLocale, { style: 'currency', currency: 'VND' });
  const dateTime = new Intl.DateTimeFormat(intlLocale, {
    dateStyle: 'short',
    timeStyle: 'short',
    timeZone: 'Asia/Ho_Chi_Minh',
  });
  const amount = (value?: number) => value == null ? '—' : money.format(value);
  const trackingToken = timeline?.tracking_token || order.tracking_token;
  const driverLabel = order.assigned_driver_name
    || (order.assigned_driver_id ? order.assigned_driver_id.slice(0, 8) : null);
  const timeWindowText = order.time_window_start && order.time_window_end
    ? `${dateTime.format(new Date(order.time_window_start))} — ${dateTime.format(new Date(order.time_window_end))}`
    : order.time_window_start
      ? dateTime.format(new Date(order.time_window_start))
      : order.time_window_end
        ? dateTime.format(new Date(order.time_window_end))
        : null;

  async function copyTrackingToken() {
    if (!trackingToken || typeof navigator === 'undefined' || !navigator.clipboard) return;
    try {
      await navigator.clipboard.writeText(trackingToken);
      setCopiedToken(true);
      toast.success(t('Đã sao chép mã tra cứu'));
      window.setTimeout(() => setCopiedToken(false), 1800);
    } catch {
      // Ignore clipboard permission denials in restricted browser contexts.
    }
  }

  async function copyTrackingLink() {
    if (!trackingToken || typeof window === 'undefined' || typeof navigator === 'undefined' || !navigator.clipboard) {
      return;
    }
    try {
      const publicUrl = `${window.location.origin}/track/${encodeURIComponent(trackingToken)}`;
      await navigator.clipboard.writeText(publicUrl);
      setCopiedLink(true);
      toast.success(t('Đã sao chép link theo dõi'));
      window.setTimeout(() => setCopiedLink(false), 1800);
    } catch {
      // Ignore clipboard permission denials in restricted browser contexts.
    }
  }

  return (
    <Tabs.Root defaultValue="details" className="space-y-4 text-sm">
      <div className="flex flex-wrap items-center justify-between gap-2.5">
        <OrderStatusBadge status={order.status} />
        {trackingToken && (
          <div className="flex flex-wrap items-center gap-1.5">
            <button
              type="button"
              onClick={copyTrackingToken}
              title={t('Sao chép mã tra cứu')}
              className="inline-flex cursor-pointer items-center gap-1.5 rounded-lg border border-slate-200 bg-slate-50 px-2.5 py-1 font-mono text-xs tabular-nums text-slate-700 transition-colors duration-150 hover:border-slate-300 hover:bg-slate-100"
            >
              <span>{trackingToken}</span>
              {copiedToken
                ? <Check className="h-3.5 w-3.5 text-emerald-600" aria-hidden="true" />
                : <Copy className="h-3.5 w-3.5 text-slate-500" aria-hidden="true" />}
            </button>
            <button
              type="button"
              onClick={copyTrackingLink}
              className="inline-flex cursor-pointer items-center gap-1.5 rounded-lg border border-[#17766c]/25 bg-[#17766c]/10 px-2.5 py-1 text-xs font-medium text-[#115e56] transition-colors duration-150 hover:bg-[#17766c]/15"
            >
              {copiedLink
                ? <Check className="h-3.5 w-3.5 text-emerald-700" aria-hidden="true" />
                : <Link2 className="h-3.5 w-3.5" aria-hidden="true" />}
              <span>{t('Sao chép link theo dõi')}</span>
            </button>
            <a
              href={`/track/${encodeURIComponent(trackingToken)}`}
              target="_blank"
              rel="noopener noreferrer"
              title={t('Mở trang theo dõi công khai')}
              aria-label={t('Mở trang theo dõi công khai')}
              className="inline-flex h-7 w-7 cursor-pointer items-center justify-center rounded-lg border border-slate-200 bg-white text-slate-600 transition-colors duration-150 hover:border-slate-300 hover:bg-slate-50 hover:text-slate-950"
            >
              <ExternalLink className="h-3.5 w-3.5" aria-hidden="true" />
            </a>
          </div>
        )}
      </div>

      <Tabs.List className="flex flex-wrap border-b border-slate-200">
        <Tabs.Trigger
          value="details"
          className="relative cursor-pointer px-3.5 py-2 text-xs font-medium text-slate-600 transition-colors hover:text-slate-950 data-[state=active]:text-slate-950 after:absolute after:inset-x-0 after:bottom-[-1px] after:h-0.5 after:rounded-full after:bg-transparent data-[state=active]:after:bg-[#17766c]"
        >
          {t('Thông tin chung')}
        </Tabs.Trigger>
        <Tabs.Trigger
          value="timeline"
          className="relative inline-flex cursor-pointer items-center gap-1.5 px-3.5 py-2 text-xs font-medium text-slate-600 transition-colors hover:text-slate-950 data-[state=active]:text-slate-950 after:absolute after:inset-x-0 after:bottom-[-1px] after:h-0.5 after:rounded-full after:bg-transparent data-[state=active]:after:bg-[#17766c]"
        >
          <span>{t('Lịch sử đơn hàng')}</span>
          {timeline && (
            <span className="rounded-full bg-slate-100 px-1.5 py-0.25 text-[11px] font-semibold tabular-nums text-slate-700">
              {timeline.events.length}
            </span>
          )}
        </Tabs.Trigger>
        {canReadRecipientNotifications && (
          <Tabs.Trigger
            value="recipient-notifications"
            className="relative cursor-pointer px-3.5 py-2 text-xs font-medium text-slate-600 transition-colors hover:text-slate-950 data-[state=active]:text-slate-950 after:absolute after:inset-x-0 after:bottom-[-1px] after:h-0.5 after:rounded-full after:bg-transparent data-[state=active]:after:bg-[#17766c]"
          >
            {t('Thông báo người nhận')}
          </Tabs.Trigger>
        )}
      </Tabs.List>

      <Tabs.Content value="details" className="space-y-4">
        <div className="grid gap-3.5 sm:grid-cols-2">
          <section className="min-w-0 space-y-2.5 rounded-xl border border-slate-200/90 bg-slate-50/50 p-3.5">
            <h3 className="text-xs font-semibold text-slate-900">{t('Người gửi')}</h3>
            <dl className="space-y-2">
              <DetailField label={t('Tên liên hệ')}>{order.sender_name}</DetailField>
              <DetailField label={t('Số điện thoại')}>{order.sender_phone}</DetailField>
              <DetailField label={t('Địa chỉ lấy hàng')}>{order.pickup_address}</DetailField>
            </dl>
          </section>
          <section className="min-w-0 space-y-2.5 rounded-xl border border-slate-200/90 bg-slate-50/50 p-3.5">
            <h3 className="text-xs font-semibold text-slate-900">{t('Người nhận')}</h3>
            <dl className="space-y-2">
              <DetailField label={t('Tên liên hệ')}>{order.customer_name}</DetailField>
              <DetailField label={t('Số điện thoại')}>{order.recipient_phone}</DetailField>
              <DetailField label={t('Địa chỉ giao hàng')}>{order.delivery_address}</DetailField>
            </dl>
          </section>
        </div>

        <section className="space-y-3 rounded-xl border border-slate-200/90 bg-white p-3.5">
          <h3 className="text-xs font-semibold text-slate-900">{t('Vận hành & Giao nhận')}</h3>
          <dl className="grid gap-3 sm:grid-cols-2">
            <DetailField label={t('Tài xế phụ trách')}>
              {driverLabel || t('Chưa phân công')}
            </DetailField>
            <DetailField label={t('Mức độ ưu tiên')}>
              {canEditPriority && (order.status === 'READY_FOR_DISPATCH' || order.status === 'PLANNED') ? (
                <select
                  aria-label={t('Mức độ ưu tiên')}
                  disabled={updatingPriority}
                  value={order.priority || 'NORMAL'}
                  onChange={async (event) => {
                    const nextPriority = event.target.value as OrderPriority;
                    setUpdatingPriority(true);
                    try {
                      const updated = await orderRepository.updateOrderPriority(order.id, nextPriority);
                      setResult({ order: updated });
                      toast.success(t('Đã cập nhật mức độ ưu tiên'));
                    } catch {
                      toast.error(t('Không thể cập nhật mức độ ưu tiên'));
                    } finally {
                      setUpdatingPriority(false);
                    }
                  }}
                  className={`h-7 cursor-pointer rounded-md px-2 text-xs font-semibold focus-visible:outline-2 focus-visible:outline-blue-600 ${ORDER_PRIORITY_BADGE[order.priority || 'NORMAL']}`}
                >
                  {(['NORMAL', 'HIGH', 'URGENT'] as OrderPriority[]).map((item) => (
                    <option key={item} value={item} className="bg-white text-slate-900">
                      {t(ORDER_PRIORITY_LABEL[item])}
                    </option>
                  ))}
                </select>
              ) : (
                <span className={`inline-block rounded-md px-2 py-0.5 text-xs font-semibold ${ORDER_PRIORITY_BADGE[order.priority || 'NORMAL']}`}>
                  {t(ORDER_PRIORITY_LABEL[order.priority || 'NORMAL'])}
                </span>
              )}
            </DetailField>
            {timeWindowText && (
              <DetailField label={t('Khung giờ giao')}>{timeWindowText}</DetailField>
            )}
            {order.notes && (
              <DetailField label={t('Ghi chú')}>{order.notes}</DetailField>
            )}
          </dl>

          {timeline?.latest_proof_url && (
            <div className="border-t border-slate-100 pt-3">
              <p className="mb-2 text-xs font-medium text-slate-500">{t('Ảnh bằng chứng giao hàng (POD)')}</p>
              <a
                href={timeline.latest_proof_url}
                target="_blank"
                rel="noopener noreferrer"
                className="group inline-block cursor-pointer overflow-hidden rounded-xl border border-slate-200 bg-slate-50 transition-colors hover:border-slate-300"
              >
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img
                  src={timeline.latest_proof_url}
                  alt={t('Ảnh bằng chứng giao hàng (POD)')}
                  className="h-28 w-44 object-cover transition-transform duration-200 group-hover:scale-[1.03]"
                />
                <span className="inline-flex w-full items-center justify-center gap-1 border-t border-slate-200 bg-white px-2.5 py-1.5 text-xs font-medium text-[#17766c]">
                  {t('Mở ảnh gốc')}
                  <ExternalLink className="h-3 w-3" aria-hidden="true" />
                </span>
              </a>
            </div>
          )}
        </section>

        <section className="space-y-3 rounded-xl border border-slate-200/90 bg-white p-3.5">
          <h3 className="text-xs font-semibold text-slate-900">{t('Thông tin COD')}</h3>
          <dl className="grid grid-cols-2 divide-x divide-slate-100 tabular-nums sm:grid-cols-3">
            <div className="pr-3">
              <DetailField label={t('Tiền thu hộ')}>{amount(order.cod_amount)}</DetailField>
            </div>
            <div className="px-3">
              <DetailField label={t('Trạng thái COD')}>
                {order.cod_status && (codStatusLabel[order.cod_status] ? t(codStatusLabel[order.cod_status]) : '—')}
              </DetailField>
            </div>
            <div className="col-span-2 mt-2 border-t border-slate-100 pt-2 sm:col-span-1 sm:mt-0 sm:border-t-0 sm:pl-3 sm:pt-0">
              <DetailField label={t('Tổng tiền người nhận trả')}>{amount(order.total_amount)}</DetailField>
            </div>
            {order.cod_collected_at && (
              <div className="col-span-full mt-2 border-t border-slate-100 pt-2">
                <DetailField label={t('Thời điểm thu COD')}>{dateTime.format(new Date(order.cod_collected_at))}</DetailField>
              </div>
            )}
          </dl>
        </section>
      </Tabs.Content>

      <Tabs.Content value="timeline">
        <OrderTimelinePanel
          timeline={timeline}
          loading={timelineState.loading}
          error={timelineState.error}
          onRetry={() => {
            setTimelineState({ loading: true, error: false });
            setTimelineAttempt((value) => value + 1);
          }}
        />
      </Tabs.Content>
      {canReadRecipientNotifications && (
        <Tabs.Content value="recipient-notifications">
          <RecipientNotificationHistory key={orderId} orderId={orderId} />
        </Tabs.Content>
      )}
    </Tabs.Root>
  );
}

export function OrderDetailModal({ orderId, orderCode, shortCode }: {
  orderId: string;
  orderCode: string;
  shortCode: string;
}) {
  const { t } = useLocale();
  const [open, setOpen] = useState(false);

  return (
    <div onClick={(event) => event.stopPropagation()}>
      <Modal
        open={open}
        onOpenChange={setOpen}
        title={t('Chi tiết đơn hàng')}
        description={orderCode || orderId}
        className="max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-2xl overflow-y-auto break-words"
        trigger={
          <button
            type="button"
            className="rounded-sm font-mono text-sm font-semibold text-blue-700 underline decoration-blue-300 underline-offset-4 hover:text-blue-900"
            aria-label={`${t('Chi tiết đơn hàng')} ${orderCode || orderId}`}
          >
            {shortCode}
          </button>
        }
      >
        {open && <OrderDetail key={orderId} orderId={orderId} />}
      </Modal>
    </div>
  );
}
