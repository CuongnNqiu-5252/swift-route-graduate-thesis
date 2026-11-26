'use client';

import { useLocale } from '@/i18n/provider';

import { useAuth } from '@/application/contexts/auth-context';
import { useDrivers } from '@/application/hooks/use-drivers';
import { useOrderPage } from '@/application/hooks/use-order-page';
import { AccessDeniedNotice, PermissionGuard } from '@/components/auth/permission-guard';
import { OrderCreatePanel } from '@/components/orders/order-create-panel';
import { OrderImportAddressQueue, OrderImportPanel } from '@/components/orders/order-import-panel';
import { OrderTable, type OrderPlanningSelection } from '@/components/orders/order-table';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Modal } from '@/components/ui/modal';
import { CustomSelect } from '@/components/ui/select';
import { OrderPriority, OrderStatus } from '@/domain/entities/order.entity';
import {
  orderRepository,
  type OrderSortDirection,
  type OrderSortField,
} from '@/infrastructure/repositories/order.repository';
import { pickupLocationRepository } from '@/infrastructure/repositories/pickup-location.repository';
import * as DropdownMenu from '@radix-ui/react-dropdown-menu';
import {
  ArrowUpDown,
  ChevronDown,
  FileSpreadsheet,
  PackageSearch,
  Plus,
  RefreshCw,
  Search,
  Warehouse,
  X
} from 'lucide-react';
import { useSearchParams } from 'next/navigation';
import { type ReactNode, useEffect, useMemo, useState } from 'react';
import { toast } from 'sonner';

const STATUS_TABS: { label: string; value?: OrderStatus }[] = [
  { label: 'Tất cả' },
  { label: 'Chờ điều phối', value: 'READY_FOR_DISPATCH' },
  { label: 'Đã lên lịch', value: 'PLANNED' },
  { label: 'Đang giao', value: 'IN_TRANSIT' },
  { label: 'Đã giao', value: 'DELIVERED' },
  { label: 'Sự cố', value: 'FAILED' },
  { label: 'Đang hoàn kho', value: 'RETURNING' },
  { label: 'Chuyển hoàn', value: 'RETURNED' },
  { label: 'Đã hủy', value: 'CANCELLED' },
];

const VALID_TAB_STATUSES = new Set<OrderStatus>(
  STATUS_TABS.map((tab) => tab.value).filter((value): value is OrderStatus => Boolean(value)),
);

function parseOrderStatusParam(value: string | null): OrderStatus | undefined {
  if (!value) return undefined;
  const normalized = value.trim().toUpperCase() as OrderStatus;
  return VALID_TAB_STATUSES.has(normalized) ? normalized : undefined;
}

interface OrdersWorkspaceProps {
  embedded?: boolean;
  refreshRevision?: number;
  selection?: OrderPlanningSelection;
  onOrdersChanged?: () => void;
  headerActions?: ReactNode;
}

export function OrdersWorkspace({ embedded = false, refreshRevision = 0, selection, onOrdersChanged, headerActions }: OrdersWorkspaceProps) {
  const { t } = useLocale();
  const { hasPermission } = useAuth();
  const searchParams = useSearchParams();
  const activeTab = parseOrderStatusParam(searchParams.get('status'));
  const { drivers } = useDrivers();
  const [entryPanel, setEntryPanel] = useState<'create' | 'import' | null>(null);
  const canAddOrder = hasPermission('order:create');
  const [showPendingAddresses, setShowPendingAddresses] = useState(false);
  const [importRevision, setImportRevision] = useState(0);
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
      .catch(() => { });
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
  const { data, isLoading, error, refetch } = useOrderPage(filters, refreshRevision);
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

  const isDelivering = selected?.status === 'IN_TRANSIT';
  const isTracking = isDelivering && Boolean(selected?.assigned_driver_id);
  // const showDeliveryRoute = isDelivering || selected?.status === 'PLANNED' || selected?.status === 'READY_FOR_DISPATCH';
  // const tracking = useOrderLiveTracking(
  //   isTracking ? selected?.id : undefined,
  //   isTracking ? selected?.assigned_driver_id : undefined,
  // );
  const selectedDriver = drivers.find(
    (driver) => driver.id === selected?.assigned_driver_id || driver.user_id === selected?.assigned_driver_id,
  );
  const selectedDriverName = selected?.assigned_driver_name || selectedDriver?.name;

  const handleCancelOrder = async (orderId: string, reason?: string) => {
    try {
      await orderRepository.cancelOrder(orderId, reason);
      refetch();
      onOrdersChanged?.();
      toast.success(t("Đã huỷ đơn hàng thành công"));
      return true;
    } catch {
      toast.error(t("Lỗi khi huỷ đơn hàng"));
      return false;
    }
  };

  const handleUpdatePriority = async (orderId: string, nextPriority: OrderPriority) => {
    try {
      await orderRepository.updateOrderPriority(orderId, nextPriority);
      void refetch();
      onOrdersChanged?.();
      toast.success(t("Đã cập nhật mức độ ưu tiên"));
    } catch {
      toast.error(t("Không thể cập nhật mức độ ưu tiên"));
    }
  };

  const copyOrderCode = (code: string) => {
    navigator.clipboard?.writeText(code);
    toast.success(t("Đã sao chép mã đơn"));
  };

  const selectTab = (nextStatus?: OrderStatus) => {
    setShowPendingAddresses(false);
    const url = new URL(window.location.href);
    const params = url.searchParams;
    if (nextStatus) {
      params.set('status', nextStatus);
    } else {
      params.delete('status');
    }
    window.history.replaceState(null, '', url);
    setPage(1);
  };

  if (!hasPermission('order:read')) {
    return <AccessDeniedNotice requiredPermission="order:read" />;
  }

  return (
    <div className="min-w-0 space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-3">
        {!embedded && <h1 className="text-xl font-semibold text-slate-950">{t('Quản lý đơn hàng')}</h1>}
        <div className="ml-auto flex flex-wrap items-center gap-2">
          <Button type="button" variant="outline" size="sm" className="min-h-11 cursor-pointer sm:min-h-9" onClick={() => {
            setImportRevision((value) => value + 1);
            void refetch();
          }} isLoading={isLoading}>
            <RefreshCw size={14} aria-hidden="true" />
            {t('Làm mới')}
          </Button>
          <PermissionGuard permission="order:create">
            <DropdownMenu.Root modal={false}>
              <DropdownMenu.Trigger asChild>
                <Button type="button" size="sm" className="min-h-11 cursor-pointer sm:min-h-9">
                  <Plus size={15} aria-hidden="true" />
                  {t('Thêm đơn')}
                  <ChevronDown size={14} aria-hidden="true" />
                </Button>
              </DropdownMenu.Trigger>
              <DropdownMenu.Portal>
                <DropdownMenu.Content align="end" sideOffset={6}
                  className="z-50 min-w-40 rounded-lg bg-white p-1 text-sm text-slate-800 shadow-lg ring-1 ring-slate-200">
                  <DropdownMenu.Item onSelect={() => setEntryPanel('create')}
                    className="flex min-h-11 cursor-pointer items-center gap-2 rounded px-3 outline-none data-highlighted:bg-slate-100 sm:min-h-9">
                    <Plus size={16} aria-hidden="true" />
                    {t('Tạo đơn')}
                  </DropdownMenu.Item>
                  <DropdownMenu.Item onSelect={() => setEntryPanel('import')}
                    className="flex min-h-11 cursor-pointer items-center gap-2 rounded px-3 outline-none data-highlighted:bg-slate-100 sm:min-h-9">
                    <FileSpreadsheet size={16} aria-hidden="true" />
                    {t('Nhập vận đơn từ Excel hoặc CSV')}
                  </DropdownMenu.Item>
                </DropdownMenu.Content>
              </DropdownMenu.Portal>
            </DropdownMenu.Root>
          </PermissionGuard>
          {headerActions}
        </div>
      </div>

      <Modal
        open={entryPanel === 'create' && canAddOrder}
        onOpenChange={(open) => { if (!open) setEntryPanel(null); }}
        title={t('Tạo đơn hàng')}
        description={t('Nhập người nhận, sau đó tìm hoặc đặt ghim tại điểm giao.')}
        className="max-w-5xl"
        bodyClassName="p-5 sm:p-6"
      >
        {entryPanel === 'create' && canAddOrder && (
          <OrderCreatePanel
            inModal
            onCreated={() => {
              setEntryPanel(null);
              void refetch();
              onOrdersChanged?.();
            }}
            onClose={() => setEntryPanel(null)}
          />
        )}
      </Modal>
      <Modal
        open={entryPanel === 'import' && canAddOrder}
        onOpenChange={(open) => { if (!open) setEntryPanel(null); }}
        title={t('Nhập vận đơn từ Excel hoặc CSV')}
        className="max-w-4xl"
        bodyClassName="p-5 sm:p-6"
      >
        {entryPanel === 'import' && canAddOrder && (
          <OrderImportPanel
            inModal
            onImported={() => {
              setImportRevision((value) => value + 1);
              void refetch();
              onOrdersChanged?.();
            }}
          />
        )}
      </Modal>

      {/* Status Tabs with Live Counts */}
      <div className="flex items-center gap-1 overflow-x-auto border-b border-slate-200">
        {STATUS_TABS.map((tab) => {
          const isActive = !showPendingAddresses && activeTab === tab.value;
          const count = tab.value
            ? statusCounts?.[tab.value] ?? (statusCounts ? 0 : '—')
            : allOrders ?? '—';
          return (
            <button
              key={t(tab.label)}
              type="button"
              aria-pressed={isActive}
              onClick={() => {
                selectTab(tab.value);
              }}
              className={`inline-flex min-h-10 shrink-0 items-center gap-1.5 border-b-2 px-3 py-2 text-xs font-medium transition-colors sm:text-sm ${isActive
                ? 'border-slate-950 font-semibold text-slate-950'
                : 'border-transparent text-slate-600 hover:bg-slate-100/80 hover:text-slate-900'
                }`}
            >
              <span>{t(tab.label)}</span>
              <span
                className={`rounded-full px-1.5 py-0.5 text-[11px] tabular-nums ${isActive ? 'bg-slate-900 text-white' : 'bg-slate-100 text-slate-600'
                  }`}
              >
                {count}
              </span>
            </button>
          );
        })}
        {hasPermission('order:create') && (
          <button type="button" aria-pressed={showPendingAddresses}
            onClick={() => setShowPendingAddresses(true)}
            className={`inline-flex min-h-10 shrink-0 items-center border-b-2 px-3 py-2 text-xs font-medium sm:text-sm ${showPendingAddresses ? 'border-slate-950 font-semibold text-slate-950'
              : 'border-transparent text-slate-600 hover:bg-slate-100/80 hover:text-slate-900'
              }`}>
            {t('Cần bổ sung địa chỉ')}
          </button>
        )}
      </div>

      {showPendingAddresses && hasPermission('order:create') ? (
        <OrderImportAddressQueue revision={importRevision} onCompleted={() => {
          setImportRevision((value) => value + 1);
          void refetch();
          onOrdersChanged?.();
        }} />
      ) : (
        <>
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
                <div className="sm:w-56">
                  <CustomSelect
                    aria-label={t("Lọc theo kho lấy hàng")}
                    value={warehouseFilter}
                    leadingIcon={<Warehouse size={15} aria-hidden="true" />}
                    onValueChange={(next) => {
                      setWarehouseFilter(next);
                      setPage(1);
                    }}
                    options={[
                      { value: '', label: `${t("Tất cả kho lấy (")}${warehouseOptions.length})` },
                      ...warehouseOptions.map((item) => ({ value: item.value, label: item.label })),
                    ]}
                  />
                </div>
              )}

              <div className="sm:w-56">
                <CustomSelect
                  aria-label={t("Sắp xếp đơn theo ngày")}
                  value={`${sortBy}:${sortDir}`}
                  leadingIcon={<ArrowUpDown size={15} aria-hidden="true" />}
                  onValueChange={(next) => {
                    const [nextField, nextDir] = next.split(':') as [OrderSortField, OrderSortDirection];
                    setSortBy(nextField);
                    setSortDir(nextDir);
                    setPage(1);
                    setSelectedId('');
                  }}
                  options={[
                    { value: 'created_at:desc', label: t("Ngày tạo: Mới nhất trước") },
                    { value: 'created_at:asc', label: t("Ngày tạo: Cũ nhất trước") },
                    { value: 'updated_at:desc', label: t("Cập nhật: Mới nhất trước") },
                    { value: 'updated_at:asc', label: t("Cập nhật: Cũ nhất trước") },
                    { value: 'time_window_start:asc', label: t("Hẹn giao: Sớm nhất trước") },
                    { value: 'time_window_start:desc', label: t("Hẹn giao: Muộn nhất trước") },
                  ]}
                />
              </div>

              {(query || activeTab || warehouseFilter || sortBy !== 'created_at' || sortDir !== 'desc') && (
                <button
                  type="button"
                  onClick={() => {
                    setQuery('');
                    selectTab(undefined);
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
              {canAddOrder && (
                <p className="mt-1 text-sm text-slate-600">{t('Mở Thêm đơn để tạo đơn lẻ hoặc nhập nhiều đơn từ CSV.')}</p>
              )}
            </div>
          ) : (
            /* Main Workspace: Table on the left, Contextual Route Inspector on the right when an order is selected */
            <div
              className={`grid items-start gap-4 ${selected ? 'grid-cols-[minmax(0,1fr)] xl:grid-cols-[minmax(0,1fr)_minmax(0,24rem)]' : 'grid-cols-[minmax(0,1fr)]'
                }`}
            >
              {/* Orders Table + Pagination */}
              <Card className="overflow-hidden border-slate-200 shadow-none">
                <CardContent className="p-0">
                  <OrderTable
                    orders={paginatedOrders}
                    isLoading={isLoading}
                    drivers={drivers}
                    planningSelection={selection}
                    showOrderDetails
                    selectedId={selected?.id}
                    onCancelOrder={hasPermission('order:cancel') ? handleCancelOrder : undefined}
                    onUpdatePriority={canAddOrder ? handleUpdatePriority : undefined}
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

            </div>
          )}
        </>
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
