'use client';

import { useLocale } from '@/i18n/provider';
import { Badge } from '@/components/ui/badge';
import { ORDER_STATUS_COLOR, ORDER_STATUS_LABEL, OrderStatus } from '@/domain/entities/order.entity';

export function OrderStatusBadge({ status }: {
  status: OrderStatus | string;
}) {
  const { t } = useLocale();
  const label = ORDER_STATUS_LABEL[status as OrderStatus] || status || t("Không xác định");
  const color = ORDER_STATUS_COLOR[status as OrderStatus] || 'bg-slate-100 text-slate-800';
  return (
    <Badge className={color}>
      {t(label)}
    </Badge>
  );
}

