'use client';

import { useMemo, useState } from 'react';
import {
  useReactTable,
  getCoreRowModel,
  flexRender,
  ColumnDef,
} from '@tanstack/react-table';
import { Order, OrderStatus } from '@/domain/entities/order.entity';
import { OrderStatusBadge } from './order-status-badge';
import { formatVNDate, formatDistance } from '@/lib/utils';
import { Button } from '@/components/ui/button';

interface OrderTableProps {
  orders: Order[];
  isLoading: boolean;
  onUpdateStatus?: (orderId: string, status: OrderStatus) => void;
  onCancelOrder?: (orderId: string, reason?: string) => void;
  selectable?: boolean;
  selectedIds?: Set<string>;
  onToggleSelect?: (order: Order) => void;
}

export function OrderTable({
  orders,
  isLoading,
  onUpdateStatus,
  onCancelOrder,
  selectable,
  selectedIds,
  onToggleSelect,
}: OrderTableProps) {
  const columns = useMemo<ColumnDef<Order>[]>(
    () => [
      ...(selectable
        ? [
            {
              id: 'select',
              header: '',
              cell: ({ row }: { row: { original: Order } }) => (
                <input
                  type="checkbox"
                  checked={selectedIds?.has(row.original.id) ?? false}
                  onChange={() => onToggleSelect?.(row.original)}
                  className="h-4 w-4 rounded border-gray-300 text-blue-600"
                />
              ),
            } as ColumnDef<Order>,
          ]
        : []),
      {
        accessorKey: 'order_code',
        header: 'Mã đơn',
        cell: ({ getValue }) => (
          <span className="font-mono text-sm font-medium text-blue-700">{getValue() as string}</span>
        ),
      },
      {
        accessorKey: 'tenant_name',
        header: 'Cửa hàng',
        cell: ({ row }) => (
          <span className="text-sm text-gray-700">{row.original.tenant_name || '—'}</span>
        ),
      },
      {
        accessorKey: 'delivery_address',
        header: 'Địa chỉ giao',
        cell: ({ row }) => (
          <div className="max-w-xs space-y-1">
            <span className="truncate text-sm text-gray-700 block">{row.original.delivery_address}</span>
            {row.original.notes && (
              <span
                className={`inline-block text-[11px] px-2 py-0.5 rounded font-medium ${
                  row.original.status === 'FAILED'
                    ? 'bg-rose-50 text-rose-700 border border-rose-200'
                    : row.original.status === 'CANCELLED'
                    ? 'bg-gray-100 text-gray-700'
                    : 'bg-amber-50 text-amber-700 border border-amber-200'
                }`}
              >
                {row.original.notes}
              </span>
            )}
          </div>
        ),
      },
      {
        accessorKey: 'weight_kg',
        header: 'Khối lượng',
        cell: ({ getValue }) => <span className="text-sm">{getValue() as number} kg</span>,
      },
      {
        accessorKey: 'status',
        header: 'Trạng thái',
        cell: ({ getValue }) => <OrderStatusBadge status={getValue() as OrderStatus} />,
      },
      {
        accessorKey: 'created_at',
        header: 'Tạo lúc',
        cell: ({ getValue }) => (
          <span className="text-xs text-gray-500">{formatVNDate(getValue() as string)}</span>
        ),
      },
      ...(onUpdateStatus || onCancelOrder
        ? [
            {
              id: 'actions',
              header: 'Thao tác',
              cell: ({ row }: { row: { original: Order } }) => {
                const order = row.original;
                const canCancel =
                  order.status === 'PAID' ||
                  order.status === 'PLANNED' ||
                  order.status === 'PENDING_PAYMENT';
                return (
                  <div className="flex items-center gap-2">
                    {onUpdateStatus && order.status === 'PAID' && (
                      <Button
                        size="sm"
                        variant="outline"
                        onClick={() => onUpdateStatus(order.id, 'PLANNED')}
                      >
                        Lên kế hoạch
                      </Button>
                    )}
                    {onCancelOrder && canCancel && (
                      <Button
                        size="sm"
                        variant="ghost"
                        className="text-red-600 hover:bg-red-50 hover:text-red-700 text-xs font-medium"
                        onClick={() => {
                          const reason = window.prompt(
                            `Nhập lý do huỷ đơn hàng #${order.order_code || order.id.slice(0, 8)}:`,
                            'Khách hàng yêu cầu huỷ đơn'
                          );
                          if (reason !== null && reason.trim().length > 0) {
                            onCancelOrder(order.id, reason.trim());
                          }
                        }}
                      >
                        Huỷ đơn
                      </Button>
                    )}
                  </div>
                );
              },
            } as ColumnDef<Order>,
          ]
        : []),
    ],
    [selectable, selectedIds, onToggleSelect, onUpdateStatus, onCancelOrder]
  );

  const table = useReactTable({ data: orders, columns, getCoreRowModel: getCoreRowModel() });

  if (isLoading) {
    return (
      <div className="flex h-40 items-center justify-center">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-blue-600 border-t-transparent" />
      </div>
    );
  }

  if (orders.length === 0) {
    return (
      <div className="flex h-40 items-center justify-center text-sm text-gray-400">
        Không có đơn hàng nào
      </div>
    );
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left">
        <thead>
          {table.getHeaderGroups().map((hg) => (
            <tr key={hg.id} className="border-b border-gray-200">
              {hg.headers.map((header) => (
                <th
                  key={header.id}
                  className="px-4 py-3 text-xs font-semibold uppercase tracking-wide text-gray-500"
                >
                  {flexRender(header.column.columnDef.header, header.getContext())}
                </th>
              ))}
            </tr>
          ))}
        </thead>
        <tbody>
          {table.getRowModel().rows.map((row) => (
            <tr
              key={row.id}
              className="border-b border-gray-100 hover:bg-gray-50 transition-colors"
            >
              {row.getVisibleCells().map((cell) => (
                <td key={cell.id} className="px-4 py-3">
                  {flexRender(cell.column.columnDef.cell, cell.getContext())}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

