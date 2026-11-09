'use client';

import { Order } from '@/domain/entities/order.entity';
import { OrderStatusBadge } from './order-status-badge';
import { formatVNDate } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import { Driver } from '@/domain/entities/driver.entity';
import { MapPin, Navigation } from 'lucide-react';

interface OrderTableProps {
  orders: Order[];
  isLoading: boolean;
  onCancelOrder?: (orderId: string, reason?: string) => void;
  selectable?: boolean;
  selectedIds?: Set<string>;
  onToggleSelect?: (order: Order) => void;
  drivers?: Driver[];
  selectedId?: string;
  onViewRoute?: (order: Order) => void;
}

export function formatShortOrderCode(orderCode?: string, fallbackId?: string): string {
  const raw = (orderCode || fallbackId || '').trim();
  if (!raw) return '—';
  if (raw.length <= 14) return raw;
  return `#${raw.slice(0, 8).toUpperCase()}`;
}

export function OrderTable({
  orders,
  isLoading,
  onCancelOrder,
  selectable,
  selectedIds,
  onToggleSelect,
  drivers = [],
  selectedId,
  onViewRoute,
}: OrderTableProps) {
  if (isLoading) {
    return (
      <div className="flex h-48 items-center justify-center">
        <div className="h-7 w-7 animate-spin rounded-full border-3 border-slate-900 border-t-transparent" />
      </div>
    );
  }

  if (orders.length === 0) {
    return (
      <div className="flex h-44 flex-col items-center justify-center px-4 text-center text-sm text-slate-500">
        Không tìm thấy đơn hàng nào khớp với bộ lọc hiện tại.
      </div>
    );
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full border-collapse text-left">
        <thead>
          <tr className="border-b border-slate-200 bg-slate-50/80 text-[11px] font-semibold uppercase tracking-wider text-slate-500">
            {selectable && <th className="w-10 px-4 py-3" />}
            <th className="px-4 py-3">Mã đơn</th>
            <th className="px-4 py-3">Người nhận & Điểm giao</th>
            <th className="px-4 py-3">Trạng thái</th>
            <th className="px-4 py-3">Tài xế</th>
            <th className="px-4 py-3">Hàng & COD</th>
            {(onCancelOrder || onViewRoute) && <th className="px-4 py-3 text-right">Thao tác</th>}
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100 text-sm">
          {orders.map((order) => {
            const isSelected = selectedId === order.id;
            const driver = drivers.find(
              (item) => item.id === order.assigned_driver_id || item.user_id === order.assigned_driver_id,
            );
            const driverName = order.assigned_driver_name || driver?.name;
            const codAmount = Number(order.cod_amount ?? 0);
            const canCancel =
              order.status === 'READY_FOR_DISPATCH' ||
              order.status === 'PLANNED' ||
              order.status === 'PENDING_PAYMENT';
            const shortCode = formatShortOrderCode(order.order_code, order.id);

            return (
              <tr
                key={order.id}
                onClick={() => onViewRoute?.(order)}
                className={`transition-colors ${
                  onViewRoute ? 'cursor-pointer' : ''
                } ${
                  isSelected
                    ? 'bg-blue-50/75 hover:bg-blue-50'
                    : 'hover:bg-slate-50/90'
                }`}
              >
                {selectable && (
                  <td
                    className="px-4 py-3 align-top"
                    onClick={(event) => event.stopPropagation()}
                  >
                    <input
                      type="checkbox"
                      checked={selectedIds?.has(order.id) ?? false}
                      onChange={() => onToggleSelect?.(order)}
                      className="mt-1 h-4 w-4 rounded border-slate-300 text-blue-600"
                    />
                  </td>
                )}

                <td className=" whitespace-nowrap px-4 py-3 align-top">
                  <div
                    className="font-mono text-sm font-semibold text-slate-900"
                    title={`Mã đầy đủ: ${order.order_code}`}
                  >
                    {shortCode}
                  </div>
                  <div className="mt-0.5 text-xs tabular-nums text-slate-500">
                    {formatVNDate(order.created_at)}
                  </div>
                </td>

                <td className="max-w-md px-4 py-3 align-top">
                  <div className="font-medium text-slate-900">
                    {order.customer_name || 'Khách nhận hàng'}
                  </div>
                  <div
                    className="mt-0.5 line-clamp-1 text-xs text-slate-600"
                    title={order.delivery_address}
                  >
                    {order.delivery_address}
                  </div>
                  <div
                    className="mt-1 flex items-center gap-1 text-[11px] text-slate-500"
                    title={order.pickup_address}
                  >
                    <MapPin size={11} className="shrink-0 text-teal-700" />
                    <span className="truncate">
                      Kho lấy: {order.pickup_location_name || order.pickup_address}
                    </span>
                  </div>
                  {order.notes && (
                    <span
                      className={`mt-1.5 inline-block rounded px-2 py-0.5 text-[11px] font-medium ${
                        order.status === 'FAILED'
                          ? 'border border-rose-200 bg-rose-50 text-rose-700'
                          : order.status === 'CANCELLED'
                          ? 'bg-slate-100 text-slate-600'
                          : 'border border-amber-200 bg-amber-50 text-amber-700'
                      }`}
                    >
                      {order.notes}
                    </span>
                  )}
                </td>

                <td className="whitespace-nowrap px-4 py-3 align-top">
                  <OrderStatusBadge status={order.status} />
                </td>

                <td className="whitespace-nowrap px-4 py-3 align-top">
                  {order.assigned_driver_id ? (
                    <div>
                      <p className="font-medium text-slate-900">
                        {driverName || 'Đã phân công'}
                      </p>
                      <p className="mt-0.5 text-xs text-slate-500">
                        {driver?.vehicle_plate || driver?.vehicle_type || '—'}
                      </p>
                    </div>
                  ) : (
                    <span className="text-xs text-slate-400">Chưa phân công</span>
                  )}
                </td>

                <td className="whitespace-nowrap px-4 py-3 align-top tabular-nums">
                  <div className="text-xs font-medium text-slate-700">
                    {order.weight_kg} kg
                  </div>
                  {codAmount > 0 ? (
                    <div className="mt-0.5 text-xs font-semibold text-emerald-700">
                      COD {new Intl.NumberFormat('vi-VN').format(codAmount)} ₫
                    </div>
                  ) : (
                    <div className="mt-0.5 text-[11px] text-slate-400">Không COD</div>
                  )}
                </td>

                {(onCancelOrder || onViewRoute) && (
                  <td
                    className="whitespace-nowrap px-4 py-3 text-right align-top"
                    onClick={(event) => event.stopPropagation()}
                  >
                    <div className="inline-flex items-center justify-end gap-1.5">
                      {onViewRoute && (
                        <Button
                          size="sm"
                          variant={isSelected ? 'default' : 'outline'}
                          className="h-8 px-2.5 text-xs font-medium"
                          onClick={() => onViewRoute(order)}
                          aria-label={`Xem lộ trình đơn ${shortCode}`}
                        >
                          <Navigation size={13} />
                          {isSelected ? 'Đang xem' : 'Lộ trình'}
                        </Button>
                      )}
                      {onCancelOrder && canCancel && (
                        <Button
                          size="sm"
                          variant="ghost"
                          className="h-8 px-2 text-xs font-medium text-rose-600 hover:bg-rose-50 hover:text-rose-700"
                          onClick={() => {
                            const reason = window.prompt(
                              `Nhập lý do huỷ đơn hàng ${shortCode}:`,
                              'Khách hàng yêu cầu huỷ đơn',
                            );
                            if (reason !== null && reason.trim().length > 0) {
                              onCancelOrder(order.id, reason.trim());
                            }
                          }}
                        >
                          Huỷ
                        </Button>
                      )}
                    </div>
                  </td>
                )}
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
