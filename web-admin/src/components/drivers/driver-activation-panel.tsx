'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  ChevronLeft,
  ChevronRight,
  KeyRound,
  Mail,
  PowerOff,
  RefreshCw,
  RotateCw,
  Search,
  Smartphone,
} from 'lucide-react';
import { toast } from 'sonner';
import { useAuth } from '@/application/contexts/auth-context';
import {
  DriverActivationKeyResult,
  DriverActivationStatusItem,
  DriverAppActivationStatus,
} from '@/domain/entities/driver.entity';
import { driverRepository } from '@/infrastructure/repositories/driver.repository';
import { useLocale } from '@/i18n/provider';
import { Button } from '@/components/ui/button';
import { ConfirmationDialog } from '@/components/ui/confirmation-dialog';
import { CustomSelect } from '@/components/ui/select';
import { DriverActivationCodeModal } from './driver-activation-code-modal';

const STATUS_BADGE_CONFIG: Record<
  DriverAppActivationStatus,
  { label: string; className: string; dotClassName: string }
> = {
  NOT_ACTIVATED: {
    label: 'Chưa kích hoạt',
    className: 'border-slate-200 bg-slate-100 text-slate-700',
    dotClassName: 'bg-slate-400',
  },
  KEY_CREATED: {
    label: 'Đã tạo mã',
    className: 'border-blue-200 bg-blue-50 text-blue-800',
    dotClassName: 'bg-blue-600',
  },
  ACTIVATED: {
    label: 'Đã kích hoạt',
    className: 'border-emerald-200 bg-emerald-50 text-emerald-800',
    dotClassName: 'bg-emerald-600',
  },
  KEY_EXPIRED: {
    label: 'Mã hết hạn',
    className: 'border-amber-200 bg-amber-50 text-amber-800',
    dotClassName: 'bg-amber-600',
  },
  DEACTIVATED: {
    label: 'Đã hủy kích hoạt',
    className: 'border-red-200 bg-red-50 text-red-800',
    dotClassName: 'bg-red-600',
  },
};

function ActivationStatusBadge({ status }: { status: DriverAppActivationStatus }) {
  const { t } = useLocale();
  const cfg = STATUS_BADGE_CONFIG[status] ?? STATUS_BADGE_CONFIG.NOT_ACTIVATED;
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-md border px-2 py-0.5 text-xs font-medium whitespace-nowrap ${cfg.className}`}
    >
      <span className={`size-1.5 rounded-full ${cfg.dotClassName}`} aria-hidden="true" />
      {t(cfg.label)}
    </span>
  );
}

interface DriverActivationPanelProps {
  onDriversMutated?: () => Promise<void> | void;
}

const PAGE_SIZE = 20;

export function DriverActivationPanel({ onDriversMutated }: DriverActivationPanelProps) {
  const { t, intlLocale } = useLocale();
  const { user, hasPermission } = useAuth();
  const canManage =
    hasPermission('driver:manage') ||
    ['OWNER', 'TENANT_ADMIN', 'SYSTEM_ADMIN'].includes(user?.tenantRole ?? user?.role ?? '');

  const [rows, setRows] = useState<DriverActivationStatusItem[]>([]);
  const [page, setPage] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [query, setQuery] = useState('');
  const [debouncedQuery, setDebouncedQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busyAction, setBusyAction] = useState(false);

  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [issuedModalItems, setIssuedModalItems] = useState<DriverActivationKeyResult[]>([]);
  const [deactivateTargetIds, setDeactivateTargetIds] = useState<string[] | null>(null);
  const selectAllRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebouncedQuery(query);
      setPage(0);
    }, 250);
    return () => window.clearTimeout(timer);
  }, [query]);

  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await driverRepository.getActivationStatus({
        page,
        size: PAGE_SIZE,
        query: debouncedQuery,
        status: statusFilter,
      });
      setRows(response.content);
      setTotalElements(response.total_elements);
      setTotalPages(response.total_pages);
      setSelectedIds((prev) => {
        const currentPageIds = new Set(response.content.map((item) => item.driver_id));
        const next = new Set<string>();
        prev.forEach((id) => {
          if (currentPageIds.has(id)) next.add(id);
        });
        return next;
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : t('Không thể tải trạng thái kích hoạt ứng dụng tài xế.'));
    } finally {
      setLoading(false);
    }
  }, [page, debouncedQuery, statusFilter, t]);

  useEffect(() => {
    let cancelled = false;
    driverRepository
      .getActivationStatus({
        page,
        size: PAGE_SIZE,
        query: debouncedQuery,
        status: statusFilter,
      })
      .then((response) => {
        if (cancelled) return;
        setRows(response.content);
        setTotalElements(response.total_elements);
        setTotalPages(response.total_pages);
        setError(null);
        setSelectedIds((prev) => {
          const currentPageIds = new Set(response.content.map((item) => item.driver_id));
          const next = new Set<string>();
          prev.forEach((id) => {
            if (currentPageIds.has(id)) next.add(id);
          });
          return next;
        });
      })
      .catch((err) => {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : t('Không thể tải trạng thái kích hoạt ứng dụng tài xế.'));
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [page, debouncedQuery, statusFilter, t]);

  const allOnPageSelected = rows.length > 0 && rows.every((row) => selectedIds.has(row.driver_id));
  const someOnPageSelected = rows.some((row) => selectedIds.has(row.driver_id)) && !allOnPageSelected;

  useEffect(() => {
    if (selectAllRef.current) {
      selectAllRef.current.indeterminate = someOnPageSelected;
    }
  }, [someOnPageSelected]);

  const selectedRows = useMemo(
    () => rows.filter((row) => selectedIds.has(row.driver_id)),
    [rows, selectedIds]
  );

  const canBulkActivate = useMemo(
    () =>
      canManage &&
      selectedRows.length > 0 &&
      selectedRows.every(
        (row) =>
          row.profile_status !== 'SUSPENDED' &&
          (row.activation_status === 'NOT_ACTIVATED' ||
            row.activation_status === 'KEY_EXPIRED' ||
            row.activation_status === 'DEACTIVATED')
      ),
    [canManage, selectedRows]
  );

  const canBulkRegenerate = useMemo(
    () =>
      canManage &&
      selectedRows.length > 0 &&
      selectedRows.every(
        (row) =>
          row.profile_status !== 'SUSPENDED' &&
          (row.activation_status === 'KEY_CREATED' ||
            row.activation_status === 'KEY_EXPIRED' ||
            row.activation_status === 'ACTIVATED')
      ),
    [canManage, selectedRows]
  );

  const canBulkDeactivate = useMemo(
    () =>
      canManage &&
      selectedRows.length > 0 &&
      selectedRows.some(
        (row) => row.activation_status === 'ACTIVATED' || row.activation_status === 'KEY_CREATED'
      ),
    [canManage, selectedRows]
  );

  const canBulkSendEmail = useMemo(
    () =>
      canManage &&
      selectedRows.length > 0 &&
      selectedRows.every((row) => Boolean(row.email && row.email.trim()) && row.profile_status !== 'SUSPENDED'),
    [canManage, selectedRows]
  );

  function toggleSelectAll() {
    if (allOnPageSelected) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(rows.map((row) => row.driver_id)));
    }
  }

  function toggleRow(driverId: string) {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(driverId)) {
        next.delete(driverId);
      } else {
        next.add(driverId);
      }
      return next;
    });
  }

  function formatTimestamp(iso?: string) {
    if (!iso) return '—';
    const parsed = new Date(iso);
    if (Number.isNaN(parsed.getTime())) return '—';
    return parsed.toLocaleString(intlLocale, {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  }

  function formatOsLabel(os?: string, osVersion?: string) {
    if (!os) return '—';
    const upper = os.toUpperCase();
    const prettyOs = upper === 'IOS' ? 'iOS' : upper === 'ANDROID' ? 'Android' : os;
    return osVersion ? `${prettyOs} ${osVersion}` : prettyOs;
  }

  async function handleSingleActivate(driver: DriverActivationStatusItem, regenerate = false) {
    setBusyAction(true);
    try {
      const issued = regenerate
        ? await driverRepository.regenerateActivationCode(driver.driver_id)
        : await driverRepository.generateActivationCode(driver.driver_id);
      setIssuedModalItems([issued]);
      await loadData();
      await onDriversMutated?.();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : t('Không thể tạo mã kích hoạt cho tài xế.'));
    } finally {
      setBusyAction(false);
    }
  }

  async function handleSingleSendEmail(driver: DriverActivationStatusItem) {
    if (!driver.email) {
      await handleSingleActivate(driver, driver.activation_status === 'KEY_CREATED');
      return;
    }
    setBusyAction(true);
    try {
      const issued = await driverRepository.sendActivationEmail(driver.driver_id, driver.email);
      setIssuedModalItems([issued]);
      toast.success(`${t('Đã gửi mã kích hoạt tới')} ${driver.email}`);
      await loadData();
      await onDriversMutated?.();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : t('Không thể gửi email kích hoạt.'));
    } finally {
      setBusyAction(false);
    }
  }

  async function handleBulkActivateOrRegenerate() {
    const ids = selectedRows.map((row) => row.driver_id);
    if (ids.length === 0) return;
    setBusyAction(true);
    try {
      const issued = await driverRepository.bulkActivateDrivers(ids, false);
      setIssuedModalItems(issued);
      setSelectedIds(new Set());
      await loadData();
      await onDriversMutated?.();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : t('Không thể cấp mã kích hoạt hàng loạt.'));
    } finally {
      setBusyAction(false);
    }
  }

  async function handleBulkEmail() {
    const ids = selectedRows.map((row) => row.driver_id);
    if (ids.length === 0) return;
    setBusyAction(true);
    try {
      const issued = await driverRepository.bulkSendActivationEmail(ids);
      setIssuedModalItems(issued);
      toast.success(`${t('Đã gửi mã kích hoạt qua email cho')} ${issued.length} ${t('tài xế')}`);
      setSelectedIds(new Set());
      await loadData();
      await onDriversMutated?.();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : t('Không thể gửi email hàng loạt.'));
    } finally {
      setBusyAction(false);
    }
  }

  async function confirmDeactivation() {
    if (!deactivateTargetIds || deactivateTargetIds.length === 0) return false;
    setBusyAction(true);
    try {
      if (deactivateTargetIds.length === 1) {
        await driverRepository.deactivateDriverApp(deactivateTargetIds[0]);
      } else {
        await driverRepository.bulkDeactivateDrivers(deactivateTargetIds);
      }
      toast.success(
        deactivateTargetIds.length === 1
          ? t('Đã hủy kích hoạt ứng dụng của tài xế.')
          : `${t('Đã hủy kích hoạt ứng dụng cho')} ${deactivateTargetIds.length} ${t('tài xế.')}`
      );
      setSelectedIds((prev) => {
        const next = new Set(prev);
        deactivateTargetIds.forEach((id) => next.delete(id));
        return next;
      });
      await loadData();
      await onDriversMutated?.();
      return true;
    } catch (err) {
      toast.error(err instanceof Error ? err.message : t('Không thể hủy kích hoạt ứng dụng.'));
      return false;
    } finally {
      setBusyAction(false);
    }
  }

  const deactivateDriverNames = useMemo(() => {
    if (!deactivateTargetIds) return '';
    const matched = rows
      .filter((r) => deactivateTargetIds.includes(r.driver_id))
      .map((r) => r.full_name);
    return matched.join(', ');
  }, [deactivateTargetIds, rows]);

  return (
    <section
      aria-labelledby="driver-activation-heading"
      className="min-w-0 overflow-hidden rounded-xl border border-slate-200 bg-white"
    >
      <div className="space-y-3 border-b border-slate-200 px-3 py-3.5 sm:px-4">
        <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
          <div>
            <h2 id="driver-activation-heading" className="text-sm font-semibold text-slate-950">
              {t('Kích hoạt & Trạng thái ứng dụng tài xế')}
            </h2>
            <p className="mt-0.5 text-xs text-slate-600">
              {t(
                'Cấp mã 12 chữ số để tài xế đăng nhập ứng dụng di động không cần mật khẩu. Mỗi tài xế chỉ kích hoạt 1 thiết bị tại một thời điểm.'
              )}
            </p>
          </div>
          {!loading && !error && (
            <p className="text-xs tabular-nums text-slate-600" role="status">
              {selectedIds.size > 0
                ? `${selectedIds.size} ${t('đã chọn')} · `
                : ''}
              {totalElements} {t('tài xế')}
            </p>
          )}
        </div>

        <div className="flex flex-wrap items-center justify-between gap-2">
          <div className="flex flex-wrap items-center gap-2">
            {canManage && (
              <>
                <Button
                  type="button"
                  size="sm"
                  className="min-h-9 cursor-pointer"
                  disabled={!canBulkActivate || busyAction}
                  onClick={() => void handleBulkActivateOrRegenerate()}
                >
                  <KeyRound size={14} aria-hidden="true" />
                  {t('Kích hoạt đã chọn')}
                </Button>
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  className="min-h-9 cursor-pointer"
                  disabled={!canBulkRegenerate || busyAction}
                  onClick={() => void handleBulkActivateOrRegenerate()}
                >
                  <RotateCw size={14} aria-hidden="true" />
                  {t('Tạo lại mã')}
                </Button>
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  className="min-h-9 cursor-pointer"
                  disabled={!canBulkSendEmail || busyAction}
                  onClick={() => void handleBulkEmail()}
                >
                  <Mail size={14} aria-hidden="true" />
                  {t('Gửi email')}
                </Button>
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  className="min-h-9 cursor-pointer text-red-700 hover:bg-red-50 hover:text-red-800"
                  disabled={!canBulkDeactivate || busyAction}
                  onClick={() =>
                    setDeactivateTargetIds(
                      selectedRows
                        .filter(
                          (row) =>
                            row.activation_status === 'ACTIVATED' || row.activation_status === 'KEY_CREATED'
                        )
                        .map((row) => row.driver_id)
                    )
                  }
                >
                  <PowerOff size={14} aria-hidden="true" />
                  {t('Hủy kích hoạt')}
                </Button>
              </>
            )}
            <Button
              type="button"
              size="sm"
              variant="outline"
              className="min-h-9 cursor-pointer"
              disabled={loading || busyAction}
              onClick={() => void loadData()}
            >
              <RefreshCw size={14} aria-hidden="true" />
              {t('Làm mới')}
            </Button>
          </div>

          <div className="flex w-full flex-wrap items-center gap-2 sm:w-auto">
            <div className="w-full sm:w-48">
              <CustomSelect
                aria-label={t('Lọc theo trạng thái kích hoạt')}
                value={statusFilter}
                onValueChange={(next) => {
                  setStatusFilter(next);
                  setPage(0);
                }}
                options={[
                  { value: 'ALL', label: t('Tất cả trạng thái') },
                  { value: 'NOT_ACTIVATED', label: t('Chưa kích hoạt') },
                  { value: 'KEY_CREATED', label: t('Đã tạo mã') },
                  { value: 'ACTIVATED', label: t('Đã kích hoạt') },
                  { value: 'KEY_EXPIRED', label: t('Mã hết hạn') },
                  { value: 'DEACTIVATED', label: t('Đã hủy kích hoạt') },
                ]}
              />
            </div>
            <label className="relative w-full sm:w-64">
              <Search
                size={15}
                aria-hidden="true"
                className="pointer-events-none absolute start-2.5 top-1/2 -translate-y-1/2 text-slate-500"
              />
              <input
                type="search"
                aria-label={t('Tìm theo tên hoặc mã tài xế')}
                placeholder={t('Tên, mã DRV, SĐT, email…')}
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                className="h-9 w-full rounded-lg border border-slate-300 bg-white pe-2 ps-8 text-sm text-slate-900 placeholder:text-slate-500 focus-visible:outline-2 focus-visible:outline-blue-600"
              />
            </label>
          </div>
        </div>
      </div>

      {loading ? (
        <div role="status" className="px-4 py-12 text-center text-sm text-slate-600">
          {t('Đang tải trạng thái kích hoạt ứng dụng…')}
        </div>
      ) : error ? (
        <div role="alert" className="bg-red-50 px-4 py-4 text-sm text-red-800">
          {error}
        </div>
      ) : rows.length === 0 ? (
        <div className="flex flex-col items-center justify-center gap-2 px-4 py-12 text-center">
          <Smartphone size={28} className="text-slate-400" aria-hidden="true" />
          <p className="text-sm font-medium text-slate-900">
            {debouncedQuery || statusFilter !== 'ALL'
              ? t('Không tìm thấy tài xế phù hợp với bộ lọc.')
              : t('Chưa có hồ sơ tài xế nào trong doanh nghiệp.')}
          </p>
          <p className="max-w-md text-xs text-slate-600">
            {t('Thêm hồ sơ tài xế ở tab "Hồ sơ & Duyệt", sau đó quay lại đây để tạo mã kích hoạt 12 chữ số.')}
          </p>
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-start text-sm">
            <thead className="border-b border-slate-200 bg-slate-50/70 text-xs text-slate-600">
              <tr>
                <th scope="col" className="w-10 px-3 py-2.5 text-center">
                  <input
                    ref={selectAllRef}
                    type="checkbox"
                    checked={allOnPageSelected}
                    onChange={toggleSelectAll}
                    aria-label={t('Chọn tất cả tài xế trên trang này')}
                    className="size-4 cursor-pointer rounded border-slate-300 text-slate-900 focus:ring-blue-600"
                  />
                </th>
                <th scope="col" className="px-3 py-2.5 text-start font-semibold">
                  {t('Tài xế')}
                </th>
                <th scope="col" className="px-3 py-2.5 text-start font-semibold">
                  {t('Email')}
                </th>
                <th scope="col" className="px-3 py-2.5 text-start font-semibold">
                  {t('Mã kích hoạt')}
                </th>
                <th scope="col" className="px-3 py-2.5 text-start font-semibold">
                  {t('Trạng thái App')}
                </th>
                <th scope="col" className="px-3 py-2.5 text-start font-semibold">
                  {t('Phiên bản')}
                </th>
                <th scope="col" className="px-3 py-2.5 text-start font-semibold">
                  {t('Hệ điều hành')}
                </th>
                <th scope="col" className="px-3 py-2.5 text-start font-semibold">
                  {t('Thiết bị')}
                </th>
                <th scope="col" className="px-3 py-2.5 text-start font-semibold">
                  {t('Kích hoạt lúc')}
                </th>
                <th scope="col" className="px-3 py-2.5 text-start font-semibold">
                  {t('Hoạt động gần nhất')}
                </th>
                <th scope="col" className="px-3 py-2.5 text-end font-semibold">
                  {t('Thao tác')}
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {rows.map((row) => {
                const isSelected = selectedIds.has(row.driver_id);
                const isSuspended = row.profile_status === 'SUSPENDED';

                return (
                  <tr
                    key={row.driver_id}
                    className={`transition-colors hover:bg-slate-50/80 ${isSelected ? 'bg-blue-50/40' : ''}`}
                  >
                    <td className="px-3 py-2.5 text-center">
                      <input
                        type="checkbox"
                        checked={isSelected}
                        onChange={() => toggleRow(row.driver_id)}
                        aria-label={`${t('Chọn tài xế')} ${row.full_name}`}
                        className="size-4 cursor-pointer rounded border-slate-300 text-slate-900 focus:ring-blue-600"
                      />
                    </td>
                    <th scope="row" className="px-3 py-2.5 text-start font-normal">
                      <div className="font-semibold text-slate-950">{row.full_name}</div>
                      <div className="mt-0.5 flex flex-wrap items-center gap-1.5 text-xs tabular-nums text-slate-500">
                        <span>{row.driver_code}</span>
                        {row.phone && <span>· {row.phone}</span>}
                        <span
                          className={
                            row.operational_status === 'AVAILABLE'
                              ? 'font-medium text-emerald-700'
                              : 'text-slate-500'
                          }
                        >
                          · {row.operational_status === 'AVAILABLE' ? t('Trực tuyến') : t('Ngoại tuyến')}
                        </span>
                      </div>
                    </th>
                    <td className="max-w-48 truncate px-3 py-2.5 text-slate-700" title={row.email || ''}>
                      {row.email || '—'}
                    </td>
                    <td className="px-3 py-2.5 text-xs text-slate-700">
                      {row.activation_status === 'KEY_CREATED' ? (
                        <div>
                          <span className="font-medium text-blue-800">{t('Đang chờ nhập mã')}</span>
                          {row.key_expires_at && (
                            <span className="mt-0.5 block tabular-nums text-slate-500">
                              {t('Hết hạn')} {formatTimestamp(row.key_expires_at)}
                            </span>
                          )}
                        </div>
                      ) : row.activation_status === 'KEY_EXPIRED' ? (
                        <span className="text-amber-800">{t('Đã hết hạn (24h)')}</span>
                      ) : row.activation_status === 'ACTIVATED' ? (
                        <span className="text-emerald-800">{t('Đã sử dụng')}</span>
                      ) : row.activation_status === 'DEACTIVATED' ? (
                        <span className="text-slate-600">{t('Cần tạo mã mới')}</span>
                      ) : (
                        <span className="text-slate-500">{t('Chưa tạo mã')}</span>
                      )}
                    </td>
                    <td className="px-3 py-2.5">
                      <ActivationStatusBadge status={row.activation_status} />
                    </td>
                    <td className="whitespace-nowrap px-3 py-2.5 tabular-nums text-slate-700">
                      {row.app_version ? `v${row.app_version.replace(/^v/i, '')}` : '—'}
                    </td>
                    <td className="whitespace-nowrap px-3 py-2.5 text-slate-700">
                      {formatOsLabel(row.device_os, row.os_version)}
                    </td>
                    <td className="max-w-40 truncate px-3 py-2.5 text-slate-700" title={row.device_model || ''}>
                      {row.device_model || '—'}
                    </td>
                    <td className="whitespace-nowrap px-3 py-2.5 text-xs tabular-nums text-slate-700">
                      {formatTimestamp(row.activated_at)}
                    </td>
                    <td className="whitespace-nowrap px-3 py-2.5 text-xs tabular-nums text-slate-700">
                      {formatTimestamp(row.last_seen_at)}
                    </td>
                    <td className="whitespace-nowrap px-3 py-2.5 text-end">
                      {canManage ? (
                        <div className="inline-flex items-center justify-end gap-1">
                          {(row.activation_status === 'NOT_ACTIVATED' ||
                            row.activation_status === 'KEY_EXPIRED' ||
                            row.activation_status === 'DEACTIVATED') && (
                            <Button
                              type="button"
                              size="sm"
                              variant="outline"
                              className="h-8 cursor-pointer px-2.5 text-xs"
                              disabled={busyAction || isSuspended}
                              onClick={() => void handleSingleActivate(row, false)}
                            >
                              <KeyRound size={13} aria-hidden="true" />
                              {t('Kích hoạt')}
                            </Button>
                          )}
                          {(row.activation_status === 'KEY_CREATED' || row.activation_status === 'ACTIVATED') && (
                            <Button
                              type="button"
                              size="sm"
                              variant="outline"
                              className="h-8 cursor-pointer px-2.5 text-xs"
                              disabled={busyAction || isSuspended}
                              title={t('Tạo mã mới (vô hiệu hóa mã/phiên cũ)')}
                              onClick={() => void handleSingleActivate(row, true)}
                            >
                              <RotateCw size={13} aria-hidden="true" />
                              {t('Tạo lại')}
                            </Button>
                          )}
                          {row.email && !isSuspended && (
                            <Button
                              type="button"
                              size="sm"
                              variant="ghost"
                              className="h-8 cursor-pointer px-2 text-xs text-slate-700 hover:text-slate-950"
                              disabled={busyAction}
                              title={`${t('Gửi mã kích hoạt tới')} ${row.email}`}
                              aria-label={`${t('Gửi email mã kích hoạt cho')} ${row.full_name}`}
                              onClick={() => void handleSingleSendEmail(row)}
                            >
                              <Mail size={14} aria-hidden="true" />
                            </Button>
                          )}
                          {(row.activation_status === 'ACTIVATED' || row.activation_status === 'KEY_CREATED') && (
                            <Button
                              type="button"
                              size="sm"
                              variant="ghost"
                              className="h-8 cursor-pointer px-2 text-xs text-red-700 hover:bg-red-50 hover:text-red-800"
                              disabled={busyAction}
                              title={t('Hủy kích hoạt ứng dụng')}
                              aria-label={`${t('Hủy kích hoạt ứng dụng của')} ${row.full_name}`}
                              onClick={() => setDeactivateTargetIds([row.driver_id])}
                            >
                              <PowerOff size={14} aria-hidden="true" />
                            </Button>
                          )}
                        </div>
                      ) : (
                        <span className="text-xs text-slate-400">—</span>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {totalPages > 1 && (
        <div className="flex items-center justify-between border-t border-slate-200 px-4 py-3 text-xs text-slate-600">
          <span className="tabular-nums">
            {t('Trang')} {page + 1} / {totalPages}
          </span>
          <div className="flex items-center gap-2">
            <Button
              type="button"
              size="sm"
              variant="outline"
              className="h-8 cursor-pointer px-2.5"
              disabled={page <= 0 || loading}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              <ChevronLeft size={14} aria-hidden="true" />
              {t('Trước')}
            </Button>
            <Button
              type="button"
              size="sm"
              variant="outline"
              className="h-8 cursor-pointer px-2.5"
              disabled={page + 1 >= totalPages || loading}
              onClick={() => setPage((p) => p + 1)}
            >
              {t('Sau')}
              <ChevronRight size={14} aria-hidden="true" />
            </Button>
          </div>
        </div>
      )}

      {issuedModalItems.length > 0 && (
        <DriverActivationCodeModal
          items={issuedModalItems}
          onClose={() => setIssuedModalItems([])}
          onEmailDelivered={() => void loadData()}
        />
      )}

      {deactivateTargetIds && deactivateTargetIds.length > 0 && (
        <ConfirmationDialog
          title={t('Hủy kích hoạt ứng dụng tài xế?')}
          description={
            deactivateTargetIds.length === 1
              ? `${t('Tài xế')} ${deactivateDriverNames} ${t(
                  'sẽ mất quyền truy cập vào ứng dụng di động ngay lập tức (phiên đăng nhập, Access Token và Refresh Token của thiết bị này sẽ bị thu hồi). Hồ sơ tài xế và lịch sử giao hàng vẫn được giữ nguyên.'
                )}`
              : `${deactivateTargetIds.length} ${t(
                  'tài xế đã chọn sẽ mất quyền truy cập vào ứng dụng di động ngay lập tức. Để sử dụng lại, bạn cần tạo mã kích hoạt mới.'
                )}`
          }
          confirmLabel={t('Xác nhận hủy kích hoạt')}
          destructive
          onCancel={() => setDeactivateTargetIds(null)}
          onConfirm={() => confirmDeactivation()}
        />
      )}
    </section>
  );
}
