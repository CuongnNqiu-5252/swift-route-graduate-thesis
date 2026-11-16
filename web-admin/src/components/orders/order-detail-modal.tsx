'use client';

import { useEffect, useState, type ReactNode } from 'react';
import { Button } from '@/components/ui/button';
import { Modal } from '@/components/ui/modal';
import type { Order } from '@/domain/entities/order.entity';
import { useLocale } from '@/i18n/provider';
import { orderRepository } from '@/infrastructure/repositories/order.repository';
import { OrderStatusBadge } from './order-status-badge';

const codStatusLabel: Record<string, string> = {
  NOT_APPLICABLE: 'Không COD',
  COD_PENDING: 'Chờ thu COD',
  IN_CUSTODY: 'Đã thu, chờ bàn giao',
  HANDED_OVER: 'Đã bàn giao COD',
};

function DetailField({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <dt className="text-slate-600">{label}</dt>
      <dd className="mt-0.5 break-words font-medium text-slate-900">{children || '—'}</dd>
    </div>
  );
}

function OrderDetail({ orderId }: { orderId: string }) {
  const { t, intlLocale } = useLocale();
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<{ order: Order } | { error: true }>();

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

  if (!result) return <p role="status" className="text-sm text-slate-600">{t('Đang tải chi tiết đơn hàng…')}</p>;
  if ('error' in result) {
    return (
      <div className="space-y-3 text-sm">
        <p role="alert">{t('Không thể tải chi tiết đơn hàng.')}</p>
        <Button variant="outline" onClick={() => {
          setResult(undefined);
          setAttempt((value) => value + 1);
        }}>{t('Thử lại')}</Button>
      </div>
    );
  }

  const { order } = result;
  const money = new Intl.NumberFormat(intlLocale, { style: 'currency', currency: 'VND' });
  const dateTime = new Intl.DateTimeFormat(intlLocale, {
    dateStyle: 'short',
    timeStyle: 'short',
    timeZone: 'Asia/Ho_Chi_Minh',
  });
  const amount = (value?: number) => value == null ? '—' : money.format(value);

  return (
    <div className="space-y-5 text-sm">
      <OrderStatusBadge status={order.status} />
      <div className="grid gap-4 sm:grid-cols-2">
        <section className="min-w-0 space-y-2">
          <h3 className="font-semibold text-slate-900">{t('Người gửi')}</h3>
          <dl className="space-y-2">
            <DetailField label={t('Tên liên hệ')}>{order.sender_name}</DetailField>
            <DetailField label={t('Số điện thoại')}>{order.sender_phone}</DetailField>
            <DetailField label={t('Địa chỉ lấy hàng')}>{order.pickup_address}</DetailField>
          </dl>
        </section>
        <section className="min-w-0 space-y-2">
          <h3 className="font-semibold text-slate-900">{t('Người nhận')}</h3>
          <dl className="space-y-2">
            <DetailField label={t('Tên liên hệ')}>{order.customer_name}</DetailField>
            <DetailField label={t('Số điện thoại')}>{order.recipient_phone}</DetailField>
            <DetailField label={t('Địa chỉ giao hàng')}>{order.delivery_address}</DetailField>
          </dl>
        </section>
      </div>
      <section className="space-y-3 border-t border-slate-200 pt-4">
        <h3 className="font-semibold text-slate-900">{t('Phí giao hàng và COD')}</h3>
        <dl className="grid grid-cols-2 gap-3 tabular-nums">
          <DetailField label={t('Tiền thu hộ')}>{amount(order.cod_amount)}</DetailField>
          <DetailField label={t('Trạng thái COD')}>
            {order.cod_status && (codStatusLabel[order.cod_status] ? t(codStatusLabel[order.cod_status]) : '—')}
          </DetailField>
          <DetailField label={t('Phí giao hàng')}>{amount(order.delivery_fee)}</DetailField>
          <DetailField label={t('Giá trị khai báo')}>{amount(order.declared_value)}</DetailField>
          <DetailField label={t('Tổng tiền người nhận trả')}>{amount(order.total_amount)}</DetailField>
          {order.payer && (
            <DetailField label={t('Bên trả phí giao hàng')}>
              {order.payer === 'SENDER' ? t('Người gửi') : order.payer === 'RECIPIENT' ? t('Người nhận') : '—'}
            </DetailField>
          )}
          {order.cod_collected_at && (
            <DetailField label={t('Thời điểm thu COD')}>{dateTime.format(new Date(order.cod_collected_at))}</DetailField>
          )}
        </dl>
      </section>
    </div>
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
        className="max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-xl overflow-y-auto break-words"
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
