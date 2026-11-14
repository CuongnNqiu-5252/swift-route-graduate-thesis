'use client';

import { useMemo } from 'react';
import {
  createColumnHelper,
  flexRender,
  getCoreRowModel,
  type PaginationState,
  type SortingState,
  useReactTable,
} from '@tanstack/react-table';
import { ArrowDown, ArrowUp, ArrowUpDown, MapPin, Navigation } from 'lucide-react';
import { useLocale } from '@/i18n/provider';
import { Order } from '@/domain/entities/order.entity';
import { Driver } from '@/domain/entities/driver.entity';
import { OrderSortDirection, OrderSortField } from '@/infrastructure/repositories/order.repository';
import { formatVNDate } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import { OrderStatusBadge } from './order-status-badge';
import { OrderPaginationBar } from './order-pagination-bar';

interface OrderTablePaginationProps {
  page: number;
  pageSize: number;
  totalOrders: number;
  totalPages: number;
  onPageChange: (nextPage: number) => void;
  onPageSizeChange: (nextPageSize: number) => void;
  onClearSelection?: () => void;
}

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
  sortBy?: OrderSortField;
  sortDir?: OrderSortDirection;
  onSortChange?: (sortBy: OrderSortField, sortDir: OrderSortDirection) => void;
  pagination?: OrderTablePaginationProps;
}

export function formatShortOrderCode(orderCode?: string, fallbackId?: string): string {
  const raw = (orderCode || fallbackId || '').trim();
  if (!raw) return '—';
  if (raw.length <= 14) return raw;
  return `#${raw.slice(0, 8).toUpperCase()}`;
}

const columnHelper = createColumnHelper<Order>();

/** Order table powered by TanStack Table with manual server-side sorting and pagination. */
export function OrderTable({
  orders,
  isLoading,
  onCancelOrder,
  selectable = false,
  selectedIds,
  onToggleSelect,
  drivers = [],
  selectedId,
  onViewRoute,
  sortBy = 'created_at',
  sortDir = 'desc',
  onSortChange,
  pagination,
}: OrderTableProps) {
  const { t, intlLocale } = useLocale();

  const driverByKey = useMemo(() => {
    const map = new Map<string, Driver>();
    drivers.forEach((driver) => {
      map.set(driver.id, driver);
      if (driver.user_id) map.set(driver.user_id, driver);
    });
    return map;
  }, [drivers]);

  const sortingState = useMemo<SortingState>(
    () => (onSortChange ? [{ id: sortBy, desc: sortDir === 'desc' }] : []),
    [onSortChange, sortBy, sortDir]
  );

  const paginationState = useMemo<PaginationState>(
    () => ({
      pageIndex: Math.max(0, (pagination?.page ?? 1) - 1),
      pageSize: pagination?.pageSize ?? (orders.length || 15),
    }),
    [orders.length, pagination?.page, pagination?.pageSize]
  );

  const columns = useMemo(() => {
    const list = [
      ...(selectable
        ? [
            columnHelper.display({
              id: 'select',
              header: () => <span className="sr-only">{t('Chọn')}</span>,
              cell: (info) => {
                const order = info.row.original;
                return (
                  <input
                    type="checkbox"
                    checked={selectedIds?.has(order.id) ?? false}
                    onChange={() => onToggleSelect?.(order)}
                    onClick={(event) => event.stopPropagation()}
                    className="mt-1 h-4 w-4 rounded border-slate-300 text-blue-600"
                  />
                );
              },
            }),
          ]
        : []),
      columnHelper.accessor('created_at', {
        id: 'created_at',
        enableSorting: Boolean(onSortChange),
        header: () => {
          if (!onSortChange) {
            return <span>{t('Mã đơn')}</span>;
          }
          const isSortedByCreated = sortBy === 'created_at';
          const Icon = !isSortedByCreated
            ? ArrowUpDown
            : sortDir === 'asc'
              ? ArrowUp
              : ArrowDown;
          return (
            <button
              type="button"
              onClick={() => {
                const nextDir: OrderSortDirection =
                  isSortedByCreated && sortDir === 'desc' ? 'asc' : 'desc';
                onSortChange('created_at', nextDir);
              }}
              className="inline-flex items-center gap-1.5 font-medium text-slate-600 transition-colors hover:text-slate-950"
              title={t('Bấm để đổi thứ tự ngày tạo đơn')}
            >
              <span>{t('Mã đơn · Ngày tạo')}</span>
              <Icon size={13} className={isSortedByCreated ? 'text-slate-900' : 'text-slate-400'} />
            </button>
          );
        },
        cell: (info) => {
          const order = info.row.original;
          const shortCode = formatShortOrderCode(order.order_code, order.id);
          return (
            <div className="whitespace-nowrap">
              <div className="font-mono text-sm font-semibold text-slate-900" title={`Mã đầy đủ: ${order.order_code}`}>
                {shortCode}
              </div>
              <div className="mt-0.5 text-xs tabular-nums text-slate-500">
                {formatVNDate(order.created_at)}
              </div>
              {sortBy === 'updated_at' && order.updated_at && (
                <div className="mt-0.5 text-[11px] tabular-nums text-slate-400">
                  {t('Cập nhật:')} {formatVNDate(order.updated_at)}
                </div>
              )}
              {sortBy === 'time_window_start' && order.time_window_start && (
                <div className="mt-0.5 text-[11px] tabular-nums text-slate-400">
                  {t('Hẹn giao:')} {formatVNDate(order.time_window_start)}
                </div>
              )}
            </div>
          );
        },
      }),
      columnHelper.display({
        id: 'recipient',
        header: () => <span>{t('Người nhận & Điểm giao')}</span>,
        cell: (info) => {
          const order = info.row.original;
          return (
            <div className="max-w-md">
              <div className="font-medium text-slate-900">
                {order.customer_name || t('Khách nhận hàng')}
              </div>
              <div className="mt-0.5 line-clamp-1 text-xs text-slate-600" title={order.delivery_address}>
                {order.delivery_address}
              </div>
              <div className="mt-1 flex items-center gap-1 text-[11px] text-slate-500" title={order.pickup_address}>
                <MapPin size={11} className="shrink-0 text-teal-700" />
                <span className="truncate">
                  {t('Kho lấy:')} {order.pickup_location_name || order.pickup_address}
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
            </div>
          );
        },
      }),
      columnHelper.accessor('status', {
        header: () => <span>{t('Trạng thái')}</span>,
        cell: (info) => {
          const order = info.row.original;
          return (
            <div className="whitespace-nowrap">
              <OrderStatusBadge status={order.status} fulfillmentStatus={order.fulfillment_status} />
            </div>
          );
        },
      }),
      columnHelper.display({
        id: 'driver',
        header: () => <span>{t('Tài xế')}</span>,
        cell: (info) => {
          const order = info.row.original;
          const driver = order.assigned_driver_id ? driverByKey.get(order.assigned_driver_id) : undefined;
          const driverName = order.assigned_driver_name || driver?.name;
          if (!order.assigned_driver_id) {
            return <span className="whitespace-nowrap text-xs text-slate-400">{t('Chưa phân công')}</span>;
          }
          return (
            <div className="whitespace-nowrap">
              <p className="font-medium text-slate-900">{driverName || t('Đã phân công')}</p>
              <p className="mt-0.5 text-xs text-slate-500">
                {driver?.vehicle_plate || driver?.vehicle_type || '—'}
              </p>
            </div>
          );
        },
      }),
      columnHelper.accessor('cod_amount', {
        header: () => <span>{t('Hàng & COD')}</span>,
        cell: (info) => {
          const order = info.row.original;
          const codAmount = Number(order.cod_amount ?? 0);
          return (
            <div className="whitespace-nowrap tabular-nums">
              <div className="text-xs font-medium text-slate-700">{order.weight_kg} kg</div>
              {codAmount > 0 ? (
                <div className="mt-0.5 text-xs font-semibold text-emerald-700">
                  COD {new Intl.NumberFormat(intlLocale).format(codAmount)} ₫
                </div>
              ) : (
                <div className="mt-0.5 text-[11px] text-slate-400">{t('Không COD')}</div>
              )}
            </div>
          );
        },
      }),
      ...(onCancelOrder || onViewRoute
        ? [
            columnHelper.display({
              id: 'actions',
              header: () => <span className="block text-right">{t('Thao tác')}</span>,
              cell: (info) => {
                const order = info.row.original;
                const isSelected = selectedId === order.id;
                const canCancel = order.status === 'READY_FOR_DISPATCH' || order.status === 'PLANNED';
                const shortCode = formatShortOrderCode(order.order_code, order.id);
                return (
                  <div
                    className="inline-flex w-full items-center justify-end gap-1.5 whitespace-nowrap"
                    onClick={(event) => event.stopPropagation()}
                  >
                    {onViewRoute && (
                      <Button
                        size="sm"
                        variant={isSelected ? 'default' : 'outline'}
                        className="h-8 px-2.5 text-xs font-medium"
                        onClick={() => onViewRoute(order)}
                        aria-label={`Xem lộ trình đơn ${shortCode}`}
                      >
                        <Navigation size={13} />
                        {isSelected ? t('Đang xem') : t('Lộ trình')}
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
                            t('Khách hàng yêu cầu huỷ đơn')
                          );
                          if (reason !== null && reason.trim().length > 0) {
                            onCancelOrder(order.id, reason.trim());
                          }
                        }}
                      >
                        {t('Huỷ')}
                      </Button>
                    )}
                  </div>
                );
              },
            }),
          ]
        : []),
    ];
    return list;
  }, [
    driverByKey,
    intlLocale,
    onCancelOrder,
    onSortChange,
    onToggleSelect,
    onViewRoute,
    selectable,
    selectedId,
    selectedIds,
    sortBy,
    sortDir,
    t,
  ]);

  // TanStack Table handles headless row/pagination/sorting coordination cleanly.
  // eslint-disable-next-line react-hooks/incompatible-library
  const table = useReactTable({
    data: orders,
    columns,
    getCoreRowModel: getCoreRowModel(),
    manualSorting: true,
    manualPagination: true,
    enableSortingRemoval: false,
    pageCount: pagination?.totalPages ?? 1,
    state: {
      sorting: sortingState,
      pagination: paginationState,
    },
    onSortingChange: (updater) => {
      if (!onSortChange) return;
      const next = typeof updater === 'function' ? updater(sortingState) : updater;
      const primary = next[0];
      if (!primary) return;
      const field =
        primary.id === 'updated_at' || primary.id === 'time_window_start' ? primary.id : 'created_at';
      onSortChange(field, primary.desc ? 'desc' : 'asc');
    },
    onPaginationChange: (updater) => {
      if (!pagination) return;
      const next = typeof updater === 'function' ? updater(paginationState) : updater;
      if (next.pageSize !== pagination.pageSize) {
        pagination.onPageSizeChange(next.pageSize);
      } else if (next.pageIndex !== pagination.page - 1) {
        pagination.onPageChange(next.pageIndex + 1);
      }
    },
  });

  return (
    <div>
      {isLoading ? (
        <div className="flex h-48 items-center justify-center">
          <div className="h-7 w-7 animate-spin rounded-full border-3 border-slate-900 border-t-transparent" />
        </div>
      ) : orders.length === 0 ? (
        <div className="flex h-44 flex-col items-center justify-center px-4 text-center text-sm text-slate-500">
          {t('Không tìm thấy đơn hàng nào khớp với bộ lọc hiện tại.')}
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full border-collapse text-left">
            <thead>
              {table.getHeaderGroups().map((headerGroup) => (
                <tr
                  key={headerGroup.id}
                  className="border-b border-slate-200 bg-slate-50/80 text-xs font-medium text-slate-500"
                >
                  {headerGroup.headers.map((header) => (
                    <th
                      key={header.id}
                      className={header.id === 'select' ? 'w-10 px-4 py-3' : 'px-4 py-3 font-medium'}
                    >
                      {header.isPlaceholder
                        ? null
                        : flexRender(header.column.columnDef.header, header.getContext())}
                    </th>
                  ))}
                </tr>
              ))}
            </thead>
            <tbody className="divide-y divide-slate-100 text-sm">
              {table.getRowModel().rows.map((row) => {
                const order = row.original;
                const isSelected = selectedId === order.id;
                return (
                  <tr
                    key={row.id}
                    onClick={() => onViewRoute?.(order)}
                    className={`transition-colors ${
                      onViewRoute ? 'cursor-pointer' : ''
                    } ${isSelected ? 'bg-blue-50/75 hover:bg-blue-50' : 'hover:bg-slate-50/90'}`}
                  >
                    {row.getVisibleCells().map((cell) => (
                      <td
                        key={cell.id}
                        className={`px-4 py-3 align-top ${cell.column.id === 'actions' ? 'text-right' : ''}`}
                      >
                        {flexRender(cell.column.columnDef.cell, cell.getContext())}
                      </td>
                    ))}
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {pagination && (
        <OrderPaginationBar
          table={table}
          totalOrders={pagination.totalOrders}
          isLoading={isLoading}
          onClearSelection={pagination.onClearSelection}
        />
      )}
    </div>
  );
}
