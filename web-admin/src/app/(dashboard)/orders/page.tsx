'use client';

import { useMemo, useState } from 'react';
import Link from 'next/link';
import { useOrders } from '@/application/hooks/use-orders';
import { OrderStatus } from '@/domain/entities/order.entity';
import { OrderTable, formatShortOrderCode } from '@/components/orders/order-table';
import { OrderStatusBadge } from '@/components/orders/order-status-badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import {
  Bike,
  ChevronLeft,
  ChevronRight,
  Copy,
  MapPin,
  PackageSearch,
  Plus,
  RefreshCw,
  Route,
  Search,
  UserRound,
  Warehouse,
  X,
} from 'lucide-react';
import { toast } from 'sonner';
import { OrderCreatePanel } from '@/components/orders/order-create-panel';
import OrderTrackingMap from '@/components/orders/order-tracking-map';
import { useOrderLiveTracking } from '@/application/hooks/use-order-live-tracking';
import { useDrivers } from '@/application/hooks/use-drivers';

const STATUS_TABS: { label: string; value?: OrderStatus }[] = [
  { label: 'Tất cả' },
  { label: 'Chờ điều phối', value: 'READY_FOR_DISPATCH' },
  { label: 'Đã lên lịch', value: 'PLANNED' },
  { label: 'Đang giao', value: 'IN_TRANSIT' },
  { label: 'Đã giao', value: 'DELIVERED' },
  { label: 'Sự cố', value: 'FAILED' },
  { label: 'Đã hủy', value: 'CANCELLED' },
];

const PAGE_SIZE = 15;

export default function OrdersPage() {
  const [activeTab, setActiveTab] = useState<OrderStatus | undefined>(undefined);
  const { orders, isLoading, error, refetch, cancelOrder } = useOrders();
  const { drivers } = useDrivers();
  const [showCreate, setShowCreate] = useState(false);
  const [selectedId, setSelectedId] = useState('');
  const [query, setQuery] = useState('');
  const [warehouseFilter, setWarehouseFilter] = useState('');
  const [page, setPage] = useState(1);

  const normalizedQuery = normalizeSearch(query);

  const statusCounts = useMemo(() => {
    const counts: Record<string, number> = { ALL: orders.length };
    for (const order of orders) {
      counts[order.status] = (counts[order.status] || 0) + 1;
    }
    return counts;
  }, [orders]);

  const warehouseOptions = useMemo(() => {
    const map = new Map<string, string>();
    for (const order of orders) {
      const key = order.pickup_location_id || order.pickup_location_name || order.pickup_address;
      const label = order.pickup_location_name || order.pickup_address;
      if (key && label && !map.has(key)) {
        map.set(key, label);
      }
    }
    return Array.from(map.entries()).map(([value, label]) => ({ value, label }));
  }, [orders]);

  const filteredOrders = useMemo(() => {
    return orders.filter((order) => {
      if (activeTab && order.status !== activeTab) return false;
      if (warehouseFilter) {
        const key = order.pickup_location_id || order.pickup_location_name || order.pickup_address;
        if (key !== warehouseFilter) return false;
      }
      if (!normalizedQuery) return true;

      const driver = drivers.find(
        (item) => item.id === order.assigned_driver_id || item.user_id === order.assigned_driver_id,
      );
      const shortCode = formatShortOrderCode(order.order_code, order.id);
      return [
        order.order_code,
        shortCode,
        order.customer_name,
        order.pickup_location_name,
        order.pickup_address,
        order.delivery_address,
        order.assigned_driver_name,
        driver?.name,
        driver?.vehicle_plate,
      ].some((value) => normalizeSearch(value).includes(normalizedQuery));
    });
  }, [activeTab, drivers, normalizedQuery, orders, warehouseFilter]);

  const totalPages = Math.max(1, Math.ceil(filteredOrders.length / PAGE_SIZE));
  const safePage = Math.min(page, totalPages);
  const paginatedOrders = useMemo(() => {
    const start = (safePage - 1) * PAGE_SIZE;
    return filteredOrders.slice(start, start + PAGE_SIZE);
  }, [filteredOrders, safePage]);

  const selected = useMemo(
    () => (selectedId ? orders.find((order) => order.id === selectedId) : undefined),
    [orders, selectedId],
  );
  const selectedIndexInFiltered = useMemo(
    () => (selected ? filteredOrders.findIndex((order) => order.id === selected.id) : -1),
    [filteredOrders, selected],
  );

  const tracking = useOrderLiveTracking(selected?.id, selected?.assigned_driver_id);
  const selectedDriver = drivers.find(
    (driver) => driver.id === selected?.assigned_driver_id || driver.user_id === selected?.assigned_driver_id,
  );
  const selectedDriverName = selected?.assigned_driver_name || selectedDriver?.name;

  const handleToggleRoute = (orderId: string) => {
    setSelectedId((prev) => {
      const next = prev === orderId ? '' : orderId;
      if (next && typeof window !== 'undefined' && window.innerWidth < 1280) {
        window.requestAnimationFrame(() => {
          document.getElementById('route-workspace')?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
        });
      }
      return next;
    });
  };

  const handleCancelOrder = async (orderId: string, reason?: string) => {
    try {
      await cancelOrder(orderId, reason);
      toast.success('Đã huỷ đơn hàng thành công');
    } catch {
      toast.error('Lỗi khi huỷ đơn hàng');
    }
  };

  const copyOrderCode = (code: string) => {
    navigator.clipboard?.writeText(code);
    toast.success('Đã sao chép mã đơn');
  };

  return (
    <div className="space-y-4">
      {/* Compact Header */}
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-xl font-bold tracking-tight text-slate-950 sm:text-2xl">Quản lý đơn hàng</h1>
          <p className="text-xs text-slate-500 sm:text-sm">
            Tìm kiếm nhanh trong hàng trăm đơn và bấm vào từng đơn để xem lộ trình trực tiếp
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Button size="sm" onClick={() => setShowCreate((value) => !value)}>
            <Plus size={15} />
            {showCreate ? 'Đóng tạo đơn' : 'Tạo đơn'}
          </Button>
          <Button variant="outline" size="sm" onClick={() => refetch()} isLoading={isLoading}>
            <RefreshCw size={14} /> Làm mới
          </Button>
          <Link href="/dispatch">
            <Button size="sm" variant="outline">
              <Route size={15} /> Điều phối
            </Button>
          </Link>
        </div>
      </div>

      {showCreate && (
        <OrderCreatePanel
          onCreated={() => {
            setShowCreate(false);
            void refetch();
          }}
          onClose={() => setShowCreate(false)}
        />
      )}

      {/* Status Tabs with Live Counts */}
      <div className="flex items-center gap-1 overflow-x-auto border-b border-slate-200">
        {STATUS_TABS.map((tab) => {
          const isActive = activeTab === tab.value;
          const count = tab.value ? statusCounts[tab.value] || 0 : statusCounts.ALL || 0;
          return (
            <button
              key={tab.label}
              type="button"
              onClick={() => {
                setActiveTab(tab.value);
                setPage(1);
              }}
              className={`inline-flex min-h-10 shrink-0 items-center gap-1.5 border-b-2 px-3 py-2 text-xs font-medium transition-colors sm:text-sm ${
                isActive
                  ? 'border-slate-950 font-semibold text-slate-950'
                  : 'border-transparent text-slate-600 hover:bg-slate-100/80 hover:text-slate-900'
              }`}
            >
              <span>{tab.label}</span>
              <span
                className={`rounded-full px-1.5 py-0.5 text-[11px] tabular-nums ${
                  isActive ? 'bg-slate-900 text-white' : 'bg-slate-100 text-slate-600'
                }`}
              >
                {count}
              </span>
            </button>
          );
        })}
      </div>

      {/* Unified Search & Warehouse Filter Toolbar */}
      <div className="flex flex-col gap-2.5 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex flex-1 flex-col gap-2 sm:flex-row sm:items-center">
          <div className="relative w-full sm:max-w-md">
            <Search
              aria-hidden="true"
              className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
              size={16}
            />
            <input
              type="text"
              role="searchbox"
              enterKeyHint="search"
              value={query}
              onChange={(event) => {
                setQuery(event.target.value);
                setPage(1);
              }}
              placeholder="Tìm mã đơn (#611D...), tên người nhận, địa chỉ, tài xế, biển số…"
              aria-label="Tìm kiếm đơn hàng"
              className="h-10 w-full rounded-lg border border-slate-300 bg-white pl-9 pr-9 text-sm text-slate-950 placeholder:text-slate-400 focus:border-blue-600 focus:outline-none focus:ring-2 focus:ring-blue-600/20"
            />
            {query && (
              <button
                type="button"
                onClick={() => {
                  setQuery('');
                  setPage(1);
                }}
                aria-label="Xóa nội dung tìm kiếm"
                className="absolute right-1.5 top-1/2 flex h-7 w-7 -translate-y-1/2 items-center justify-center rounded-md text-slate-500 hover:bg-slate-100 hover:text-slate-900"
              >
                <X size={15} />
              </button>
            )}
          </div>

          {warehouseOptions.length > 1 && (
            <div className="relative sm:w-56">
              <Warehouse
                aria-hidden="true"
                className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
                size={15}
              />
              <select
                aria-label="Lọc theo kho lấy hàng"
                value={warehouseFilter}
                onChange={(event) => {
                  setWarehouseFilter(event.target.value);
                  setPage(1);
                }}
                className="h-10 w-full rounded-lg border border-slate-300 bg-white pl-9 pr-3 text-sm text-slate-800 focus:border-blue-600 focus:outline-none"
              >
                <option value="">Tất cả kho lấy ({warehouseOptions.length})</option>
                {warehouseOptions.map((item) => (
                  <option key={item.value} value={item.value}>
                    {item.label}
                  </option>
                ))}
              </select>
            </div>
          )}

          {(query || activeTab || warehouseFilter) && (
            <button
              type="button"
              onClick={() => {
                setQuery('');
                setActiveTab(undefined);
                setWarehouseFilter('');
                setPage(1);
              }}
              className="self-start text-xs font-medium text-blue-600 hover:text-blue-800 sm:self-center"
            >
              Xóa bộ lọc
            </button>
          )}
        </div>

        <p role="status" className="shrink-0 text-xs tabular-nums text-slate-500 sm:text-sm">
          {isLoading
            ? 'Đang tải đơn hàng…'
            : `Hiển thị ${paginatedOrders.length} / ${filteredOrders.length} đơn`}
        </p>
      </div>

      {error && (
        <p role="alert" className="text-sm text-rose-700">
          Không tải được danh sách đơn. Hãy thử làm mới.
        </p>
      )}

      {!isLoading && orders.length === 0 ? (
        <div className="flex min-h-60 flex-col items-center justify-center rounded-xl border border-slate-200 bg-slate-50/50 text-center">
          <PackageSearch className="text-slate-400" size={28} />
          <p className="mt-3 font-medium text-slate-900">Chưa có đơn hàng nào trong hệ thống</p>
          <p className="mt-1 text-sm text-slate-500">Bấm &ldquo;Tạo đơn&rdquo; ở góc trên để thêm đơn vận chuyển mới.</p>
        </div>
      ) : (
        /* Main Workspace: Table on the left, Contextual Route Inspector on the right when an order is selected */
        <div
          className={`grid items-start gap-4 ${
            selected ? 'xl:grid-cols-[minmax(0,1fr)_27rem]' : 'grid-cols-1'
          }`}
        >
          {/* Orders Table + Pagination */}
          <Card className="overflow-hidden border-slate-200 shadow-none">
            <CardContent className="p-0">
              <OrderTable
                orders={paginatedOrders}
                isLoading={isLoading}
                drivers={drivers}
                selectedId={selected?.id}
                onViewRoute={(order) => handleToggleRoute(order.id)}
                onCancelOrder={handleCancelOrder}
              />

              {filteredOrders.length > PAGE_SIZE && (
                <div className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-200 bg-slate-50/70 px-4 py-3 text-xs text-slate-600">
                  <span className="tabular-nums">
                    Trang <strong>{safePage}</strong> / {totalPages} ({filteredOrders.length} đơn)
                  </span>
                  <div className="flex items-center gap-1.5">
                    <Button
                      size="sm"
                      variant="outline"
                      disabled={safePage <= 1}
                      onClick={() => setPage((current) => Math.max(1, current - 1))}
                      className="h-8 px-2.5 text-xs"
                    >
                      <ChevronLeft size={14} /> Trước
                    </Button>
                    <Button
                      size="sm"
                      variant="outline"
                      disabled={safePage >= totalPages}
                      onClick={() => setPage((current) => Math.min(totalPages, current + 1))}
                      className="h-8 px-2.5 text-xs"
                    >
                      Tiếp <ChevronRight size={14} />
                    </Button>
                  </div>
                </div>
              )}
            </CardContent>
          </Card>

          {/* Contextual Route & Live Tracking Inspector (Only visible when an order is selected) */}
          {selected && (
            <aside
              id="route-workspace"
              aria-label="Chi tiết lộ trình đơn hàng"
              className="overflow-hidden rounded-xl border border-slate-200 bg-white xl:sticky xl:top-2"
            >
              <div className="flex items-center justify-between gap-2 border-b border-slate-200 bg-slate-50 px-4 py-3">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <span
                      className="font-mono text-sm font-bold text-slate-950"
                      title={selected.order_code}
                    >
                      {formatShortOrderCode(selected.order_code, selected.id)}
                    </span>
                    <button
                      type="button"
                      onClick={() => copyOrderCode(selected.order_code)}
                      title="Sao chép mã đơn đầy đủ"
                      className="rounded p-1 text-slate-400 hover:bg-slate-200/70 hover:text-slate-700"
                    >
                      <Copy size={13} />
                    </button>
                    <OrderStatusBadge status={selected.status} />
                  </div>
                  <p className="mt-0.5 truncate text-xs text-slate-600">
                    Người nhận: <strong className="font-medium text-slate-900">{selected.customer_name || 'Khách nhận hàng'}</strong>
                  </p>
                </div>

                <div className="flex shrink-0 items-center gap-1">
                  {selectedIndexInFiltered > 0 && (
                    <button
                      type="button"
                      onClick={() => setSelectedId(filteredOrders[selectedIndexInFiltered - 1].id)}
                      title="Đơn trước trong danh sách"
                      className="flex h-8 w-8 items-center justify-center rounded-lg border border-slate-200 bg-white text-slate-600 hover:bg-slate-100"
                    >
                      <ChevronLeft size={15} />
                    </button>
                  )}
                  {selectedIndexInFiltered >= 0 && selectedIndexInFiltered < filteredOrders.length - 1 && (
                    <button
                      type="button"
                      onClick={() => setSelectedId(filteredOrders[selectedIndexInFiltered + 1].id)}
                      title="Đơn tiếp theo trong danh sách"
                      className="flex h-8 w-8 items-center justify-center rounded-lg border border-slate-200 bg-white text-slate-600 hover:bg-slate-100"
                    >
                      <ChevronRight size={15} />
                    </button>
                  )}
                  <button
                    type="button"
                    onClick={() => setSelectedId('')}
                    aria-label="Đóng bản đồ lộ trình"
                    title="Đóng lộ trình"
                    className="flex h-8 w-8 items-center justify-center rounded-lg border border-slate-200 bg-white text-slate-500 hover:bg-slate-100 hover:text-slate-900"
                  >
                    <X size={16} />
                  </button>
                </div>
              </div>

              <div className="border-b border-slate-200 px-4 py-2.5">
                <TrackingSignal
                  signal={tracking.signal}
                  lastUpdate={tracking.lastUpdate}
                  accuracy={tracking.accuracy}
                  assigned={Boolean(selected.assigned_driver_id)}
                />
              </div>

              <OrderTrackingMap
                pickup={[selected.pickup_lat, selected.pickup_lng]}
                delivery={[selected.delivery_lat, selected.delivery_lng]}
                driver={tracking.position}
                driverName={selectedDriverName}
                orderCode={formatShortOrderCode(selected.order_code, selected.id)}
              />

              <div className="space-y-3 divide-y divide-slate-100 p-4 text-xs">
                <div className="flex items-start gap-2.5">
                  <UserRound size={15} className="mt-0.5 shrink-0 text-slate-400" />
                  <div className="min-w-0 flex-1">
                    <span className="text-slate-500">Tài xế phụ trách</span>
                    <p className="mt-0.5 font-semibold text-slate-900">
                      {selectedDriverName || (selected.assigned_driver_id ? 'Tài xế đã phân công' : 'Chưa phân công tài xế')}
                    </p>
                    {selectedDriver && (
                      <p className="mt-0.5 flex items-center gap-1 text-slate-600">
                        <Bike size={12} />
                        {selectedDriver.vehicle_plate || selectedDriver.vehicle_type || 'Chưa có biển số'}
                      </p>
                    )}
                  </div>
                </div>

                <div className="flex items-start gap-2.5 pt-3">
                  <MapPin size={15} className="mt-0.5 shrink-0 text-teal-700" />
                  <div className="min-w-0 flex-1 space-y-2">
                    <div>
                      <span className="text-slate-500">
                        Điểm lấy hàng {selected.pickup_location_name ? `(${selected.pickup_location_name})` : ''}
                      </span>
                      <p className="mt-0.5 text-slate-800">{selected.pickup_address}</p>
                    </div>
                    <div>
                      <span className="text-slate-500">Điểm giao hàng</span>
                      <p className="mt-0.5 font-medium text-slate-950">{selected.delivery_address}</p>
                    </div>
                  </div>
                </div>
              </div>
            </aside>
          )}
        </div>
      )}
    </div>
  );
}

function TrackingSignal({
  signal,
  lastUpdate,
  accuracy,
  assigned,
}: {
  signal: 'waiting' | 'live' | 'stale' | 'reconnecting';
  lastUpdate?: Date;
  accuracy?: number;
  assigned: boolean;
}) {
  if (!assigned) {
    return <p className="text-xs text-slate-500">Đơn hàng chưa được phân công tài xế</p>;
  }
  const presentation =
    signal === 'live'
      ? ['bg-emerald-500', 'Đang nhận GPS trực tiếp']
      : signal === 'stale'
      ? ['bg-amber-500', 'Tín hiệu GPS đã cũ']
      : signal === 'reconnecting'
      ? ['bg-rose-500', 'Đang kết nối lại luồng GPS']
      : ['bg-slate-400', 'Đang chờ vị trí mới từ tài xế'];
  return (
    <div className="flex flex-wrap items-center justify-between gap-2 text-xs">
      <span className="flex items-center gap-2 font-medium text-slate-800">
        <span className={`h-2 w-2 rounded-full ${presentation[0]}`} />
        {presentation[1]}
      </span>
      {lastUpdate && (
        <span className="text-slate-500">
          {lastUpdate.toLocaleTimeString('vi-VN')}
          {accuracy != null ? ` · ±${Math.round(accuracy)}m` : ''}
        </span>
      )}
    </div>
  );
}

function normalizeSearch(value?: string) {
  return (value || '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .toLowerCase()
    .trim();
}
