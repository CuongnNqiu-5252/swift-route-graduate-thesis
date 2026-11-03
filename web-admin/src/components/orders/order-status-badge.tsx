import { Badge } from '@/components/ui/badge';
import { ORDER_STATUS_COLOR, ORDER_STATUS_LABEL, OrderStatus } from '@/domain/entities/order.entity';

export function OrderStatusBadge({ status }: { status: OrderStatus }) {
  return (
    <Badge className={ORDER_STATUS_COLOR[status]}>
      {ORDER_STATUS_LABEL[status]}
    </Badge>
  );
}

