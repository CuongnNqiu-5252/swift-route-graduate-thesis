'use client';

import { useLocale } from '@/i18n/provider';

import { useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { useOrderPage } from '@/application/hooks/use-order-page';
import {
  orderRepository,
  type OrderSortDirection,
  type OrderSortField,
} from '@/infrastructure/repositories/order.repository';
import { pickupLocationRepository } from '@/infrastructure/repositories/pickup-location.repository';
import { OrderStatus } from '@/domain/entities/order.entity';
import { OrderTable, formatShortOrderCode } from '@/components/orders/order-table';
import { OrderStatusBadge } from '@/components/orders/order-status-badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import {
  ArrowUpDown,
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
  { label: 'Chuyển hoàn', value: 'RETURNED' },
  { label: 'Đã hủy', value: 'CANCELLED' },
];

export default function OrdersPage() {
  const { t } = useLocale();
  const [activeTab, setActiveTab] = useState<OrderStatus | undefined>(undefined);
  const { drivers } = useDrivers();
  const [showCreate, setShowCreate] = useState(false);
  const [selectedId, setSelectedId] = useState('');
  const [query, setQuery] = useState('');
  const [warehouseFilter, setWarehouseFilter] = useState('');
  const [sortBy, setSortBy] = useState<OrderSortField>('created_at');
  const [sortDir, setSortDir] = useState<OrderSortDirection>('desc');
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(15);

  const [warehouseOptions, setWarehouseOptions] = useState<{ value: string; label: string }[]>([]);
  useEffect(() => {
    let active = true;
    pickupLocationRepository.getPickupLocations()
      .then((locations) => {
        if (active) setWarehouseOptions(locations.map(({ id, name }) => ({ value: id, label: name })));
      })
      .catch(() => {});
    return () => { active = false; };
  }, []);

  const normalizedQuery = normalizeSearch(query);
  const driverIds = useMemo(() => normalizedQuery ? drivers
    .filter((driver) => [driver.name, driver.vehicle_plate].some((value) => normalizeSearch(value).includes(normalizedQuery)))
    .flatMap((driver) => [driver.user_id, driver.id].filter(Boolean)) : [], [drivers, normalizedQuery]);
  const filters = useMemo(() => ({
    page: page - 1,
    size: pageSize,
    status: activeTab,
    query: query.trim(),
    pickupLocationId: warehouseFilter,
    driverIds,
    sortBy,
    sortDir,
  }), [activeTab, driverIds, page, pageSize, query, sortBy, sortDir, warehouseFilter]);
  const { data, isLoading, error, refetch } = useOrderPage(filters);
  const paginatedOrders = data?.orders.content ?? [];
  const totalOrders = data?.orders.total_elements ?? 0;
  const totalPages = Math.max(1, data?.orders.total_pages ?? 1);
  const statusCounts = data?.status_counts;
  const allOrders = statusCounts ? Object.values(statusCounts).reduce((sum, count) => sum + count, 0) : undefined;
  const selectedIndexOnPage = paginatedOrders.findIndex((order) => order.id === selectedId);
  const selected = paginatedOrders[selectedIndexOnPage];

  useEffect(() => {
    if (data && page > totalPages) {
      queueMicrotask(() => setPage((current) => Math.min(current, totalPages)));
    }
  }, [data, page, totalPages]);

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
      await orderRepository.cancelOrder(orderId, reason);
      refetch();
      toast.success(t("Đã huỷ đơn hàng thành công"));
    } catch {
      toast.error(t("Lỗi khi huỷ đơn hàng"));
    }
  };

  const copyOrderCode = (code: string) => {
    navigator.clipboard?.writeText(code);
    toast.success(t("Đã sao chép mã đơn"));
  };

  return (
    <div className="space-y-4">
      {/* Compact Header */}
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-xl font-bold tracking-tight text-slate-950 sm:text-2xl">{t("Quản lý đơn hàng")}</h1>
          <p className="text-xs text-slate-500 sm:text-sm">
            {t("Tìm kiếm nhanh trong hàng trăm đơn và bấm vào từng đơn để xem lộ trình trực tiếp")}
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Button size="sm" onClick={() => setShowCreate((value) => !value)}>
            <Plus size={15} />
            {showCreate ? t("Đóng tạo đơn") : t("Tạo đơn")}
          </Button>
          <Button variant="outline" size="sm" onClick={() => refetch()} isLoading={isLoading}>
            <RefreshCw size={14} />{" "}{t("Làm mới")}
          </Button>
          <Link href="/dispatch">
            <Button size="sm" variant="outline">
              <Route size={15} />{" "}{t("Điều phối")}
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
          const count = tab.value
            ? statusCounts?.[tab.value === 'READY_FOR_DISPATCH' ? 'PAID' : tab.value] ?? (statusCounts ? 0 : '—')
            : allOrders ?? '—';
          return (
            <button
              key={t(tab.label)}
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
              <span>{t(tab.label)}</span>
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
              maxLength={200}
              enterKeyHint="search"
              value={query}
              onChange={(event) => {
                setQuery(event.target.value);
                setPage(1);
              }}
              placeholder={t("Tìm mã đơn (#611D...), tên người nhận, địa chỉ, tài xế, biển số…")}
              aria-label={t("Tìm kiếm đơn hàng")}
              className="h-10 w-full rounded-lg border border-slate-300 bg-white pl-9 pr-9 text-sm text-slate-950 placeholder:text-slate-400 focus:border-blue-600 focus:outline-none focus:ring-2 focus:ring-blue-600/20"
            />
            {query && (
              <button
                type="button"
                onClick={() => {
                  setQuery('');
                  setPage(1);
                }}
                aria-label={t("Xóa nội dung tìm kiếm")}
                className="absolute right-1.5 top-1/2 flex h-7 w-7 -translate-y-1/2 items-center justify-center rounded-md text-slate-500 hover:bg-slate-100 hover:text-slate-900"
              >
                <X size={15} />
              </button>
            )}
          </div>

          {warehouseOptions.length > 0 && (
            <div className="relative sm:w-56">
              <Warehouse
                aria-hidden="true"
                className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
                size={15}
              />
              <select
                aria-label={t("Lọc theo kho lấy hàng")}
                value={warehouseFilter}
                onChange={(event) => {
                  setWarehouseFilter(event.target.value);
                  setPage(1);
                }}
                className="h-10 w-full rounded-lg border border-slate-300 bg-white pl-9 pr-3 text-sm text-slate-800 focus:border-blue-600 focus:outline-none"
              >
                <option value="">{t("Tất cả kho lấy (")}{warehouseOptions.length})</option>
                {warehouseOptions.map((item) => (
                  <option key={item.value} value={item.value}>
                    {item.label}
                  </option>
                ))}
              </select>
            </div>
          )}

          <div className="relative sm:w-56">
            <ArrowUpDown
              aria-hidden="true"
              className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
              size={15}
            />
            <select
              aria-label={t("Sắp xếp đơn theo ngày")}
              value={`${sortBy}:${sortDir}`}
              onChange={(event) => {
                const [nextField, nextDir] = event.target.value.split(':') as [OrderSortField, OrderSortDirection];
                setSortBy(nextField);
                setSortDir(nextDir);
                setPage(1);
                setSelectedId('');
              }}
              className="h-10 w-full rounded-lg border border-slate-300 bg-white pl-9 pr-3 text-sm text-slate-800 focus:border-blue-600 focus:outline-none"
            >
              <option value="created_at:desc">{t("Ngày tạo: Mới nhất trước")}</option>
              <option value="created_at:asc">{t("Ngày tạo: Cũ nhất trước")}</option>
              <option value="updated_at:desc">{t("Cập nhật: Mới nhất trước")}</option>
              <option value="updated_at:asc">{t("Cập nhật: Cũ nhất trước")}</option>
              <option value="time_window_start:asc">{t("Hẹn giao: Sớm nhất trước")}</option>
              <option value="time_window_start:desc">{t("Hẹn giao: Muộn nhất trước")}</option>
            </select>
          </div>

          {(query || activeTab || warehouseFilter || sortBy !== 'created_at' || sortDir !== 'desc') && (
            <button
              type="button"
              onClick={() => {
                setQuery('');
                setActiveTab(undefined);
                setWarehouseFilter('');
                setSortBy('created_at');
                setSortDir('desc');
                setPage(1);
                setSelectedId('');
              }}
              className="self-start text-xs font-medium text-blue-600 hover:text-blue-800 sm:self-center"
            >
              {t("Xóa bộ lọc")}
            </button>
          )}
        </div>

        <p role="status" className="shrink-0 text-xs tabular-nums text-slate-500 sm:text-sm">
          {isLoading
            ? t("Đang tải đơn hàng…")
            : error ? "—" : `${t("Hiển thị")} ${paginatedOrders.length} / ${totalOrders} ${t("đơn")}`}
        </p>
      </div>

      {error && (
        <p role="alert" className="text-sm text-rose-700">
          {t("Không tải được danh sách đơn. Hãy thử làm mới.")}
        </p>
      )}

      {!isLoading && !error && allOrders === 0 ? (
        <div className="flex min-h-60 flex-col items-center justify-center rounded-xl border border-slate-200 bg-slate-50/50 text-center">
          <PackageSearch className="text-slate-400" size={28} />
          <p className="mt-3 font-medium text-slate-900">{t("Chưa có đơn hàng nào trong hệ thống")}</p>
          <p className="mt-1 text-sm text-slate-500">{t("Bấm &ldquo;Tạo đơn&rdquo; ở góc trên để thêm đơn vận chuyển mới.")}</p>
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
                sortBy={sortBy}
                sortDir={sortDir}
                onSortChange={(nextField, nextDir) => {
                  setSortBy(nextField);
                  setSortDir(nextDir);
                  setPage(1);
                  setSelectedId('');
                }}
                pagination={{
                  page,
                  pageSize,
                  totalOrders,
                  totalPages,
                  onPageChange: (nextPage) => {
                    setSelectedId('');
                    setPage(nextPage);
                  },
                  onPageSizeChange: (nextSize) => {
                    setSelectedId('');
                    setPageSize(nextSize);
                    setPage(1);
                  },
                  onClearSelection: () => setSelectedId(''),
                }}
              />
            </CardContent>
          </Card>

          {/* Contextual Route & Live Tracking Inspector (Only visible when an order is selected) */}
          {selected && (
            <aside
              id="route-workspace"
              aria-label={t("Chi tiết lộ trình đơn hàng")}
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
                      title={t("Sao chép mã đơn đầy đủ")}
                      className="rounded p-1 text-slate-400 hover:bg-slate-200/70 hover:text-slate-700"
                    >
                      <Copy size={13} />
                    </button>
                    <OrderStatusBadge status={selected.status} fulfillmentStatus={selected.fulfillment_status} />
                  </div>
                  <p className="mt-0.5 truncate text-xs text-slate-600">
                    {t("Người nhận:")}{" "}<strong className="font-medium text-slate-900">{selected.customer_name || t("Khách nhận hàng")}</strong>
                  </p>
                </div>

                <div className="flex shrink-0 items-center gap-1">
                  {selectedIndexOnPage > 0 && (
                    <button
                      type="button"
                      onClick={() => setSelectedId(paginatedOrders[selectedIndexOnPage - 1].id)}
                      title={t("Đơn trước trong danh sách")}
                      className="flex h-8 w-8 items-center justify-center rounded-lg border border-slate-200 bg-white text-slate-600 hover:bg-slate-100"
                    >
                      <ChevronLeft size={15} />
                    </button>
                  )}
                  {selectedIndexOnPage >= 0 && selectedIndexOnPage < paginatedOrders.length - 1 && (
                    <button
                      type="button"
                      onClick={() => setSelectedId(paginatedOrders[selectedIndexOnPage + 1].id)}
                      title={t("Đơn tiếp theo trong danh sách")}
                      className="flex h-8 w-8 items-center justify-center rounded-lg border border-slate-200 bg-white text-slate-600 hover:bg-slate-100"
                    >
                      <ChevronRight size={15} />
                    </button>
                  )}
                  <button
                    type="button"
                    onClick={() => setSelectedId('')}
                    aria-label={t("Đóng bản đồ lộ trình")}
                    title={t("Đóng lộ trình")}
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
                    <span className="text-slate-500">{t("Tài xế phụ trách")}</span>
                    <p className="mt-0.5 font-semibold text-slate-900">
                      {selectedDriverName || (selected.assigned_driver_id ? t("Tài xế đã phân công") : t("Chưa phân công tài xế"))}
                    </p>
                    {selectedDriver && (
                      <p className="mt-0.5 flex items-center gap-1 text-slate-600">
                        <Bike size={12} />
                        {selectedDriver.vehicle_plate || selectedDriver.vehicle_type || t("Chưa có biển số")}
                      </p>
                    )}
                  </div>
                </div>

                <div className="flex items-start gap-2.5 pt-3">
                  <MapPin size={15} className="mt-0.5 shrink-0 text-teal-700" />
                  <div className="min-w-0 flex-1 space-y-2">
                    <div>
                      <span className="text-slate-500">
                        {t("Điểm lấy hàng")}{" "}{selected.pickup_location_name ? `(${selected.pickup_location_name})` : ''}
                      </span>
                      <p className="mt-0.5 text-slate-800">{selected.pickup_address}</p>
                    </div>
                    <div>
                      <span className="text-slate-500">{t("Điểm giao hàng")}</span>
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
  const { t, intlLocale } = useLocale();
  if (!assigned) {
    return <p className="text-xs text-slate-500">{t("Đơn hàng chưa được phân công tài xế")}</p>;
  }
  const presentation =
    signal === 'live'
      ? ['bg-emerald-500', t("Đang nhận GPS trực tiếp")]
      : signal === 'stale'
      ? ['bg-amber-500', t("Tín hiệu GPS đã cũ")]
      : signal === 'reconnecting'
      ? ['bg-rose-500', t("Đang kết nối lại luồng GPS")]
      : ['bg-slate-400', t("Đang chờ vị trí mới từ tài xế")];
  return (
    <div className="flex flex-wrap items-center justify-between gap-2 text-xs">
      <span className="flex items-center gap-2 font-medium text-slate-800">
        <span className={`h-2 w-2 rounded-full ${presentation[0]}`} />
        {presentation[1]}
      </span>
      {lastUpdate && (
        <span className="text-slate-500">
          {lastUpdate.toLocaleTimeString(intlLocale)}
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
