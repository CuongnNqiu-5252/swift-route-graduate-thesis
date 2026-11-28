'use client';

import { useAuth } from '@/application/contexts/auth-context';
import {
  buildOperationsPersonDirectory,
  formatIncidentType,
  resolveDriverDisplayName,
  resolveReporterDisplayName,
} from '@/application/operations-display';
import type { TenantMember } from '@/domain/entities/member.entity';
import { useLocale } from '@/i18n/provider';
import type { Translator } from '@/i18n/translate';
import { memberRepository } from '@/infrastructure/repositories/member.repository';
import {
  AlertOctagon,
  AlertTriangle,
  Banknote,
  CheckCircle2,
  ClipboardCheck,
  Clock,
  Eye,
  Package,
  RefreshCw,
  RotateCcw,
  UserCheck,
} from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';

import { OperationsWeatherRisks } from '@/components/dispatch/route-weather-risk';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Modal } from '@/components/ui/modal';
import { CustomSelect } from '@/components/ui/select';
import type { Driver } from '@/domain/entities/driver.entity';
import { driverRepository } from '@/infrastructure/repositories/driver.repository';
import {
  operationsRepository,
  type IncidentDto,
  type ResolveIncidentParams,
  type ReturningOrderDto,
} from '@/infrastructure/repositories/operations.repository';

type TabKey = 'incidents' | 'returns' | 'cod';
type ReturnModalAction = 'CONFIRM_RETURN' | 'REQUEUE_DISPATCH';

type CodItem = {
  id: string;
  driver_id: string;
  ledger_total: number;
  declared_amount: number;
  order_count: number;
  order_codes: string[];
  notes: string | null;
  declared_at: string;
};

const uuidPattern = /^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i;

type CodVarianceResolution = 'DRIVER_RESPONSIBLE' | 'BUSINESS_ADJUSTMENT' | 'OTHER';

const formatVnd = (amount: number, locale: string) =>
  new Intl.NumberFormat(locale, { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(amount);

const formatDate = (isoStr: string | null | undefined, locale: string) => {
  if (!isoStr) return '—';
  try {
    return new Date(isoStr).toLocaleString(locale, {
      hour: '2-digit',
      minute: '2-digit',
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    });
  } catch {
    return isoStr;
  }
};

const incidentStatusBadge = (status: string, t: Translator) => {
  switch (status?.toUpperCase()) {
    case 'OPEN':
      return <Badge className="border border-rose-200 bg-rose-50 text-rose-700">{t('Đang chờ xử lý')}</Badge>;
    case 'IN_PROGRESS':
      return <Badge className="border border-amber-200 bg-amber-50 text-amber-700">{t('Đang xử lý')}</Badge>;
    case 'RESOLVED':
      return <Badge className="border border-emerald-200 bg-emerald-50 text-emerald-700">{t('Đã giải quyết')}</Badge>;
    default:
      return <Badge className="bg-slate-100 text-slate-700">{t('Chưa xác định')}</Badge>;
  }
};

const returningOrderStatusBadge = (status: ReturningOrderDto['status'], t: Translator) => {
  switch (status) {
    case 'RETURNING':
      return <Badge className="border border-amber-300 bg-amber-50 text-amber-800">{t('Đang hoàn về kho')}</Badge>;
    case 'FAILED':
      return <Badge className="border border-rose-200 bg-rose-50 text-rose-700">{t('Giao thất bại')}</Badge>;
    case 'RETURNED':
      return <Badge className="border border-emerald-200 bg-emerald-50 text-emerald-700">{t('Đã nhập kho hoàn')}</Badge>;
    default:
      return <Badge className="bg-slate-100 text-slate-700">{status}</Badge>;
  }
};

export default function OperationsPage() {
  const { t, intlLocale } = useLocale();
  const formatCurrency = (amount: number) => formatVnd(amount, intlLocale);
  const { user } = useAuth();
  const [memberDirectory, setMemberDirectory] = useState<{ tenantId: string; members: TenantMember[] }>();
  const tenantId = user?.tenantId;

  useEffect(() => {
    if (!tenantId) return;
    let active = true;
    memberRepository
      .getMembers(tenantId)
      .then((members) => {
        if (active) {
          setMemberDirectory({ tenantId, members });
        }
      })
      .catch(() => { });
    return () => {
      active = false;
    };
  }, [tenantId]);

  const [activeTab, setActiveTab] = useState<TabKey>('incidents');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [incidents, setIncidents] = useState<IncidentDto[]>([]);
  const [returningOrders, setReturningOrders] = useState<ReturningOrderDto[]>([]);
  const [codList, setCodList] = useState<CodItem[]>([]);
  const [codDrivers, setCodDrivers] = useState<Driver[]>([]);

  const personDirectory = useMemo(
    () =>
      buildOperationsPersonDirectory(
        codDrivers,
        memberDirectory && memberDirectory.tenantId === tenantId ? memberDirectory.members : [],
        user,
      ),
    [codDrivers, memberDirectory, tenantId, user],
  );

  // Filters
  const [incidentStatusFilter, setIncidentStatusFilter] = useState<string>('OPEN');
  const [returnStatusFilter, setReturnStatusFilter] = useState<string>('ACTIVE');

  // Modals
  const [selectedIncident, setSelectedIncident] = useState<IncidentDto | null>(null);
  const [selectedReturnOrder, setSelectedReturnOrder] = useState<{
    order: ReturningOrderDto;
    action: ReturnModalAction;
  } | null>(null);
  const [selectedCodReceipt, setSelectedCodReceipt] = useState<CodItem | null>(null);

  // Form states
  const [resolvingAction, setResolvingAction] = useState<ResolveIncidentParams['resolution_type'] | null>(null);
  const [resolutionNotes, setResolutionNotes] = useState('');
  const [returnActionNotes, setReturnActionNotes] = useState('');
  const [actionLoading, setActionLoading] = useState(false);

  const [codCountedTotal, setCodCountedTotal] = useState('');
  const [codVarianceResolution, setCodVarianceResolution] = useState<CodVarianceResolution>('BUSINESS_ADJUSTMENT');
  const [codSettlementNotes, setCodSettlementNotes] = useState('');
  const [codFormError, setCodFormError] = useState<string | null>(null);

  const parseCodQueue = (revRes: unknown): CodItem[] => {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const rawRev = (revRes as any)?.data ?? revRes;
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    return (rawRev?.cod_collections ?? rawRev?.codCollections ?? []).map((item: any) => ({
      id: String(item.id ?? ''),
      driver_id: String(item.driver_id ?? item.driverId ?? ''),
      ledger_total: Number(item.ledger_total ?? item.ledgerTotal ?? 0),
      declared_amount: Number(item.declared_amount ?? item.declaredAmount ?? 0),
      order_count: Number(item.order_count ?? item.orderCount ?? 0),
      order_codes: ((item.order_codes ?? item.orderCodes ?? []) as string[])
        .map((code) => code.trim())
        .filter((code) => code && !uuidPattern.test(code)),
      notes: (item.notes ?? null) as string | null,
      declared_at: String(item.declared_at ?? item.declaredAt ?? ''),
    }));
  };

  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [incRes, retRes, revRes, drivers] = await Promise.all([
        operationsRepository.getIncidents(),
        operationsRepository.getReturningOrders({ status: 'ALL' }),
        operationsRepository.getReviewQueue(),
        driverRepository.getDrivers().catch(() => []),
      ]);
      setIncidents(incRes);
      setReturningOrders(retRes);
      setCodList(parseCodQueue(revRes));
      setCodDrivers(drivers);
    } catch (err) {
      setError(err instanceof Error ? err.message : t('Không thể tải dữ liệu điều hành'));
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => {
    let active = true;
    Promise.all([
      operationsRepository.getIncidents(),
      operationsRepository.getReturningOrders({ status: 'ALL' }),
      operationsRepository.getReviewQueue(),
      driverRepository.getDrivers().catch(() => []),
    ])
      .then(([incRes, retRes, revRes, drivers]) => {
        if (!active) return;
        setIncidents(incRes);
        setReturningOrders(retRes);
        setCodList(parseCodQueue(revRes));
        setCodDrivers(drivers);
      })
      .catch((err) => {
        if (active) setError(err instanceof Error ? err.message : t('Không thể tải dữ liệu điều hành'));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [t]);

  const handleResolveIncident = async (type: ResolveIncidentParams['resolution_type']) => {
    if (!selectedIncident) return;
    setActionLoading(true);
    setError(null);
    try {
      await operationsRepository.resolveIncident(selectedIncident.id, {
        resolution_type: type,
        notes: resolutionNotes.trim() || undefined,
      });
      setSelectedIncident(null);
      setResolvingAction(null);
      setResolutionNotes('');
      await loadData();
    } catch (err) {
      setError(err instanceof Error ? err.message : t('Lỗi khi xử lý sự cố'));
    } finally {
      setActionLoading(false);
    }
  };

  const openReturnActionModal = (order: ReturningOrderDto, action: ReturnModalAction) => {
    setSelectedReturnOrder({ order, action });
    setReturnActionNotes('');
  };

  const handleSubmitReturnAction = async () => {
    if (!selectedReturnOrder) return;
    setActionLoading(true);
    setError(null);
    try {
      const notes = returnActionNotes.trim() || undefined;
      if (selectedReturnOrder.action === 'CONFIRM_RETURN') {
        await operationsRepository.confirmOrderReturn(selectedReturnOrder.order.order_id, notes);
      } else {
        await operationsRepository.requeueFailedOrder(selectedReturnOrder.order.order_id, notes);
      }
      setSelectedReturnOrder(null);
      setReturnActionNotes('');
      await loadData();
    } catch (err) {
      setError(err instanceof Error ? err.message : t('Không thể cập nhật trạng thái đơn hoàn'));
    } finally {
      setActionLoading(false);
    }
  };

  const openCodSettlement = (receipt: CodItem) => {
    setSelectedCodReceipt(receipt);
    setCodCountedTotal('');
    setCodVarianceResolution('BUSINESS_ADJUSTMENT');
    setCodSettlementNotes('');
    setCodFormError(null);
  };

  const handleSettleCod = async () => {
    if (!selectedCodReceipt) return;
    const countedTotal = Number(codCountedTotal);
    if (codCountedTotal.trim() === '' || !Number.isFinite(countedTotal) || countedTotal < 0) {
      setCodFormError(t('Nhập số tiền thu ngân thực tế đã kiểm đếm.'));
      return;
    }
    const hasVariance = countedTotal !== selectedCodReceipt.ledger_total;
    if (hasVariance && codSettlementNotes.trim() === '') {
      setCodFormError(t('Ghi rõ lý do và quyết định xử lý khi số tiền có chênh lệch.'));
      return;
    }
    setActionLoading(true);
    setError(null);
    setCodFormError(null);
    try {
      await operationsRepository.settleCodReceipt(selectedCodReceipt.id, {
        counted_total: countedTotal,
        ...(hasVariance ? { variance_resolution: codVarianceResolution } : {}),
        ...(codSettlementNotes.trim() ? { notes: codSettlementNotes.trim() } : {}),
      });
      setSelectedCodReceipt(null);
      await loadData();
    } catch (err) {
      setCodFormError(err instanceof Error ? err.message : t('Không quyết toán được phiếu nộp COD.'));
    } finally {
      setActionLoading(false);
    }
  };

  const filteredIncidents = incidents.filter((i) => {
    if (incidentStatusFilter !== 'ALL' && i.status !== incidentStatusFilter) return false;
    return true;
  });

  const filteredReturns = returningOrders.filter((item) => {
    if (returnStatusFilter === 'ACTIVE') return item.status === 'RETURNING' || item.status === 'FAILED';
    if (returnStatusFilter !== 'ALL' && item.status !== returnStatusFilter) return false;
    return true;
  });

  const parsedCodCount = codCountedTotal.trim() === '' ? null : Number(codCountedTotal);
  const codDifference =
    selectedCodReceipt && parsedCodCount !== null && Number.isFinite(parsedCodCount)
      ? parsedCodCount - selectedCodReceipt.ledger_total
      : null;
  const hasCodVariance = codDifference !== null && codDifference !== 0;

  const openIncidentsCount = incidents.filter((i) => i.status === 'OPEN' || i.status === 'IN_PROGRESS').length;
  const pendingReturnCheckInCount = returningOrders.filter(
    (item) => item.status === 'RETURNING' || item.status === 'FAILED',
  ).length;
  const returnedCompletedCount = returningOrders.filter((item) => item.status === 'RETURNED').length;

  return (
    <div className="mx-auto max-w-7xl space-y-6 pb-12">
      {/* Page Header */}
      <div className="flex flex-col gap-4 border-b border-slate-200 pb-5 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-950">
            {t('Trung tâm Điều hành & Xử lý Ngoại lệ')}
          </h1>
          <p className="mt-1 text-sm text-slate-600">
            {t('Xử lý các đơn giao chưa thành công, xác nhận kiện hoàn về kho và đối soát COD.')}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={() => void loadData()}
            isLoading={loading}
            className="cursor-pointer flex items-center gap-1.5"
          >
            <RefreshCw size={15} />
            {t('Làm mới')}
          </Button>
        </div>
      </div>

      <OperationsWeatherRisks />
      {error && (
        <div
          role="alert"
          className="flex items-center gap-3 rounded-sm border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800"
        >
          <AlertOctagon size={18} className="shrink-0 text-rose-600" />
          <span>{error}</span>
        </div>
      )}

      {/* Top Metric Cards */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
          <div className="flex items-center justify-between">
            <span className="text-sm font-medium text-slate-500">{t('Đơn chờ điều phối xử lý')}</span>
            <span className="rounded-sm bg-rose-50 p-2 text-rose-600">
              <AlertTriangle size={18} />
            </span>
          </div>
          <div className="mt-2 flex items-baseline gap-2 tabular-nums">
            <span className="text-2xl font-bold tracking-tight text-slate-900">{openIncidentsCount}</span>
            <span className="text-xs text-slate-500">
              {t('trên')} {incidents.length} {t('báo cáo')}
            </span>
          </div>
        </div>

        <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
          <div className="flex items-center justify-between">
            <span className="text-sm font-medium text-slate-500">{t('Đơn chờ nhập kho hoàn')}</span>
            <span className="rounded-sm bg-amber-50 p-2 text-amber-600">
              <RotateCcw size={18} />
            </span>
          </div>
          <div className="mt-2 flex items-baseline gap-2 tabular-nums">
            <span className="text-2xl font-bold tracking-tight text-slate-900">{pendingReturnCheckInCount}</span>
            <span className="text-xs text-slate-500">{t('đơn giao lỗi / đang hoàn')}</span>
          </div>
        </div>

        <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
          <div className="flex items-center justify-between">
            <span className="text-sm font-medium text-slate-500">{t('Đã nhập kho hoàn')}</span>
            <span className="rounded-sm bg-indigo-50 p-2 text-indigo-600">
              <Package size={18} />
            </span>
          </div>
          <div className="mt-2 flex items-baseline gap-2 tabular-nums">
            <span className="text-2xl font-bold tracking-tight text-slate-900">{returnedCompletedCount}</span>
            <span className="text-xs text-slate-500">{t('đơn đã nhận lại')}</span>
          </div>
        </div>

        <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
          <div className="flex items-center justify-between">
            <span className="text-sm font-medium text-slate-500">{t('COD chờ xác minh')}</span>
            <span className="rounded-sm bg-emerald-50 p-2 text-emerald-600">
              <Banknote size={18} />
            </span>
          </div>
          <div className="mt-2 flex items-baseline gap-2 tabular-nums">
            <span className="text-2xl font-bold tracking-tight text-slate-900">{codList.length}</span>
            <span className="text-xs text-slate-500">{t('phiếu nộp tiền')}</span>
          </div>
        </div>
      </div>

      {/* Main Tabs Navigation */}
      <div className="border-b border-slate-200">
        <nav className="-mb-px flex gap-6 overflow-x-auto" aria-label={t('Tabs')}>
          <button
            type="button"
            onClick={() => setActiveTab('incidents')}
            className={`cursor-pointer flex items-center gap-2 border-b-2 py-3 text-sm font-semibold transition-colors ${activeTab === 'incidents'
                ? 'border-rose-600 text-rose-600'
                : 'border-transparent text-slate-500 hover:border-slate-300 hover:text-slate-700'
              }`}
          >
            <AlertTriangle size={17} />
            <span>{t('Đơn giao chưa thành công')}</span>
            {openIncidentsCount > 0 && (
              <span className="rounded-full bg-rose-100 px-2 py-0.5 text-xs font-bold tabular-nums text-rose-700">
                {openIncidentsCount}
              </span>
            )}
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('returns')}
            className={`cursor-pointer flex items-center gap-2 border-b-2 py-3 text-sm font-semibold transition-colors ${activeTab === 'returns'
                ? 'border-indigo-600 text-indigo-600'
                : 'border-transparent text-slate-500 hover:border-slate-300 hover:text-slate-700'
              }`}
          >
            <RotateCcw size={17} />
            <span>{t('Hàng hoàn về kho')}</span>
            {pendingReturnCheckInCount > 0 && (
              <span className="rounded-full bg-indigo-100 px-2 py-0.5 text-xs font-bold tabular-nums text-indigo-700">
                {pendingReturnCheckInCount}
              </span>
            )}
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('cod')}
            className={`cursor-pointer flex items-center gap-2 border-b-2 py-3 text-sm font-semibold transition-colors ${activeTab === 'cod'
                ? 'border-emerald-600 text-emerald-600'
                : 'border-transparent text-slate-500 hover:border-slate-300 hover:text-slate-700'
              }`}
          >
            <Banknote size={17} />
            <span>{t('Đối soát COD')}</span>
            {codList.length > 0 && (
              <span className="rounded-full bg-emerald-100 px-2 py-0.5 text-xs font-bold tabular-nums text-emerald-700">
                {codList.length}
              </span>
            )}
          </button>
        </nav>
      </div>

      {/* ========================================================================= */}
      {/* TAB 1: FAILED DELIVERY EXCEPTIONS */}
      {/* ========================================================================= */}
      {activeTab === 'incidents' && (
        <div className="space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-3 rounded-sm border border-slate-200 bg-white p-3">
            <div className="flex items-center gap-1.5 text-xs font-medium text-slate-500">
              <span>{t('Trạng thái:')}</span>
              <CustomSelect
                size="sm"
                aria-label={t('Trạng thái:')}
                value={incidentStatusFilter}
                onValueChange={setIncidentStatusFilter}
                className="w-44"
                options={[
                  { value: 'OPEN', label: t('Đang chờ xử lý') },
                  { value: 'RESOLVED', label: t('Đã giải quyết') },
                  { value: 'ALL', label: t('Tất cả') },
                ]}
              />
            </div>

            <div className="text-xs tabular-nums text-slate-500">
              {t('Hiển thị')} <span className="font-semibold text-slate-800">{filteredIncidents.length}</span>{' '}
              {t('báo cáo')}
            </div>
          </div>

          <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm text-slate-600">
                <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wider text-slate-500">
                  <tr>
                    <th className="px-5 py-3 font-semibold">{t('Mã đơn')}</th>
                    <th className="px-5 py-3 font-semibold">{t('Lý do không giao được')}</th>
                    <th className="px-5 py-3 font-semibold">{t('Tài xế phụ trách')}</th>
                    <th className="px-5 py-3 font-semibold">{t('Thời gian')}</th>
                    <th className="px-5 py-3 font-semibold">{t('Trạng thái')}</th>
                    <th className="px-5 py-3 text-right font-semibold">{t('Thao tác')}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {filteredIncidents.map((inc) => (
                    <tr key={inc.id} className="transition-colors hover:bg-slate-50/75">
                      <td className="px-5 py-3.5">
                        <div className="font-mono text-sm font-semibold text-slate-900">
                          {inc.order_code || (inc.order_id ? `#${inc.order_id.slice(0, 8)}` : '—')}
                        </div>
                      </td>
                      <td className="px-5 py-3.5">
                        <div className="font-medium text-slate-800">{formatIncidentType(inc.incident_type, t)}</div>
                        {inc.notes && (
                          <div className="max-w-xs truncate text-xs text-slate-500" title={inc.notes}>
                            {inc.notes}
                          </div>
                        )}
                      </td>
                      <td className="px-5 py-3.5 text-xs font-medium text-slate-700">
                        {resolveDriverDisplayName(inc.driver_id, personDirectory, t, 'Chưa gán')}
                      </td>
                      <td className="px-5 py-3.5 text-xs tabular-nums text-slate-500">
                        {formatDate(inc.created_at, intlLocale)}
                      </td>
                      <td className="px-5 py-3.5">{incidentStatusBadge(inc.status, t)}</td>
                      <td className="px-5 py-3.5 text-right">
                        <Button
                          size="sm"
                          variant="outline"
                          onClick={() => {
                            setSelectedIncident(inc);
                            setResolvingAction(null);
                            setResolutionNotes('');
                          }}
                          className="cursor-pointer text-xs"
                        >
                          <Eye size={14} className="mr-1" />
                          {inc.status === 'RESOLVED' ? t('Chi tiết') : t('Xử lý')}
                        </Button>
                      </td>
                    </tr>
                  ))}

                  {filteredIncidents.length === 0 && (
                    <tr>
                      <td colSpan={6} className="px-5 py-12 text-center text-sm text-slate-500">
                        {t('Không có báo cáo nào phù hợp với bộ lọc.')}
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* ========================================================================= */}
      {/* TAB 2: STREAMLINED WAREHOUSE RETURN CHECK-IN & RE-DISPATCH */}
      {/* ========================================================================= */}
      {activeTab === 'returns' && (
        <div className="space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-3 rounded-sm border border-slate-200 bg-white p-3">
            <div className="flex flex-wrap items-center gap-3">
              <div className="flex items-center gap-1.5 text-xs font-medium text-slate-500">
                <span>{t('Trạng thái đơn:')}</span>
                <CustomSelect
                  size="sm"
                  aria-label={t('Trạng thái đơn:')}
                  value={returnStatusFilter}
                  onValueChange={setReturnStatusFilter}
                  className="w-52"
                  options={[
                    { value: 'ACTIVE', label: t('Chờ xử lý / Nhập kho') },
                    { value: 'RETURNING', label: t('Đang hoàn về kho') },
                    { value: 'FAILED', label: t('Giao thất bại') },
                    { value: 'RETURNED', label: t('Đã nhập kho hoàn') },
                    { value: 'ALL', label: t('Tất cả') },
                  ]}
                />
              </div>
            </div>

            <div className="text-xs tabular-nums text-slate-500">
              {t('Hiển thị')} <span className="font-semibold text-slate-800">{filteredReturns.length}</span>{' '}
              {t('đơn hàng')}
            </div>
          </div>

          <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm text-slate-600">
                <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wider text-slate-500">
                  <tr>
                    <th className="px-5 py-3 font-semibold">{t('Mã đơn')}</th>
                    <th className="px-5 py-3 font-semibold">{t('Lý do / Người nhận')}</th>
                    <th className="px-5 py-3 font-semibold">{t('Kho hoàn về')}</th>
                    <th className="px-5 py-3 font-semibold">{t('Tài xế phụ trách')}</th>
                    <th className="px-5 py-3 font-semibold">{t('Trạng thái')}</th>
                    <th className="px-5 py-3 text-right font-semibold">{t('Thao tác')}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {filteredReturns.map((item) => {
                    const isReturned = item.status === 'RETURNED';
                    return (
                      <tr key={item.order_id} className="transition-colors duration-150 hover:bg-slate-50/75">
                        <td className="px-5 py-3.5">
                          <div className="font-mono text-sm font-semibold text-slate-900">
                            {item.order_code || `#${item.order_id.slice(0, 8)}`}
                          </div>
                          <div className="mt-0.5 text-xs tabular-nums text-slate-500">
                            {formatDate(item.updated_at, intlLocale)}
                          </div>
                        </td>
                        <td className="px-5 py-3.5">
                          <div
                            className="max-w-xs truncate text-sm font-medium text-slate-800"
                            title={item.latest_reason ?? undefined}
                          >
                            {item.latest_reason || t('Giao hàng không thành công')}
                          </div>
                          <div className="max-w-xs truncate text-xs text-slate-500" title={item.delivery_address}>
                            {item.recipient_name ? `${item.recipient_name} · ` : ''}
                            {item.delivery_address}
                          </div>
                        </td>
                        <td className="px-5 py-3.5">
                          <div className="font-medium text-slate-800">
                            {item.pickup_location_name || t('Kho lấy ban đầu')}
                          </div>
                          {item.pickup_address && (
                            <div className="max-w-xs truncate text-xs text-slate-500" title={item.pickup_address}>
                              {item.pickup_address}
                            </div>
                          )}
                        </td>
                        <td className="px-5 py-3.5">
                          {item.assigned_driver_id ? (
                            <span className="inline-flex items-center gap-1.5 text-xs font-medium text-slate-700">
                              <UserCheck size={13} aria-hidden="true" className="shrink-0 text-[#17766c]" />
                              <span>{resolveDriverDisplayName(item.assigned_driver_id, personDirectory, t)}</span>
                            </span>
                          ) : (
                            <span className="text-xs text-slate-400">{t('Chưa gán tài xế')}</span>
                          )}
                        </td>
                        <td className="px-5 py-3.5">{returningOrderStatusBadge(item.status, t)}</td>
                        <td className="px-5 py-3.5 text-right">
                          {!isReturned ? (
                            <div className="flex items-center justify-end gap-1.5">
                              <Button
                                size="sm"
                                variant="outline"
                                onClick={() => openReturnActionModal(item, 'REQUEUE_DISPATCH')}
                                className="cursor-pointer text-xs"
                              >
                                <Clock size={13} aria-hidden="true" className="mr-1 text-blue-600" />
                                {t('Giao lại ca sau')}
                              </Button>
                              <Button
                                size="sm"
                                onClick={() => openReturnActionModal(item, 'CONFIRM_RETURN')}
                                className="cursor-pointer bg-emerald-600 text-xs text-white hover:bg-emerald-700"
                              >
                                <ClipboardCheck size={13} aria-hidden="true" className="mr-1" />
                                {t('Xác nhận nhập kho')}
                              </Button>
                            </div>
                          ) : (
                            <span className="inline-flex items-center gap-1 text-xs font-medium text-emerald-700">
                              <CheckCircle2 size={13} aria-hidden="true" />
                              <span>{t('Đã hoàn tất')}</span>
                            </span>
                          )}
                        </td>
                      </tr>
                    );
                  })}

                  {filteredReturns.length === 0 && (
                    <tr>
                      <td colSpan={6} className="px-5 py-12 text-center text-sm text-slate-500">
                        {t('Không có đơn hoàn kho nào trong mục này.')}
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* ========================================================================= */}
      {/* TAB 3: COD RECONCILIATION */}
      {/* ========================================================================= */}
      {activeTab === 'cod' && (
        <div className="space-y-4">
          <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="text-base font-semibold text-slate-900">{t('Phiếu nộp COD chờ kiểm đếm')}</h2>
            <p className="mt-1 text-sm text-slate-500">
              {t('Mỗi phiếu gồm các đơn tài xế nộp cùng một lần. Thu ngân nhập số thực đếm để quyết toán.')}
            </p>

            <div className="mt-4 divide-y divide-slate-100">
              {codList.map((item) => (
                <div
                  key={item.id}
                  className="flex flex-col gap-3 py-4 sm:flex-row sm:items-center sm:justify-between"
                >
                  <div>
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="font-semibold text-slate-900">
                        {t('Phiếu #')}
                        {item.id.slice(0, 8)}
                      </span>
                      <Badge className="border border-slate-200 bg-slate-50 tabular-nums text-slate-700">
                        {item.order_count} {t('đơn')}
                      </Badge>
                    </div>
                    <div className="mt-1 text-sm text-slate-700">
                      {t('Theo hệ thống:')}{' '}
                      <span className="font-semibold tabular-nums">{formatCurrency(item.ledger_total)}</span>
                      {' · '}
                      {t('Tài xế khai nộp:')}{' '}
                      <span className="font-semibold tabular-nums">{formatCurrency(item.declared_amount)}</span>
                    </div>
                    <div className="mt-1 text-xs tabular-nums text-slate-500">
                      {t('Tài xế:')}{' '}
                      {resolveDriverDisplayName(item.driver_id, personDirectory, t, 'Chưa có tên')}{' '}
                      {t('· Gửi lúc:')} {formatDate(item.declared_at, intlLocale)}
                    </div>
                    {item.order_codes.length > 0 && (
                      <div className="mt-1 max-w-3xl truncate text-xs text-slate-500">
                        {t('Mã đơn:')} {item.order_codes.join(', ')}
                      </div>
                    )}
                  </div>

                  <Button
                    size="sm"
                    onClick={() => openCodSettlement(item)}
                    isLoading={actionLoading}
                    className="cursor-pointer shrink-0 bg-slate-900 text-white hover:bg-slate-800"
                  >
                    <Banknote size={15} className="mr-1" />
                    {t('Nhập tiền thực đếm')}
                  </Button>
                </div>
              ))}

              {codList.length === 0 && (
                <p className="py-8 text-center text-sm text-slate-500">
                  {t('Không có phiếu nộp COD nào đang chờ kiểm đếm.')}
                </p>
              )}
            </div>
          </div>
        </div>
      )}

      {/* ========================================================================= */}
      {/* MODAL: COD SETTLEMENT */}
      {/* ========================================================================= */}
      <Modal
        open={selectedCodReceipt !== null}
        onOpenChange={(open) => {
          if (!open) {
            setSelectedCodReceipt(null);
            setCodFormError(null);
          }
        }}
        title={t('Kiểm đếm phiếu nộp COD')}
        description={t('Nhập số tiền thực tế tại quầy. Nếu có chênh lệch, quyết toán ngay cùng lý do xử lý.')}
        className="max-w-xl"
      >
        {selectedCodReceipt && (
          <div className="space-y-5 text-sm">
            <div className="grid gap-3 rounded-sm bg-slate-50 p-4 sm:grid-cols-2">
              <div>
                <p className="text-xs text-slate-500">{t('Theo hệ thống')}</p>
                <p className="mt-1 font-semibold tabular-nums text-slate-900">
                  {formatCurrency(selectedCodReceipt.ledger_total)}
                </p>
              </div>
              <div>
                <p className="text-xs text-slate-500">{t('Tài xế khai nộp')}</p>
                <p className="mt-1 font-semibold tabular-nums text-slate-900">
                  {formatCurrency(selectedCodReceipt.declared_amount)}
                </p>
              </div>
            </div>

            <div>
              <label htmlFor="cod-counted-total" className="mb-2 block font-medium text-slate-800">
                {t('Số tiền thu ngân thực đếm (VND)')}
              </label>
              <input
                id="cod-counted-total"
                type="number"
                min={0}
                step={1}
                inputMode="numeric"
                autoFocus
                value={codCountedTotal}
                onChange={(event) => {
                  setCodCountedTotal(event.target.value);
                  setCodFormError(null);
                }}
                placeholder={t('Nhập số tiền đã đếm')}
                className="h-11 w-full rounded-sm border border-slate-300 bg-white px-3 text-base tabular-nums text-slate-900 outline-none focus:border-slate-700 focus:ring-2 focus:ring-slate-200"
              />
            </div>

            {codDifference !== null && (
              <div
                className={`rounded-sm px-4 py-3 ${hasCodVariance ? 'bg-amber-50 text-amber-900' : 'bg-emerald-50 text-emerald-900'
                  }`}
              >
                {hasCodVariance
                  ? `${t('Chênh lệch')} ${codDifference > 0 ? t('thừa') : t('thiếu')} ${formatCurrency(
                    Math.abs(codDifference),
                  )}.`
                  : t('Số tiền thực đếm khớp với hệ thống.')}
              </div>
            )}

            {hasCodVariance && (
              <>
                <div>
                  <label htmlFor="cod-variance-resolution" className="mb-2 block font-medium text-slate-800">
                    {t('Cách ghi nhận chênh lệch')}
                  </label>
                  <CustomSelect
                    id="cod-variance-resolution"
                    value={codVarianceResolution}
                    onValueChange={(next) => setCodVarianceResolution(next as CodVarianceResolution)}
                    options={[
                      { value: 'BUSINESS_ADJUSTMENT', label: t('Doanh nghiệp chấp nhận điều chỉnh') },
                      { value: 'DRIVER_RESPONSIBLE', label: t('Ghi nhận tài xế chịu trách nhiệm') },
                      { value: 'OTHER', label: t('Xử lý theo quyết định khác') },
                    ]}
                  />
                </div>
                <div>
                  <label htmlFor="cod-settlement-notes" className="mb-2 block font-medium text-slate-800">
                    {t('Lý do và quyết định xử lý')}
                  </label>
                  <textarea
                    id="cod-settlement-notes"
                    rows={3}
                    value={codSettlementNotes}
                    onChange={(event) => {
                      setCodSettlementNotes(event.target.value);
                      setCodFormError(null);
                    }}
                    placeholder={t('Ví dụ: doanh nghiệp chấp nhận thiếu 20.000₫ do sai lệch khi đối soát.')}
                    className="w-full rounded-sm border border-slate-300 bg-white px-3 py-2 text-slate-900 outline-none focus:border-slate-700 focus:ring-2 focus:ring-slate-200"
                  />
                </div>
              </>
            )}

            {codFormError && (
              <p role="alert" className="rounded-sm bg-rose-50 px-3 py-2 text-sm text-rose-800">
                {codFormError}
              </p>
            )}

            <div className="flex flex-col-reverse gap-2 border-t border-slate-200 pt-4 sm:flex-row sm:justify-end">
              <Button
                variant="outline"
                onClick={() => setSelectedCodReceipt(null)}
                disabled={actionLoading}
                className="cursor-pointer"
              >
                {t('Đóng')}
              </Button>
              <Button
                onClick={() => void handleSettleCod()}
                isLoading={actionLoading}
                className="cursor-pointer bg-slate-900 text-white hover:bg-slate-800"
              >
                {hasCodVariance ? t('Quyết toán có chênh lệch') : t('Xác nhận đã nhận đủ')}
              </Button>
            </div>
          </div>
        )}
      </Modal>

      {/* ========================================================================= */}
      {/* MODAL: FAILED DELIVERY DETAIL & RESOLUTION */}
      {/* ========================================================================= */}
      <Modal
        open={selectedIncident !== null}
        onOpenChange={(open) => {
          if (!open) {
            setSelectedIncident(null);
            setResolvingAction(null);
          }
        }}
        title={`${t('Chi tiết đơn giao lỗi:')} ${selectedIncident?.order_code ||
          (selectedIncident?.order_id ? `#${selectedIncident.order_id.slice(0, 8)}` : '')
          }`}
        description={t('Lý do tài xế ghi nhận tại điểm giao và quyết định điều phối cho đơn hàng.')}
        className="max-w-2xl"
      >
        {selectedIncident && (
          <div className="space-y-5">
            <div className="space-y-2.5 rounded-sm border border-slate-200 bg-slate-50 p-4 text-sm">
              <div className="flex items-center justify-between">
                <span className="font-semibold text-slate-900">
                  {formatIncidentType(selectedIncident.incident_type, t)}
                </span>
                {incidentStatusBadge(selectedIncident.status, t)}
              </div>

              {selectedIncident.notes && (
                <p className="rounded border border-slate-200 bg-white p-2.5 text-xs text-slate-700">
                  <span className="font-semibold">{t('Ghi chú hiện trường:')}</span> {selectedIncident.notes}
                </p>
              )}

              {selectedIncident.resolution_notes && (
                <p className="rounded border border-emerald-200 bg-emerald-50/70 p-2.5 text-xs text-emerald-900">
                  <span className="font-semibold">{t('Kết quả xử lý:')}</span> {selectedIncident.resolution_notes}
                </p>
              )}

              <div className="grid grid-cols-2 gap-2 pt-1 text-xs text-slate-600">
                <div>
                  <span className="text-slate-400">{t('Người báo cáo:')}</span>{' '}
                  {resolveReporterDisplayName(selectedIncident.reported_by, personDirectory, t)}
                </div>
                <div className="tabular-nums">
                  <span className="text-slate-400">{t('Thời gian:')}</span>{' '}
                  {formatDate(selectedIncident.created_at, intlLocale)}
                </div>
                <div>
                  <span className="text-slate-400">{t('Tài xế phụ trách:')}</span>{' '}
                  {resolveDriverDisplayName(selectedIncident.driver_id, personDirectory, t, 'Chưa gán')}
                </div>
              </div>

              {selectedIncident.evidence_urls && selectedIncident.evidence_urls.length > 0 && (
                <div className="pt-2">
                  <span className="mb-1.5 block text-xs font-semibold text-slate-700">
                    {t('Ảnh bằng chứng hiện trường:')}
                  </span>
                  <div className="flex flex-wrap gap-2">
                    {selectedIncident.evidence_urls.map((url, idx) => (
                      <a
                        key={idx}
                        href={url}
                        target="_blank"
                        rel="noreferrer"
                        className="group relative block h-20 w-20 overflow-hidden rounded-sm border border-slate-200 bg-slate-100"
                      >
                        {/* eslint-disable-next-line @next/next/no-img-element */}
                        <img
                          src={url}
                          alt={t('Bằng chứng hiện trường')}
                          className="h-full w-full object-cover transition-transform group-hover:scale-105"
                        />
                      </a>
                    ))}
                  </div>
                </div>
              )}
            </div>

            {selectedIncident.status !== 'RESOLVED' && (
              <div className="space-y-3 border-t border-slate-200 pt-4">
                <h3 className="text-sm font-semibold text-slate-900">{t('Chọn hướng xử lý cho đơn hàng:')}</h3>

                <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
                  <Button
                    size="sm"
                    variant={resolvingAction === 'RETRY_DELIVERY' ? 'default' : 'outline'}
                    onClick={() => setResolvingAction('RETRY_DELIVERY')}
                    className="cursor-pointer h-auto justify-start py-2.5 text-xs"
                  >
                    <Clock size={14} className="mr-1.5 shrink-0 text-blue-600" />
                    <div>
                      <div className="text-left font-semibold">{t('Giao lại ca sau')}</div>
                      <div className="text-[10px] font-normal text-slate-500">{t('Đưa về hàng chờ điều phối')}</div>
                    </div>
                  </Button>

                  <Button
                    size="sm"
                    variant={resolvingAction === 'REQUEST_RETURN' ? 'default' : 'outline'}
                    onClick={() => setResolvingAction('REQUEST_RETURN')}
                    className="cursor-pointer h-auto justify-start py-2.5 text-xs"
                  >
                    <RotateCcw size={14} className="mr-1.5 shrink-0 text-indigo-600" />
                    <div>
                      <div className="text-left font-semibold">{t('Hoàn hàng về kho')}</div>
                      <div className="text-[10px] font-normal text-slate-500">{t('Chuyển sang chờ nhập kho hoàn')}</div>
                    </div>
                  </Button>
                </div>

                {resolvingAction && (
                  <div className="mt-3 space-y-2 rounded-sm border border-indigo-100 bg-indigo-50/50 p-3">
                    <label className="text-xs font-semibold text-slate-800">{t('Ghi chú điều phối (Tùy chọn):')}</label>
                    <textarea
                      value={resolutionNotes}
                      onChange={(e) => setResolutionNotes(e.target.value)}
                      rows={2}
                      placeholder={t('Nhập ghi chú hoặc khung giờ khách hẹn lại...')}
                      className="w-full rounded-md border border-slate-300 bg-white p-2 text-xs text-slate-900 focus:outline-none focus:ring-1 focus:ring-indigo-500"
                    />
                    <div className="flex justify-end gap-2 pt-1">
                      <Button
                        size="sm"
                        variant="outline"
                        onClick={() => setResolvingAction(null)}
                        className="cursor-pointer"
                      >
                        {t('Hủy')}
                      </Button>
                      <Button
                        size="sm"
                        onClick={() => void handleResolveIncident(resolvingAction)}
                        isLoading={actionLoading}
                        className="cursor-pointer bg-indigo-600 text-white hover:bg-indigo-700"
                      >
                        {t('Xác nhận xử lý')}
                      </Button>
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>
        )}
      </Modal>

      {/* ========================================================================= */}
      {/* MODAL: WAREHOUSE RETURN CHECK-IN / REQUEUE FOR NEXT SHIFT */}
      {/* ========================================================================= */}
      <Modal
        open={selectedReturnOrder !== null}
        onOpenChange={(open) => {
          if (!open) {
            setSelectedReturnOrder(null);
            setReturnActionNotes('');
          }
        }}
        title={
          selectedReturnOrder?.action === 'CONFIRM_RETURN'
            ? `${t('Xác nhận nhập kho hoàn:')} ${selectedReturnOrder.order.order_code || `#${selectedReturnOrder.order.order_id.slice(0, 8)}`
            }`
            : `${t('Đưa lại hàng chờ điều phối:')} ${selectedReturnOrder?.order.order_code ||
            (selectedReturnOrder ? `#${selectedReturnOrder.order.order_id.slice(0, 8)}` : '')
            }`
        }
        description={
          selectedReturnOrder?.action === 'CONFIRM_RETURN'
            ? t('Xác nhận tài xế đã mang kiện hàng giao không thành công bàn giao lại cho kho.')
            : t('Tách đơn khỏi tuyến cũ và đưa về trạng thái sẵn sàng điều phối cho ca tiếp theo.')
        }
        className="max-w-lg"
      >
        {selectedReturnOrder && (
          <div className="space-y-4 text-sm">
            <div className="space-y-2 rounded-sm border border-slate-200 bg-slate-50 p-3.5 text-xs">
              <div className="flex items-center justify-between">
                <span className="font-semibold text-slate-900">
                  {selectedReturnOrder.order.recipient_name || t('Khách nhận hàng')}
                </span>
                {returningOrderStatusBadge(selectedReturnOrder.order.status, t)}
              </div>
              <div className="text-slate-600">{selectedReturnOrder.order.delivery_address}</div>
              {selectedReturnOrder.order.latest_reason && (
                <div className="rounded border border-slate-200 bg-white px-2.5 py-1.5 text-slate-700">
                  <span className="font-semibold">{t('Lý do gần nhất:')}</span>{' '}
                  {selectedReturnOrder.order.latest_reason}
                </div>
              )}
            </div>

            <div>
              <label htmlFor="return-action-notes" className="mb-1.5 block text-xs font-semibold text-slate-800">
                {t('Ghi chú (tùy chọn)')}
              </label>
              <textarea
                id="return-action-notes"
                rows={2}
                value={returnActionNotes}
                onChange={(e) => setReturnActionNotes(e.target.value)}
                placeholder={
                  selectedReturnOrder.action === 'CONFIRM_RETURN'
                    ? t('VD: Đã kiểm nhận nguyên kiện tại kho...')
                    : t('VD: Khách hẹn giao lại vào ca sáng mai...')
                }
                className="w-full rounded-sm border border-slate-300 px-3 py-2 text-xs text-slate-900 focus:border-slate-700 focus:outline-none focus:ring-2 focus:ring-slate-200"
              />
            </div>

            <div className="flex justify-end gap-2 border-t border-slate-200 pt-4">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setSelectedReturnOrder(null)}
                disabled={actionLoading}
                className="cursor-pointer"
              >
                {t('Hủy')}
              </Button>
              <Button
                size="sm"
                onClick={() => void handleSubmitReturnAction()}
                isLoading={actionLoading}
                className={
                  selectedReturnOrder.action === 'CONFIRM_RETURN'
                    ? 'cursor-pointer bg-emerald-600 text-white hover:bg-emerald-700'
                    : 'cursor-pointer bg-blue-600 text-white hover:bg-blue-700'
                }
              >
                {selectedReturnOrder.action === 'CONFIRM_RETURN'
                  ? t('Xác nhận đã nhập kho')
                  : t('Đưa về hàng chờ điều phối')}
              </Button>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
}
