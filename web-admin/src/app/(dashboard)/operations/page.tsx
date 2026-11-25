'use client';

import type { Translator } from "@/i18n/translate";
import { useLocale } from '@/i18n/provider';

import { useAuth } from '@/application/contexts/auth-context';
import {
  buildOperationsPersonDirectory,
  formatDiscrepancyType,
  formatIncidentType,
  resolveDriverDisplayName,
  resolveReporterDisplayName,
} from '@/application/operations-display';
import type { TenantMember } from '@/domain/entities/member.entity';
import { memberRepository } from '@/infrastructure/repositories/member.repository';
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  AlertTriangle,
  RotateCcw,
  Banknote,
  Check,
  CheckCircle2,
  ClipboardCheck,
  ExternalLink,
  Eye,
  RefreshCw,
  Clock,
  ShieldAlert,
  Package,
  UserCheck,
  AlertOctagon,
} from 'lucide-react';

import { Button } from '@/components/ui/button';
import { CustomSelect } from '@/components/ui/select';
import { Badge } from '@/components/ui/badge';
import { Modal } from '@/components/ui/modal';
import { OperationsWeatherRisks } from '@/components/dispatch/route-weather-risk';
import {
  operationsRepository,
  IncidentDto,
  ReturnRequestDto,
  ResolveIncidentParams,
  ReportDiscrepancyParams,
} from '@/infrastructure/repositories/operations.repository';

import { driverRepository } from '@/infrastructure/repositories/driver.repository';
import type { Driver } from '@/domain/entities/driver.entity';
import type { OrderHandoverDto } from '@/infrastructure/repositories/operations.repository';

type TabKey = 'incidents' | 'returns' | 'cod' | 'handovers';

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

const severityBadge = (severity: string, t: Translator) => {
  switch (severity?.toUpperCase()) {
    case 'CRITICAL':
      return <Badge className="bg-red-600 text-white font-semibold">{t("Khẩn cấp")}</Badge>;
    case 'HIGH':
      return <Badge className="bg-rose-100 text-rose-800 border border-rose-300">{t("Cao")}</Badge>;
    case 'MEDIUM':
      return <Badge className="bg-amber-100 text-amber-800 border border-amber-300">{t("Trung bình")}</Badge>;
    case 'LOW':
      return <Badge className="bg-blue-100 text-blue-800 border border-blue-300">{t("Thấp")}</Badge>;
    default:
      return <Badge className="bg-slate-100 text-slate-700">{t("Chưa xác định")}</Badge>;
  }
};

const incidentStatusBadge = (status: string, t: Translator) => {
  switch (status?.toUpperCase()) {
    case 'OPEN':
      return <Badge className="bg-rose-50 text-rose-700 border border-rose-200">{t("Đang chờ xử lý")}</Badge>;
    case 'IN_PROGRESS':
      return <Badge className="bg-amber-50 text-amber-700 border border-amber-200">{t("Đang xử lý")}</Badge>;
    case 'ESCALATED':
      return <Badge className="bg-purple-100 text-purple-800 border border-purple-300">{t("Đã leo thang")}</Badge>;
    case 'RESOLVED':
      return <Badge className="bg-emerald-50 text-emerald-700 border border-emerald-200">{t("Đã giải quyết")}</Badge>;
    case 'CANCELLED':
      return <Badge className="bg-slate-100 text-slate-600">{t("Đã hủy")}</Badge>;
    default:
      return <Badge className="bg-slate-100 text-slate-700">{t("Chưa xác định")}</Badge>;
  }
};

const returnStatusBadge = (status: string, t: Translator) => {
  switch (status?.toUpperCase()) {
    case 'REQUESTED':
      return <Badge className="bg-amber-100 text-amber-800 border border-amber-300">{t("Chờ duyệt")}</Badge>;
    case 'IN_EXECUTION':
      return <Badge className="bg-sky-50 text-sky-700 border border-sky-200">{t("Đang hoàn về")}</Badge>;
    case 'ARRIVED':
      return <Badge className="bg-indigo-50 text-indigo-700 border border-indigo-200">{t("Đã đến điểm hoàn")}</Badge>;
    case 'AWAITING_RECEIPT':
      return <Badge className="bg-orange-100 text-orange-800 border border-orange-300 animate-pulse">{t("Chờ kiểm nhận")}</Badge>;
    case 'DISCREPANCY':
      return <Badge className="bg-rose-600 text-white font-semibold">{t("Có chênh lệch / Hỏng")}</Badge>;
    case 'EXECUTION_FAILED':
      return <Badge className="bg-rose-100 text-rose-800 border border-rose-300">{t("Hoàn hàng thất bại")}</Badge>;
    case 'COMPLETED':
      return <Badge className="bg-emerald-50 text-emerald-700 border border-emerald-200">{t("Đã nhận hàng vào kho")}</Badge>;
    case 'REJECTED':
      return <Badge className="bg-slate-100 text-slate-600">{t("Bị từ chối")}</Badge>;
    case 'CANCELLED':
      return <Badge className="bg-slate-100 text-slate-600">{t("Đã hủy")}</Badge>;
    default:
      return <Badge className="bg-slate-100 text-slate-700">{t("Chưa xác định")}</Badge>;
  }
};

function OrderHandoverQueue() {
  const { t } = useLocale();
  const [requests, setRequests] = useState<OrderHandoverDto[] | null>(null);
  const [drivers, setDrivers] = useState<Driver[]>([]);
  const [selected, setSelected] = useState<OrderHandoverDto | null>(null);
  const [driverId, setDriverId] = useState('');
  const [notes, setNotes] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const driverDirectory = useMemo(
    () => buildOperationsPersonDirectory(drivers, []),
    [drivers],
  );

  const refresh = useCallback(async () => {
    try {
      const [queue, roster] = await Promise.all([
        operationsRepository.getOrderHandovers(),
        driverRepository.getDrivers(),
      ]);
      setRequests(queue);
      setDrivers(roster);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : t("Không tải được yêu cầu bàn giao."));
    }
  }, [t]);

  useEffect(() => {
    const timer = window.setTimeout(() => void refresh(), 0);
    return () => window.clearTimeout(timer);
  }, [refresh]);

  async function submit(action: 'assign' | 'reject' | 'cancel') {
    if (!selected) return;
    setBusy(true);
    setError(null);
    try {
      if (action === 'assign') {
        await operationsRepository.assignOrderHandover(selected.handover_id, driverId);
      } else {
        await operationsRepository.closeOrderHandover(selected.handover_id, action, notes.trim());
      }
      setSelected(null);
      await refresh();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : t("Không cập nhật được yêu cầu."));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="space-y-3">
      {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
      {!requests && !error && <p>{t("Đang tải yêu cầu bàn giao…")}</p>}
      {requests?.length === 0 && <p>{t("Hiện chưa có yêu cầu bàn giao.")}</p>}
      {requests?.map((request) => (
        <article key={request.handover_id} className="flex flex-wrap items-center justify-between gap-3 rounded-lg border p-4">
          <div>
            <p className="font-semibold">{request.order_code ?? request.order_id}</p>
            <p className="text-sm">{request.reason}</p>
            <p className="text-sm text-slate-600">
              {t("Tài xế gửi:")}{" "}{resolveDriverDisplayName(request.from_driver_id, driverDirectory, t)}
              {request.to_driver_id && (
                <>{" "}{t("· Tài xế nhận:")}{" "}{resolveDriverDisplayName(request.to_driver_id, driverDirectory, t)}</>
              )}
            </p>
            <p className="text-sm text-slate-600">
              {request.status === 'REQUESTED' ? t("Chờ chọn tài xế nhận") : t("Chờ tài xế nhận xác nhận")}
            </p>
          </div>
          <Button variant="outline" onClick={() => {
            setSelected(request);
            setDriverId('');
            setNotes('');
            setError(null);
          }}>{t("Xử lý")}</Button>
        </article>
      ))}
      {selected && (
        <div className="space-y-3 rounded-lg border p-4">
          <p className="font-semibold">{selected.order_code ?? selected.order_id}</p>
          {selected.status === 'REQUESTED' && (
            <div className="space-y-1 text-sm">
              <span>{t("Tài xế nhận")}</span>
              <CustomSelect
                value={driverId}
                placeholder={t("Chọn tài xế")}
                onValueChange={setDriverId}
                className="max-w-sm"
                options={drivers
                  .filter(
                    (driver) =>
                      driver.onboarding_status === 'ACTIVE' &&
                      driver.user_id &&
                      driver.user_id !== selected.from_driver_id,
                  )
                  .map((driver) => ({
                    value: driver.user_id!,
                    label: driver.name,
                  }))}
              />
            </div>
          )}
          <label className="block text-sm">
            {t("Lý do từ chối hoặc hủy")}
            <textarea className="mt-1 block w-full rounded border p-2" maxLength={1000}
              value={notes} onChange={(event) => setNotes(event.target.value)} />
          </label>
          <div className="flex flex-wrap gap-2">
            {selected.status === 'REQUESTED' ? (
              <>
                <Button disabled={busy || !driverId} onClick={() => void submit('assign')}>{t("Chỉ định tài xế")}</Button>
                <Button variant="outline" disabled={busy || !notes.trim()} onClick={() => void submit('reject')}>{t("Từ chối")}</Button>
              </>
            ) : (
              <Button variant="outline" disabled={busy || !notes.trim()} onClick={() => void submit('cancel')}>{t("Hủy bàn giao")}</Button>
            )}
            <Button variant="ghost" disabled={busy} onClick={() => setSelected(null)}>{t("Đóng")}</Button>
          </div>
        </div>
      )}
    </section>
  );
}

export default function OperationsPage() {
  const { t, intlLocale } = useLocale();
  const formatCurrency = (amount: number) => formatVnd(amount, intlLocale);
  const { user } = useAuth();
  const [memberDirectory, setMemberDirectory] = useState<{ tenantId: string; members: TenantMember[] }>();
  const tenantId = user?.tenantId;
  useEffect(() => {
    if (!tenantId) return;
    let active = true;
    memberRepository.getMembers(tenantId).then((members) => {
      if (active) {
        setMemberDirectory({ tenantId, members });
      }
    }).catch(() => {});
    return () => { active = false; };
  }, [tenantId]);

  const [activeTab, setActiveTab] = useState<TabKey>('incidents');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [incidents, setIncidents] = useState<IncidentDto[]>([]);
  const [returns, setReturns] = useState<ReturnRequestDto[]>([]);
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
  const [incidentStatusFilter, setIncidentStatusFilter] = useState<string>('ALL');
  const [incidentSeverityFilter, setIncidentSeverityFilter] = useState<string>('ALL');
  const [returnStatusFilter, setReturnStatusFilter] = useState<string>('ACTIVE');

  // Modals & Drawers
  const [selectedIncident, setSelectedIncident] = useState<IncidentDto | null>(null);
  const [selectedReturn, setSelectedReturn] = useState<ReturnRequestDto | null>(null);
  const [selectedCodReceipt, setSelectedCodReceipt] = useState<CodItem | null>(null);

  // Form states in modals
  const [resolvingAction, setResolvingAction] = useState<ResolveIncidentParams['resolution_type'] | null>(null);
  const [resolutionNotes, setResolutionNotes] = useState('');
  const [actionLoading, setActionLoading] = useState(false);
  const [codCountedTotal, setCodCountedTotal] = useState('');
  const [codVarianceResolution, setCodVarianceResolution] = useState<CodVarianceResolution>('BUSINESS_ADJUSTMENT');
  const [codSettlementNotes, setCodSettlementNotes] = useState('');
  const [codFormError, setCodFormError] = useState<string | null>(null);

  // Discrepancy & Receipt Forms
  const [discrepancyModalOpen, setDiscrepancyModalOpen] = useState(false);
  const [discrepancyType, setDiscrepancyType] = useState('DAMAGED');
  const [discrepancyNotes, setDiscrepancyNotes] = useState('');
  const [discrepancyFile, setDiscrepancyFile] = useState<File | null>(null);
  const [receiptNotes, setReceiptNotes] = useState('');
  const [receiptFormError, setReceiptFormError] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [incRes, retRes, revRes, drivers] = await Promise.all([
        operationsRepository.getIncidents(),
        operationsRepository.getReturns(),
        operationsRepository.getReviewQueue(),
        driverRepository.getDrivers().catch(() => []),
      ]);
      setIncidents(incRes);
      setReturns(retRes);

      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const rawRev = (revRes as any)?.data ?? revRes;
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const rawCod = (rawRev?.cod_collections ?? rawRev?.codCollections ?? []).map((item: any) => ({
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
      setCodList(rawCod);
      setCodDrivers(drivers);
    } catch (err) {
      setError(err instanceof Error ? err.message : t("Không thể tải dữ liệu điều hành"));
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => {
    let active = true;
    Promise.all([
      operationsRepository.getIncidents(),
      operationsRepository.getReturns(),
      operationsRepository.getReviewQueue(),
      driverRepository.getDrivers().catch(() => []),
    ]).then(([incRes, retRes, revRes, drivers]) => {
      if (!active) return;
      setIncidents(incRes);
      setReturns(retRes);
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const rawRev = (revRes as any)?.data ?? revRes;
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const rawCod = (rawRev?.cod_collections ?? rawRev?.codCollections ?? []).map((item: any) => ({
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
      setCodList(rawCod);
      setCodDrivers(drivers);
    }).catch((err) => {
      if (active) setError(err instanceof Error ? err.message : t("Không thể tải dữ liệu điều hành"));
    }).finally(() => {
      if (active) setLoading(false);
    });
    return () => { active = false; };
  }, [t]);

  // Handle Incident Resolution
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
      setError(err instanceof Error ? err.message : t("Lỗi khi xử lý sự cố"));
    } finally {
      setActionLoading(false);
    }
  };

  // Handle Return Approval
  const handleApproveReturn = async (returnId: string) => {
    setActionLoading(true);
    setError(null);
    try {
      await operationsRepository.approveReturn(returnId);
      setSelectedReturn(null);
      await loadData();
    } catch (err) {
      setError(err instanceof Error ? err.message : t("Lỗi khi duyệt hoàn hàng"));
    } finally {
      setActionLoading(false);
    }
  };

  const openReturnDetail = (ret: ReturnRequestDto) => {
    setSelectedReturn(ret);
    setReceiptNotes('');
    setReceiptFormError(null);
  };

  // Handle Warehouse Confirm Receipt (and Discrepancy Resolution)
  const handleConfirmReceipt = async (returnId: string, notes?: string, requireNotes = false) => {
    const trimmedNotes = notes?.trim() || '';
    if (requireNotes && !trimmedNotes) {
      setReceiptFormError(t("Vui lòng nhập kết luận xử lý chênh lệch trước khi chốt nhập kho."));
      return;
    }

    setActionLoading(true);
    setError(null);
    setReceiptFormError(null);
    try {
      await operationsRepository.confirmReturnReceipt(returnId, trimmedNotes || undefined);
      setSelectedReturn(null);
      setReceiptNotes('');
      await loadData();
    } catch (err) {
      setError(err instanceof Error ? err.message : t("Lỗi khi xác nhận nhận hàng"));
    } finally {
      setActionLoading(false);
    }
  };

  // Handle Submit Discrepancy
  const handleSubmitDiscrepancy = async () => {
    if (!selectedReturn) return;
    setActionLoading(true);
    setError(null);
    try {
      let evidenceUrl: string | undefined;
      if (discrepancyFile) {
        const uploadRes = await operationsRepository.uploadImage(discrepancyFile, 'returns/discrepancies');
        evidenceUrl = uploadRes.url;
      }

      const params: ReportDiscrepancyParams = {
        discrepancy_type: discrepancyType,
        discrepancy_notes: discrepancyNotes.trim() || undefined,
        evidence_url: evidenceUrl,
      };

      await operationsRepository.reportDiscrepancy(selectedReturn.id, params);
      setDiscrepancyModalOpen(false);
      setSelectedReturn(null);
      setDiscrepancyFile(null);
      setDiscrepancyNotes('');
      await loadData();
    } catch (err) {
      setError(err instanceof Error ? err.message : t("Lỗi khi báo cáo chênh lệch"));
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
      setCodFormError(t("Nhập số tiền thu ngân thực tế đã kiểm đếm."));
      return;
    }
    const hasVariance = countedTotal !== selectedCodReceipt.ledger_total;
    if (hasVariance && codSettlementNotes.trim() === '') {
      setCodFormError(t("Ghi rõ lý do và quyết định xử lý khi số tiền có chênh lệch."));
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
      setCodFormError(err instanceof Error ? err.message : t("Không quyết toán được phiếu nộp COD."));
    } finally {
      setActionLoading(false);
    }
  };

  // Filtered lists
  const filteredIncidents = incidents.filter((i) => {
    if (incidentStatusFilter !== 'ALL' && i.status !== incidentStatusFilter) return false;
    if (incidentSeverityFilter !== 'ALL' && i.severity !== incidentSeverityFilter) return false;
    return true;
  });

  const parsedCodCount = codCountedTotal.trim() === '' ? null : Number(codCountedTotal);
  const codDifference = selectedCodReceipt && parsedCodCount !== null && Number.isFinite(parsedCodCount)
    ? parsedCodCount - selectedCodReceipt.ledger_total
    : null;
  const hasCodVariance = codDifference !== null && codDifference !== 0;

  const filteredReturns = returns.filter((r) => {
    if (returnStatusFilter === 'ACTIVE') return r.status !== 'COMPLETED' && r.status !== 'CANCELLED';
    if (returnStatusFilter !== 'ALL' && r.status !== returnStatusFilter) return false;
    return true;
  });

  const openIncidentsCount = incidents.filter((i) => i.status === 'OPEN' || i.status === 'IN_PROGRESS').length;
  const pendingReturnsCount = returns.filter((r) => r.status === 'REQUESTED' || r.status === 'AWAITING_RECEIPT').length;

  return (
    <div className="mx-auto max-w-7xl space-y-6 pb-12">
      {/* Page Header */}
      <div className="flex flex-col gap-4 border-b border-slate-200 pb-5 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-950">
            {t("Trung tâm Điều hành & Xử lý Ngoại lệ")}
          </h1>
          <p className="mt-1 text-sm text-slate-600">
            {t("Giám sát sự cố phát sinh trong chuyến giao, quản lý quy trình hoàn hàng về kho (RTS) và đối soát COD.")}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={() => void loadData()}
            isLoading={loading}
            className="flex items-center gap-1.5"
          >
            <RefreshCw size={15} />
            {t("Làm mới")}
          </Button>
        </div>
      </div>

      <OperationsWeatherRisks />
      {error && (
        <div role="alert" className="flex items-center gap-3 rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800">
          <AlertOctagon size={18} className="shrink-0 text-rose-600" />
          <span>{error}</span>
        </div>
      )}

      {/* Top Metric Cards */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
          <div className="flex items-center justify-between">
            <span className="text-sm font-medium text-slate-500">{t("Sự cố cần giải quyết")}</span>
            <span className="rounded-lg bg-rose-50 p-2 text-rose-600">
              <AlertTriangle size={18} />
            </span>
          </div>
          <div className="mt-2 flex items-baseline gap-2">
            <span className="text-2xl font-bold tracking-tight text-slate-900">{openIncidentsCount}</span>
            <span className="text-xs text-slate-500">{t("trên")}{" "}{incidents.length}{" "}{t("sự cố")}</span>
          </div>
        </div>

        <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
          <div className="flex items-center justify-between">
            <span className="text-sm font-medium text-slate-500">{t("Hoàn hàng chờ duyệt")}</span>
            <span className="rounded-lg bg-amber-50 p-2 text-amber-600">
              <RotateCcw size={18} />
            </span>
          </div>
          <div className="mt-2 flex items-baseline gap-2">
            <span className="text-2xl font-bold tracking-tight text-slate-900">
              {returns.filter((r) => r.status === 'REQUESTED').length}
            </span>
            <span className="text-xs text-slate-500">{t("yêu cầu mới")}</span>
          </div>
        </div>

        <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
          <div className="flex items-center justify-between">
            <span className="text-sm font-medium text-slate-500">{t("Kho chờ kiểm nhận")}</span>
            <span className="rounded-lg bg-indigo-50 p-2 text-indigo-600">
              <Package size={18} />
            </span>
          </div>
          <div className="mt-2 flex items-baseline gap-2">
            <span className="text-2xl font-bold tracking-tight text-slate-900">
              {returns.filter((r) => r.status === 'AWAITING_RECEIPT' || r.status === 'ARRIVED').length}
            </span>
            <span className="text-xs text-slate-500">{t("đang đợi thủ kho")}</span>
          </div>
        </div>

        <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
          <div className="flex items-center justify-between">
            <span className="text-sm font-medium text-slate-500">{t("COD chờ xác minh")}</span>
            <span className="rounded-lg bg-emerald-50 p-2 text-emerald-600">
              <Banknote size={18} />
            </span>
          </div>
          <div className="mt-2 flex items-baseline gap-2">
            <span className="text-2xl font-bold tracking-tight text-slate-900">{codList.length}</span>
            <span className="text-xs text-slate-500">{t("giao dịch nộp tiền")}</span>
          </div>
        </div>
      </div>

      {/* Main Tabs Navigation */}
      <div className="border-b border-slate-200">
        <nav className="-mb-px flex gap-6 overflow-x-auto" aria-label={t("Tabs")}>
          <button
            onClick={() => setActiveTab('incidents')}
            className={`flex items-center gap-2 border-b-2 py-3 text-sm font-semibold transition-colors ${
              activeTab === 'incidents'
                ? 'border-rose-600 text-rose-600'
                : 'border-transparent text-slate-500 hover:border-slate-300 hover:text-slate-700'
            }`}
          >
            <AlertTriangle size={17} />
            <span>{t("Trung tâm Sự cố (Exception Center)")}</span>
            {openIncidentsCount > 0 && (
              <span className="rounded-full bg-rose-100 px-2 py-0.5 text-xs font-bold text-rose-700">
                {openIncidentsCount}
              </span>
            )}
          </button>

          <button
            onClick={() => setActiveTab('returns')}
            className={`flex items-center gap-2 border-b-2 py-3 text-sm font-semibold transition-colors ${
              activeTab === 'returns'
                ? 'border-indigo-600 text-indigo-600'
                : 'border-transparent text-slate-500 hover:border-slate-300 hover:text-slate-700'
            }`}
          >
            <RotateCcw size={17} />
            <span>{t("Quản lý Hoàn hàng (RTS Management)")}</span>
            {pendingReturnsCount > 0 && (
              <span className="rounded-full bg-indigo-100 px-2 py-0.5 text-xs font-bold text-indigo-700">
                {pendingReturnsCount}
              </span>
            )}
          </button>

          <button
            onClick={() => setActiveTab('cod')}
            className={`flex items-center gap-2 border-b-2 py-3 text-sm font-semibold transition-colors ${
              activeTab === 'cod'
                ? 'border-emerald-600 text-emerald-600'
                : 'border-transparent text-slate-500 hover:border-slate-300 hover:text-slate-700'
            }`}
          >
            <Banknote size={17} />
            <span>{t("Đối soát COD")}</span>
            {codList.length > 0 && (
              <span className="rounded-full bg-emerald-100 px-2 py-0.5 text-xs font-bold text-emerald-700">
                {codList.length}
              </span>
            )}
          </button>
          <button type="button" onClick={() => setActiveTab('handovers')}
            className={`shrink-0 border-b-2 px-1 py-4 text-sm font-medium ${activeTab === 'handovers' ? 'border-blue-600 text-blue-700' : 'border-transparent text-slate-600'}`}>
            {t("Bàn giao đơn")}
          </button>
        </nav>
      </div>

      {/* ========================================================================= */}
      {/* TAB 1: EXCEPTION CENTER (INCIDENTS) */}
      {/* ========================================================================= */}
      {activeTab === 'handovers' && <OrderHandoverQueue />}

      {activeTab === 'incidents' && (
        <div className="space-y-4">
          {/* Filters Bar */}
          <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-slate-200 bg-white p-3">
            <div className="flex flex-wrap items-center gap-3">
              <div className="flex items-center gap-1.5 text-xs font-medium text-slate-500">
                <span>{t("Trạng thái:")}</span>
                <CustomSelect
                  size="sm"
                  aria-label={t("Trạng thái:")}
                  value={incidentStatusFilter}
                  onValueChange={setIncidentStatusFilter}
                  className="w-44"
                  options={[
                    { value: 'ALL', label: t("Tất cả trạng thái") },
                    { value: 'OPEN', label: t("Đang chờ xử lý") },
                    { value: 'IN_PROGRESS', label: t("Đang xử lý") },
                    { value: 'ESCALATED', label: t("Đã leo thang") },
                    { value: 'RESOLVED', label: t("Đã giải quyết") },
                  ]}
                />
              </div>

              <div className="flex items-center gap-1.5 text-xs font-medium text-slate-500">
                <span>{t("Mức độ:")}</span>
                <CustomSelect
                  size="sm"
                  aria-label={t("Mức độ:")}
                  value={incidentSeverityFilter}
                  onValueChange={setIncidentSeverityFilter}
                  className="w-36"
                  options={[
                    { value: 'ALL', label: t("Tất cả mức độ") },
                    { value: 'CRITICAL', label: t("Khẩn cấp") },
                    { value: 'HIGH', label: t("Cao") },
                    { value: 'MEDIUM', label: t("Trung bình") },
                    { value: 'LOW', label: t("Thấp") },
                  ]}
                />
              </div>
            </div>

            <div className="text-xs text-slate-500">
              {t("Hiển thị")}{" "}<span className="font-semibold text-slate-800">{filteredIncidents.length}</span>{" "}{t("sự cố")}
            </div>
          </div>

          {/* Incidents Table */}
          <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm text-slate-600">
                <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wider text-slate-500">
                  <tr>
                    <th className="px-5 py-3 font-semibold">{t("Mã đơn / Tuyến")}</th>
                    <th className="px-5 py-3 font-semibold">{t("Loại sự cố")}</th>
                    <th className="px-5 py-3 font-semibold">{t("Mức độ")}</th>
                    <th className="px-5 py-3 font-semibold">{t("Thời gian")}</th>
                    <th className="px-5 py-3 font-semibold">{t("Trạng thái")}</th>
                    <th className="px-5 py-3 text-right font-semibold">{t("Thao tác")}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {filteredIncidents.map((inc) => (
                    <tr key={inc.id} className="hover:bg-slate-50/75 transition-colors">
                      <td className="px-5 py-3.5">
                        <div className="font-semibold text-slate-900">
                          {inc.order_code || (inc.order_id ? `#${inc.order_id.slice(0, 8)}` : t("Sự cố tuyến đường"))}
                        </div>
                        {inc.dispatch_plan_id && (
                          <div className="text-xs text-slate-500">
                            {t("Tuyến:")}{" "}{inc.dispatch_plan_id.slice(0, 8)}
                          </div>
                        )}
                      </td>
                      <td className="px-5 py-3.5">
                        <div className="font-medium text-slate-800">{formatIncidentType(inc.incident_type, t)}</div>
                        {inc.notes && (
                          <div className="max-w-xs truncate text-xs text-slate-500" title={inc.notes}>
                            {inc.notes}
                          </div>
                        )}
                      </td>
                      <td className="px-5 py-3.5">{severityBadge(inc.severity, t)}</td>
                      <td className="px-5 py-3.5 text-xs text-slate-500">
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
                          className="text-xs"
                        >
                          <Eye size={14} className="mr-1" />
                          {t("Xem & Xử lý")}
                        </Button>
                      </td>
                    </tr>
                  ))}

                  {filteredIncidents.length === 0 && (
                    <tr>
                      <td colSpan={6} className="px-5 py-12 text-center text-sm text-slate-500">
                        {t("Không có sự cố nào phù hợp với bộ lọc.")}
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
      {/* TAB 2: RETURN-TO-SENDER (RTS) MANAGEMENT */}
      {/* ========================================================================= */}
      {activeTab === 'returns' && (
        <div className="space-y-4">
          {/* Filters Bar */}
          <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-slate-200 bg-white p-3">
            <div className="flex flex-wrap items-center gap-3">
              <div className="flex items-center gap-1.5 text-xs font-medium text-slate-500">
                <span>{t("Trạng thái hoàn:")}</span>
                <CustomSelect
                  size="sm"
                  aria-label={t("Trạng thái hoàn:")}
                  value={returnStatusFilter}
                  onValueChange={setReturnStatusFilter}
                  className="w-48"
                  options={[
                    { value: 'ALL', label: t("Tất cả trạng thái") },
                    { value: 'ACTIVE', label: t("Đang xử lý") },
                    { value: 'REQUESTED', label: t("Chờ duyệt") },
                    { value: 'IN_EXECUTION', label: t("Đang hoàn về") },
                    { value: 'ARRIVED', label: t("Đã đến điểm hoàn") },
                    { value: 'AWAITING_RECEIPT', label: t("Chờ kiểm nhận") },
                    { value: 'DISCREPANCY', label: t("Có chênh lệch / Hỏng") },
                    { value: 'EXECUTION_FAILED', label: t("Hoàn hàng thất bại") },
                    { value: 'COMPLETED', label: t("Đã nhận hàng vào kho") },
                  ]}
                />
              </div>
            </div>

            <div className="text-xs text-slate-500">
              {t("Hiển thị")}{" "}<span className="font-semibold text-slate-800">{filteredReturns.length}</span>{" "}{t("yêu cầu")}
            </div>
          </div>

          {/* Returns Table */}
          <div className="overflow-hidden rounded-xl border border-slate-200 bg-white">
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm text-slate-600">
                <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wider text-slate-500">
                  <tr>
                    <th className="px-5 py-3 font-semibold">{t("Mã đơn")}</th>
                    <th className="px-5 py-3 font-semibold">{t("Lý do hoàn")}</th>
                    <th className="px-5 py-3 font-semibold">{t("Điểm hoàn về")}</th>
                    <th className="px-5 py-3 font-semibold">{t("Lưu ký / Tiếp nhận")}</th>
                    <th className="px-5 py-3 font-semibold">{t("Trạng thái")}</th>
                    <th className="px-5 py-3 text-right font-semibold">{t("Thao tác")}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {filteredReturns.map((ret) => (
                    <tr key={ret.id} className="transition-colors duration-150 hover:bg-slate-50/75">
                      <td className="px-5 py-3.5">
                        <div className="font-mono text-sm font-semibold text-slate-900">
                          {ret.order_code || `#${ret.order_id.slice(0, 8)}`}
                        </div>
                        <div className="mt-0.5 text-xs tabular-nums text-slate-500">
                          {formatDate(ret.created_at, intlLocale)}
                        </div>
                      </td>
                      <td className="px-5 py-3.5">
                        <div className="max-w-xs truncate text-sm font-medium text-slate-800" title={ret.reason}>
                          {ret.reason}
                        </div>
                        {ret.discrepancy_type && (
                          <div className="mt-1 inline-flex items-center gap-1 rounded-md border border-rose-200 bg-rose-50 px-2 py-0.5 text-[11px] font-medium text-rose-700">
                            <AlertTriangle size={12} aria-hidden="true" className="shrink-0" />
                            <span>{formatDiscrepancyType(ret.discrepancy_type, t)}</span>
                          </div>
                        )}
                      </td>
                      <td className="px-5 py-3.5">
                        <div className="font-medium text-slate-800">{ret.return_location_name || t("Kho lấy ban đầu")}</div>
                        {ret.return_address && (
                          <div className="max-w-xs truncate text-xs text-slate-500" title={ret.return_address}>
                            {ret.return_address}
                          </div>
                        )}
                      </td>
                      <td className="px-5 py-3.5">
                        {ret.status === 'COMPLETED' ? (
                          <div className="space-y-0.5">
                            <span className="inline-flex items-center gap-1.5 text-xs font-medium text-emerald-700">
                              <ClipboardCheck size={13} aria-hidden="true" className="shrink-0" />
                              <span>
                                {resolveReporterDisplayName(ret.received_by, personDirectory, t, "Đã nhập kho")}
                              </span>
                            </span>
                            {ret.received_at && (
                              <div className="text-[11px] tabular-nums text-slate-500">
                                {formatDate(ret.received_at, intlLocale)}
                              </div>
                            )}
                          </div>
                        ) : ret.current_holder_id ? (
                          <span className="inline-flex items-center gap-1.5 text-xs font-medium text-slate-700">
                            <UserCheck size={13} aria-hidden="true" className="shrink-0 text-[#17766c]" />
                            <span>{resolveDriverDisplayName(ret.current_holder_id, personDirectory, t)}</span>
                          </span>
                        ) : (
                          <span className="text-xs text-slate-400">{t("Chờ gán tài xế hoàn")}</span>
                        )}
                      </td>
                      <td className="px-5 py-3.5">{returnStatusBadge(ret.status, t)}</td>
                      <td className="px-5 py-3.5 text-right">
                        <div className="flex items-center justify-end gap-1.5">
                          {ret.status === 'REQUESTED' && (
                            <Button
                              size="sm"
                              onClick={() => void handleApproveReturn(ret.id)}
                              isLoading={actionLoading}
                              className="cursor-pointer bg-[#17766c] text-xs text-white hover:bg-[#136058]"
                            >
                              <CheckCircle2 size={13} aria-hidden="true" className="mr-1" />
                              {t("Duyệt hoàn")}
                            </Button>
                          )}

                          {(ret.status === 'IN_EXECUTION' || ret.status === 'AWAITING_RECEIPT' || ret.status === 'ARRIVED' || ret.status === 'EXECUTION_FAILED') && (
                            <>
                              <Button
                                size="sm"
                                onClick={() => openReturnDetail(ret)}
                                className="cursor-pointer bg-emerald-600 text-xs text-white hover:bg-emerald-700"
                              >
                                <ClipboardCheck size={13} aria-hidden="true" className="mr-1" />
                                {t("Kiểm nhận")}
                              </Button>
                              <Button
                                size="sm"
                                variant="outline"
                                onClick={() => {
                                  openReturnDetail(ret);
                                  setDiscrepancyModalOpen(true);
                                }}
                                className="cursor-pointer border-rose-300 text-xs text-rose-700 hover:bg-rose-50"
                              >
                                <ShieldAlert size={13} aria-hidden="true" className="mr-1" />
                                {t("Báo hỏng/lệch")}
                              </Button>
                            </>
                          )}

                          {ret.status === 'DISCREPANCY' && (
                            <Button
                              size="sm"
                              onClick={() => openReturnDetail(ret)}
                              className="cursor-pointer bg-amber-600 text-xs text-white hover:bg-amber-700"
                            >
                              <ClipboardCheck size={13} aria-hidden="true" className="mr-1" />
                              {t("Chốt xử lý")}
                            </Button>
                          )}

                          <Button
                            size="sm"
                            variant="outline"
                            onClick={() => openReturnDetail(ret)}
                            className="cursor-pointer text-xs"
                          >
                            <Eye size={14} aria-hidden="true" className="mr-1" />
                            {t("Chi tiết")}
                          </Button>
                        </div>
                      </td>
                    </tr>
                  ))}

                  {filteredReturns.length === 0 && (
                    <tr>
                      <td colSpan={6} className="px-5 py-12 text-center text-sm text-slate-500">
                        {t("Không có yêu cầu hoàn hàng nào.")}
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
            <h2 className="text-base font-semibold text-slate-900">{t("Phiếu nộp COD chờ kiểm đếm")}</h2>
            <p className="mt-1 text-sm text-slate-500">
              {t("Mỗi phiếu gồm các đơn tài xế nộp cùng một lần. Thu ngân nhập số thực đếm để quyết toán.")}
            </p>

            <div className="mt-4 divide-y divide-slate-100">
              {codList.map((item) => (
                <div key={item.id} className="flex flex-col gap-3 py-4 sm:flex-row sm:items-center sm:justify-between">
                  <div>
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="font-semibold text-slate-900">
                        {t("Phiếu #")}{item.id.slice(0, 8)}
                      </span>
                      <Badge className="border border-slate-200 bg-slate-50 text-slate-700">
                        {item.order_count}{" "}{t("đơn")}
                      </Badge>
                    </div>
                    <div className="mt-1 text-sm text-slate-700">
                      {t("Theo hệ thống:")}{" "}<span className="font-semibold tabular-nums">{formatCurrency(item.ledger_total)}</span>
                      {' · '}{t("Tài xế khai nộp:")}{" "}<span className="font-semibold tabular-nums">{formatCurrency(item.declared_amount)}</span>
                    </div>
                    <div className="mt-1 text-xs text-slate-500">
                      {t("Tài xế:")}{" "}{resolveDriverDisplayName(item.driver_id, personDirectory, t, "Chưa có tên")}{" "}{t("· Gửi lúc:")}{" "}{formatDate(item.declared_at, intlLocale)}
                    </div>
                    {item.order_codes.length > 0 && (
                      <div className="mt-1 max-w-3xl truncate text-xs text-slate-500">
                        {t("Mã đơn:")}{" "}{item.order_codes.join(', ')}
                      </div>
                    )}
                  </div>

                  <Button
                    size="sm"
                    onClick={() => openCodSettlement(item)}
                    isLoading={actionLoading}
                    className="shrink-0 bg-slate-900 text-white hover:bg-slate-800"
                  >
                    <Banknote size={15} className="mr-1" />
                    {t("Nhập tiền thực đếm")}
                  </Button>
                </div>
              ))}

              {codList.length === 0 && (
                <p className="py-8 text-center text-sm text-slate-500">
                  {t("Không có phiếu nộp COD nào đang chờ kiểm đếm.")}
                </p>
              )}
            </div>
          </div>
        </div>
      )}

      <Modal
        open={selectedCodReceipt !== null}
        onOpenChange={(open) => {
          if (!open) {
            setSelectedCodReceipt(null);
            setCodFormError(null);
          }
        }}
        title={t("Kiểm đếm phiếu nộp COD")}
        description={t("Nhập số tiền thực tế tại quầy. Nếu có chênh lệch, quyết toán ngay cùng lý do xử lý.")}
        className="max-w-xl"
      >
        {selectedCodReceipt && (
          <div className="space-y-5 text-sm">
            <div className="grid gap-3 rounded-lg bg-slate-50 p-4 sm:grid-cols-2">
              <div>
                <p className="text-xs text-slate-500">{t("Theo hệ thống")}</p>
                <p className="mt-1 font-semibold tabular-nums text-slate-900">
                  {formatCurrency(selectedCodReceipt.ledger_total)}
                </p>
              </div>
              <div>
                <p className="text-xs text-slate-500">{t("Tài xế khai nộp")}</p>
                <p className="mt-1 font-semibold tabular-nums text-slate-900">
                  {formatCurrency(selectedCodReceipt.declared_amount)}
                </p>
              </div>
            </div>

            <div>
              <label htmlFor="cod-counted-total" className="mb-2 block font-medium text-slate-800">
                {t("Số tiền thu ngân thực đếm (VND)")}
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
                placeholder={t("Nhập số tiền đã đếm")}
                className="h-11 w-full rounded-lg border border-slate-300 bg-white px-3 text-base tabular-nums text-slate-900 outline-none focus:border-slate-700 focus:ring-2 focus:ring-slate-200"
              />
            </div>

            {codDifference !== null && (
              <div className={`rounded-lg px-4 py-3 ${hasCodVariance ? 'bg-amber-50 text-amber-900' : 'bg-emerald-50 text-emerald-900'}`}>
                {hasCodVariance
                  ? `${t("Chênh lệch")} ${codDifference > 0 ? t("thừa") : t("thiếu")} ${formatCurrency(Math.abs(codDifference))}.`
                  : t("Số tiền thực đếm khớp với hệ thống.")}
              </div>
            )}

            {hasCodVariance && (
              <>
                <div>
                  <label htmlFor="cod-variance-resolution" className="mb-2 block font-medium text-slate-800">
                    {t("Cách ghi nhận chênh lệch")}
                  </label>
                  <CustomSelect
                    id="cod-variance-resolution"
                    value={codVarianceResolution}
                    onValueChange={(next) => setCodVarianceResolution(next as CodVarianceResolution)}
                    options={[
                      { value: 'BUSINESS_ADJUSTMENT', label: t("Doanh nghiệp chấp nhận điều chỉnh") },
                      { value: 'DRIVER_RESPONSIBLE', label: t("Ghi nhận tài xế chịu trách nhiệm") },
                      { value: 'OTHER', label: t("Xử lý theo quyết định khác") },
                    ]}
                  />
                </div>
                <div>
                  <label htmlFor="cod-settlement-notes" className="mb-2 block font-medium text-slate-800">
                    {t("Lý do và quyết định xử lý")}
                  </label>
                  <textarea
                    id="cod-settlement-notes"
                    rows={3}
                    value={codSettlementNotes}
                    onChange={(event) => {
                      setCodSettlementNotes(event.target.value);
                      setCodFormError(null);
                    }}
                    placeholder={t("Ví dụ: doanh nghiệp chấp nhận thiếu 20.000₫ do sai lệch khi đối soát.")}
                    className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-slate-900 outline-none focus:border-slate-700 focus:ring-2 focus:ring-slate-200"
                  />
                </div>
              </>
            )}

            {codFormError && (
              <p role="alert" className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-800">
                {codFormError}
              </p>
            )}

            <div className="flex flex-col-reverse gap-2 border-t border-slate-200 pt-4 sm:flex-row sm:justify-end">
              <Button variant="outline" onClick={() => setSelectedCodReceipt(null)} disabled={actionLoading}>
                {t("Đóng")}
              </Button>
              <Button
                onClick={() => void handleSettleCod()}
                isLoading={actionLoading}
                className="bg-slate-900 text-white hover:bg-slate-800"
              >
                {hasCodVariance ? t("Quyết toán có chênh lệch") : t("Xác nhận đã nhận đủ")}
              </Button>
            </div>
          </div>
        )}
      </Modal>

      {/* ========================================================================= */}
      {/* MODAL 1: INCIDENT DETAIL & RESOLUTION WORKFLOW */}
      {/* ========================================================================= */}
      <Modal
        open={selectedIncident !== null}
        onOpenChange={(open) => {
          if (!open) {
            setSelectedIncident(null);
            setResolvingAction(null);
          }
        }}
        title={`${t("Chi tiết sự cố:")} ${selectedIncident?.order_code || (selectedIncident?.order_id ? `#${selectedIncident.order_id.slice(0, 8)}` : t("Tuyến đường"))}`}
        description={t("Thông tin sự cố do tài xế hoặc hệ thống báo cáo và các hành động xử lý chuẩn SaaS.")}
        className="max-w-2xl"
      >
        {selectedIncident && (
          <div className="space-y-5">
            {/* Incident Summary Card */}
            <div className="rounded-lg border border-slate-200 bg-slate-50 p-4 space-y-2.5 text-sm">
              <div className="flex items-center justify-between">
                <span className="font-semibold text-slate-900">{formatIncidentType(selectedIncident.incident_type, t)}</span>
                <div className="flex gap-2">
                  {severityBadge(selectedIncident.severity, t)}
                  {incidentStatusBadge(selectedIncident.status, t)}
                </div>
              </div>

              {selectedIncident.notes && (
                <p className="text-slate-700 bg-white p-2.5 rounded border border-slate-200 text-xs">
                  <span className="font-semibold">{t("Mô tả:")}</span> {selectedIncident.notes}
                </p>
              )}

              <div className="grid grid-cols-2 gap-2 text-xs text-slate-600 pt-1">
                <div>
                  <span className="text-slate-400">{t("Người báo cáo:")}</span>{' '}
                  {resolveReporterDisplayName(selectedIncident.reported_by, personDirectory, t)}
                </div>
                <div>
                  <span className="text-slate-400">{t("Thời gian:")}</span> {formatDate(selectedIncident.created_at, intlLocale)}
                </div>
                <div>
                  <span className="text-slate-400">{t("Tài xế phụ trách:")}</span>{' '}
                  {resolveDriverDisplayName(selectedIncident.driver_id, personDirectory, t, "Chưa gán")}
                </div>
              </div>

              {/* Supporting Evidence Images (Cloudinary) */}
              {selectedIncident.evidence_urls && selectedIncident.evidence_urls.length > 0 && (
                <div className="pt-2">
                  <span className="text-xs font-semibold text-slate-700 block mb-1.5">{t("Ảnh bằng chứng hiện trường:")}</span>
                  <div className="flex flex-wrap gap-2">
                    {selectedIncident.evidence_urls.map((url, idx) => (
                      <a
                        key={idx}
                        href={url}
                        target="_blank"
                        rel="noreferrer"
                        className="group relative block h-20 w-20 overflow-hidden rounded-lg border border-slate-200 bg-slate-100"
                      >
                        {/* eslint-disable-next-line @next/next/no-img-element */}
                        <img
                          src={url}
                          alt={t("Bằng chứng sự cố")}
                          className="h-full w-full object-cover transition-transform group-hover:scale-105"
                        />
                      </a>
                    ))}
                  </div>
                </div>
              )}
            </div>

            {/* Resolution Actions Section */}
            {selectedIncident.status !== 'RESOLVED' && (
              <div className="space-y-3 border-t border-slate-200 pt-4">
                <h3 className="text-sm font-semibold text-slate-900">{t("Chọn giải pháp xử lý nghiệp vụ:")}</h3>

                <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
                  <Button
                    size="sm"
                    variant={resolvingAction === 'CONTINUE_DELIVERY' ? 'default' : 'outline'}
                    onClick={() => setResolvingAction('CONTINUE_DELIVERY')}
                    className="text-xs justify-start h-auto py-2"
                  >
                    <CheckCircle2 size={14} className="mr-1.5 text-emerald-600 shrink-0" />
                    <div>
                      <div className="font-semibold text-left">{t("Tiếp tục giao")}</div>
                      <div className="text-[10px] text-slate-500 font-normal">{t("Khách gọi lại / đã xử lý")}</div>
                    </div>
                  </Button>

                  <Button
                    size="sm"
                    variant={resolvingAction === 'RETRY_DELIVERY' ? 'default' : 'outline'}
                    onClick={() => setResolvingAction('RETRY_DELIVERY')}
                    className="text-xs justify-start h-auto py-2"
                  >
                    <Clock size={14} className="mr-1.5 text-blue-600 shrink-0" />
                    <div>
                      <div className="font-semibold text-left">{t("Giao lại ca sau")}</div>
                      <div className="text-[10px] text-slate-500 font-normal">{t("Hẹn lại giờ giao")}</div>
                    </div>
                  </Button>

                  <Button
                    size="sm"
                    variant={resolvingAction === 'REQUEST_RETURN' ? 'default' : 'outline'}
                    onClick={() => setResolvingAction('REQUEST_RETURN')}
                    className="text-xs justify-start h-auto py-2"
                  >
                    <RotateCcw size={14} className="mr-1.5 text-indigo-600 shrink-0" />
                    <div>
                      <div className="font-semibold text-left">{t("Yêu cầu hoàn hàng")}</div>
                      <div className="text-[10px] text-slate-500 font-normal">{t("Khách từ chối / hủy đơn")}</div>
                    </div>
                  </Button>

                  <Button
                    size="sm"
                    variant={resolvingAction === 'ESCALATED' ? 'default' : 'outline'}
                    onClick={() => setResolvingAction('ESCALATED')}
                    className="text-xs justify-start h-auto py-2"
                  >
                    <ShieldAlert size={14} className="mr-1.5 text-purple-600 shrink-0" />
                    <div>
                      <div className="font-semibold text-left">{t("Leo thang")}</div>
                      <div className="text-[10px] text-slate-500 font-normal">{t("Mất hàng / hỏng nặng")}</div>
                    </div>
                  </Button>

                  <Button
                    size="sm"
                    variant={resolvingAction === 'RESOLVED_NO_ACTION' ? 'default' : 'outline'}
                    onClick={() => setResolvingAction('RESOLVED_NO_ACTION')}
                    className="text-xs justify-start h-auto py-2"
                  >
                    <CheckCircle2 size={14} className="mr-1.5 text-slate-600 shrink-0" />
                    <div>
                      <div className="font-semibold text-left">{t("Đóng sự cố")}</div>
                      <div className="text-[10px] text-slate-500 font-normal">{t("Không cần thao tác thêm")}</div>
                    </div>
                  </Button>
                </div>

                {resolvingAction && (
                  <div className="mt-3 space-y-2 rounded-lg border border-indigo-100 bg-indigo-50/50 p-3">
                    <label className="text-xs font-semibold text-slate-800">
                      {t("Ghi chú quyết định xử lý (Bắt buộc):")}
                    </label>
                    <textarea
                      value={resolutionNotes}
                      onChange={(e) => setResolutionNotes(e.target.value)}
                      rows={2}
                      placeholder={t("Nhập lý do hoặc chi tiết điều phối...")}
                      className="w-full rounded-md border border-slate-300 p-2 text-xs text-slate-900 focus:outline-none focus:ring-1 focus:ring-indigo-500"
                    />
                    <div className="flex justify-end gap-2 pt-1">
                      <Button size="sm" variant="outline" onClick={() => setResolvingAction(null)}>
                        {t("Hủy")}
                      </Button>
                      <Button
                        size="sm"
                        onClick={() => void handleResolveIncident(resolvingAction)}
                        isLoading={actionLoading}
                        className="bg-indigo-600 text-white hover:bg-indigo-700"
                      >
                        {t("Xác nhận xử lý")}
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
      {/* MODAL 2: RETURN REQUEST DETAIL & RECEIVING CONFIRMATION */}
      {/* ========================================================================= */}
      <Modal
        open={selectedReturn !== null && !discrepancyModalOpen}
        onOpenChange={(open) => {
          if (!open) {
            setSelectedReturn(null);
            setReceiptNotes('');
            setReceiptFormError(null);
          }
        }}
        title={`${t("Yêu cầu hoàn hàng:")} ${selectedReturn?.order_code || (selectedReturn?.order_id ? `#${selectedReturn.order_id.slice(0, 8)}` : '')}`}
        description={t("Theo dõi lộ trình hoàn về kho, xác nhận lưu ký và kiểm nhận hàng hóa.")}
        className="max-h-[calc(100dvh-2rem)] max-w-2xl overflow-y-auto"
      >
        {selectedReturn && (() => {
          const canReceiveNormally =
            selectedReturn.status === 'IN_EXECUTION' ||
            selectedReturn.status === 'ARRIVED' ||
            selectedReturn.status === 'AWAITING_RECEIPT' ||
            selectedReturn.status === 'EXECUTION_FAILED';
          const isDiscrepancy = selectedReturn.status === 'DISCREPANCY';
          const isCompleted = selectedReturn.status === 'COMPLETED';
          const progressStep = isCompleted
            ? 3
            : isDiscrepancy || selectedReturn.status === 'ARRIVED' || selectedReturn.status === 'AWAITING_RECEIPT'
              ? 2
              : selectedReturn.status === 'IN_EXECUTION' || selectedReturn.status === 'EXECUTION_FAILED'
                ? 1
                : 0;
          const returnSteps = [
            t("Tạo yêu cầu"),
            t("Duyệt & vận chuyển"),
            isDiscrepancy ? t("Đối soát chênh lệch") : t("Kiểm nhận tại kho"),
            t("Đã nhập kho"),
          ];

          return (
            <div className="space-y-4 text-sm">
              {/* 4-step RTO progress rail */}
              <div className="rounded-xl border border-slate-200 bg-white px-4 py-3">
                <div className="grid grid-cols-4 gap-2">
                  {returnSteps.map((stepLabel, idx) => {
                    const isDone = idx < progressStep || (isCompleted && idx === 3);
                    const isCurrent = idx === progressStep && !isCompleted;
                    const isAlertStep = isDiscrepancy && idx === 2;
                    return (
                      <div key={stepLabel} className="flex flex-col items-center text-center">
                        <div className="relative flex w-full items-center justify-center">
                          {idx > 0 && (
                            <span
                              aria-hidden="true"
                              className={`absolute left-0 right-1/2 top-1/2 h-0.5 -translate-y-1/2 ${
                                idx <= progressStep
                                  ? isDiscrepancy && idx === 2
                                    ? 'bg-rose-400'
                                    : 'bg-[#17766c]'
                                  : 'bg-slate-200'
                              }`}
                            />
                          )}
                          {idx < returnSteps.length - 1 && (
                            <span
                              aria-hidden="true"
                              className={`absolute left-1/2 right-0 top-1/2 h-0.5 -translate-y-1/2 ${
                                idx < progressStep ? 'bg-[#17766c]' : 'bg-slate-200'
                              }`}
                            />
                          )}
                          <span
                            className={`relative z-10 flex h-6 w-6 items-center justify-center rounded-full border text-[11px] font-semibold tabular-nums transition-colors ${
                              isAlertStep
                                ? 'border-rose-600 bg-rose-600 text-white ring-4 ring-rose-100'
                                : isDone
                                  ? 'border-[#17766c] bg-[#17766c] text-white'
                                  : isCurrent
                                    ? 'border-[#17766c] bg-white text-[#17766c] ring-4 ring-[#17766c]/15'
                                    : 'border-slate-300 bg-white text-slate-400'
                            }`}
                          >
                            {isDone ? (
                              <Check size={12} aria-hidden="true" />
                            ) : isAlertStep ? (
                              <AlertTriangle size={12} aria-hidden="true" />
                            ) : (
                              idx + 1
                            )}
                          </span>
                        </div>
                        <span
                          className={`mt-1.5 text-[11px] font-medium leading-tight ${
                            isAlertStep
                              ? 'text-rose-700'
                              : isDone || isCurrent
                                ? 'text-slate-900'
                                : 'text-slate-400'
                          }`}
                        >
                          {stepLabel}
                        </span>
                      </div>
                    );
                  })}
                </div>
              </div>

              {/* Overview Card */}
              <div className="space-y-3 rounded-xl border border-slate-200 bg-slate-50/70 p-4">
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div className="min-w-0">
                    <div className="text-[11px] font-medium uppercase tracking-wide text-slate-500">
                      {t("Lý do yêu cầu hoàn")}
                    </div>
                    <p className="mt-0.5 font-semibold text-slate-900">{selectedReturn.reason}</p>
                  </div>
                  {returnStatusBadge(selectedReturn.status, t)}
                </div>

                <dl className="grid grid-cols-1 gap-x-4 gap-y-2.5 border-t border-slate-200/80 pt-3 text-xs sm:grid-cols-2">
                  <div>
                    <dt className="text-slate-500">{t("Kho hoàn về")}</dt>
                    <dd className="mt-0.5 font-medium text-slate-900">
                      {selectedReturn.return_location_name || t("Kho ban đầu")}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-slate-500">{t("Địa chỉ kho")}</dt>
                    <dd className="mt-0.5 font-medium text-slate-900">
                      {selectedReturn.return_address || t("Địa chỉ kho gửi")}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-slate-500">{t("Người đang cầm hàng")}</dt>
                    <dd className="mt-0.5 flex items-center gap-1.5 font-medium text-slate-900">
                      <span
                        aria-hidden="true"
                        className={`h-2 w-2 rounded-full ${
                          selectedReturn.current_holder_id ? 'bg-[#17766c]' : 'bg-slate-400'
                        }`}
                      />
                      <span>
                        {selectedReturn.current_holder_id
                          ? resolveDriverDisplayName(selectedReturn.current_holder_id, personDirectory, t)
                          : isCompleted
                            ? t("Đã nhập kho")
                            : t("Chưa gán tài xế")}
                      </span>
                    </dd>
                  </div>
                  <div>
                    <dt className="text-slate-500">{t("Người tạo yêu cầu")}</dt>
                    <dd className="mt-0.5 font-medium text-slate-900">
                      {resolveReporterDisplayName(selectedReturn.requested_by, personDirectory, t, "Chưa xác định")}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-slate-500">{t("Người duyệt hoàn")}</dt>
                    <dd className="mt-0.5 font-medium text-slate-900">
                      {selectedReturn.approved_by
                        ? resolveReporterDisplayName(selectedReturn.approved_by, personDirectory, t)
                        : t("Chưa duyệt")}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-slate-500">{t("Thời gian tạo")}</dt>
                    <dd className="mt-0.5 font-medium tabular-nums text-slate-900">
                      {formatDate(selectedReturn.created_at, intlLocale)}
                    </dd>
                  </div>
                </dl>
              </div>

              {/* Discrepancy Record Card (kept visible even after COMPLETED for full audit trail) */}
              {selectedReturn.discrepancy_type && (
                <div className="space-y-2.5 rounded-xl border border-rose-200 bg-rose-50/70 p-4 text-xs text-rose-900">
                  <div className="flex items-center gap-2 font-semibold text-rose-800">
                    <AlertTriangle size={15} aria-hidden="true" className="shrink-0 text-rose-600" />
                    <span>{t("Biên bản chênh lệch / hư hỏng khi hoàn")}</span>
                  </div>
                  <div className="grid gap-1.5">
                    <div>
                      <span className="text-rose-700/80">{t("Loại chênh lệch:")}</span>{' '}
                      <span className="font-semibold text-rose-950">
                        {formatDiscrepancyType(selectedReturn.discrepancy_type, t)}
                      </span>
                    </div>
                    {selectedReturn.discrepancy_notes && (
                      <div>
                        <span className="text-rose-700/80">{t("Mô tả sai lệch:")}</span>{' '}
                        <span className="text-rose-950">{selectedReturn.discrepancy_notes}</span>
                      </div>
                    )}
                  </div>
                  {selectedReturn.discrepancy_evidence_url && (
                    <div className="pt-1">
                      <a
                        href={selectedReturn.discrepancy_evidence_url}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="group inline-flex flex-col overflow-hidden rounded-lg border border-rose-200 bg-white transition-colors duration-150 hover:border-rose-400"
                      >
                        {/* eslint-disable-next-line @next/next/no-img-element */}
                        <img
                          src={selectedReturn.discrepancy_evidence_url}
                          alt={t("Ảnh chụp bằng chứng chênh lệch hàng hoàn")}
                          className="h-28 w-44 object-cover"
                        />
                        <span className="inline-flex items-center justify-center gap-1 border-t border-rose-100 px-2.5 py-1.5 text-[11px] font-semibold text-rose-700 group-hover:text-rose-900">
                          <span>{t("Xem ảnh chụp hàng hỏng")}</span>
                          <ExternalLink size={12} aria-hidden="true" />
                        </span>
                      </a>
                    </div>
                  )}
                </div>
              )}

              {/* Warehouse Receipt Audit Card (visible once received / completed) */}
              {(selectedReturn.received_at || selectedReturn.received_by || isCompleted) && (
                <div className="space-y-2.5 rounded-xl border border-emerald-200 bg-emerald-50/60 p-4 text-xs text-emerald-950">
                  <div className="flex items-center gap-2 font-semibold text-emerald-900">
                    <ClipboardCheck size={15} aria-hidden="true" className="shrink-0 text-emerald-700" />
                    <span>{t("Biên bản xác nhận nhận hàng hoàn")}</span>
                  </div>
                  <dl className="grid grid-cols-1 gap-2 sm:grid-cols-2">
                    <div>
                      <dt className="text-emerald-800/80">{t("Người xác nhận nhận kho")}</dt>
                      <dd className="mt-0.5 font-semibold text-emerald-950">
                        {resolveReporterDisplayName(selectedReturn.received_by, personDirectory, t, "Nhân viên kho")}
                      </dd>
                    </div>
                    <div>
                      <dt className="text-emerald-800/80">{t("Thời điểm nhập kho")}</dt>
                      <dd className="mt-0.5 font-semibold tabular-nums text-emerald-950">
                        {selectedReturn.received_at ? formatDate(selectedReturn.received_at, intlLocale) : '—'}
                      </dd>
                    </div>
                    <div className="sm:col-span-2">
                      <dt className="text-emerald-800/80">{t("Ghi chú kiểm nhận / Kết luận")}</dt>
                      <dd className="mt-0.5 text-emerald-950">
                        {selectedReturn.completion_notes || t("Hàng hoàn nguyên vẹn, đã nhập kho.")}
                      </dd>
                    </div>
                  </dl>
                </div>
              )}

              {/* Receiving / Discrepancy Settlement Input */}
              {(canReceiveNormally || isDiscrepancy) && (
                <div className="space-y-2 rounded-xl border border-slate-200 bg-white p-4">
                  <label
                    htmlFor="return-completion-notes"
                    className="block text-xs font-semibold text-slate-800"
                  >
                    {isDiscrepancy
                      ? t("Kết luận xử lý chênh lệch & nhập kho (bắt buộc)")
                      : t("Ghi chú kiểm nhận kho (tùy chọn)")}
                  </label>
                  <textarea
                    id="return-completion-notes"
                    rows={2}
                    value={receiptNotes}
                    onChange={(e) => {
                      setReceiptNotes(e.target.value);
                      if (receiptFormError) setReceiptFormError(null);
                    }}
                    placeholder={
                      isDiscrepancy
                        ? t("VD: Đã lập biên bản bồi thường với tài xế, đồng ý chốt nhập kho...")
                        : t("VD: Đã đồng kiểm nguyên tem niêm phong, nhập kho hoàn...")
                    }
                    className="w-full rounded-lg border border-slate-300 px-3 py-2 text-xs text-slate-900 transition-colors focus:border-[#17766c] focus:outline-none focus:ring-2 focus:ring-[#17766c]/20"
                  />
                  {receiptFormError && (
                    <p role="alert" className="text-xs font-medium text-rose-600">
                      {receiptFormError}
                    </p>
                  )}
                </div>
              )}

              {/* Actions for return */}
              <div className="flex flex-wrap items-center justify-end gap-2 border-t border-slate-200 pt-4">
                {selectedReturn.status === 'REQUESTED' && (
                  <Button
                    onClick={() => void handleApproveReturn(selectedReturn.id)}
                    isLoading={actionLoading}
                    className="cursor-pointer bg-[#17766c] text-white hover:bg-[#136058]"
                  >
                    <CheckCircle2 size={15} aria-hidden="true" className="mr-1.5" />
                    {t("Phê duyệt hoàn hàng")}
                  </Button>
                )}

                {canReceiveNormally && (
                  <>
                    <Button
                      variant="outline"
                      onClick={() => setDiscrepancyModalOpen(true)}
                      className="cursor-pointer border-rose-300 text-rose-700 hover:bg-rose-50"
                    >
                      <ShieldAlert size={15} aria-hidden="true" className="mr-1.5" />
                      {t("Có chênh lệch / Hàng hỏng")}
                    </Button>

                    <Button
                      onClick={() => void handleConfirmReceipt(selectedReturn.id, receiptNotes, false)}
                      isLoading={actionLoading}
                      className="cursor-pointer bg-emerald-600 text-white hover:bg-emerald-700"
                    >
                      <ClipboardCheck size={15} aria-hidden="true" className="mr-1.5" />
                      {t("Xác nhận đã nhận hàng đủ")}
                    </Button>
                  </>
                )}

                {isDiscrepancy && (
                  <Button
                    onClick={() => void handleConfirmReceipt(selectedReturn.id, receiptNotes, true)}
                    isLoading={actionLoading}
                    className="cursor-pointer bg-amber-600 text-white hover:bg-amber-700"
                  >
                    <ClipboardCheck size={15} aria-hidden="true" className="mr-1.5" />
                    {t("Chốt xử lý & nhập kho")}
                  </Button>
                )}

                <Button
                  variant="outline"
                  onClick={() => {
                    setSelectedReturn(null);
                    setReceiptNotes('');
                    setReceiptFormError(null);
                  }}
                  className="cursor-pointer"
                >
                  {t("Đóng")}
                </Button>
              </div>
            </div>
          );
        })()}
      </Modal>

      {/* ========================================================================= */}
      {/* MODAL 3: REPORT DISCREPANCY & CLOUDINARY UPLOAD */}
      {/* ========================================================================= */}
      <Modal
        open={discrepancyModalOpen}
        onOpenChange={(open) => {
          if (!open) setDiscrepancyModalOpen(false);
        }}
        title={t("Báo cáo chênh lệch / Hàng hoàn bị hỏng")}
        description={t("Ghi nhận tình trạng hư hỏng, mất hàng hoặc sai sản phẩm khi kiểm nhận bàn giao từ tài xế.")}
        className="max-w-lg"
      >
        <div className="space-y-4 text-sm">
          <div>
            <label className="mb-1 block text-xs font-semibold text-slate-700">{t("Loại chênh lệch:")}</label>
            <CustomSelect
              aria-label={t("Loại chênh lệch:")}
              value={discrepancyType}
              onValueChange={setDiscrepancyType}
              options={[
                { value: 'DAMAGED', label: t("Hàng hóa bị vỡ / hư hỏng") },
                { value: 'MISSING_ITEMS', label: t("Thiếu kiện hàng / thiếu sản phẩm") },
                { value: 'WRONG_ITEMS', label: t("Sai sản phẩm hoàn về") },
                { value: 'REFUSED_BY_RECIPIENT', label: t("Bao bì rách nát / khách từ chối") },
                { value: 'OTHER', label: t("Lý do khác") },
              ]}
            />
          </div>

          <div>
            <label className="mb-1 block text-xs font-semibold text-slate-700">{t("Ghi chú chi tiết:")}</label>
            <textarea
              rows={3}
              value={discrepancyNotes}
              onChange={(e) => setDiscrepancyNotes(e.target.value)}
              placeholder={t("Mô tả cụ thể lý do chênh lệch hoặc tình trạng hàng (VD: Hộp rách vỡ, thiếu 1 sản phẩm bên trong...)")}
              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-xs text-slate-900 transition-colors focus:border-rose-500 focus:outline-none focus:ring-2 focus:ring-rose-500/20"
            />
          </div>

          {/* Cloudinary Upload Field */}
          <div>
            <label className="mb-1 block text-xs font-semibold text-slate-700">
              {t("Ảnh chụp bằng chứng (Tải lên Cloudinary):")}
            </label>
            <div className="flex flex-wrap items-center gap-3">
              <input
                type="file"
                accept="image/*"
                onChange={(e) => {
                  if (e.target.files && e.target.files[0]) {
                    setDiscrepancyFile(e.target.files[0]);
                  }
                }}
                className="cursor-pointer text-xs text-slate-600 file:mr-3 file:cursor-pointer file:rounded-lg file:border-0 file:bg-slate-100 file:px-3 file:py-1.5 file:text-xs file:font-semibold file:text-slate-700 hover:file:bg-slate-200"
              />
              {discrepancyFile && (
                <span className="inline-flex items-center gap-1 text-xs font-medium text-emerald-700">
                  <Check size={14} aria-hidden="true" />
                  <span>{discrepancyFile.name}</span>
                </span>
              )}
            </div>
          </div>

          <div className="flex justify-end gap-2 border-t border-slate-200 pt-4">
            <Button variant="outline" size="sm" onClick={() => setDiscrepancyModalOpen(false)} className="cursor-pointer">
              {t("Hủy")}
            </Button>
            <Button
              size="sm"
              onClick={() => void handleSubmitDiscrepancy()}
              isLoading={actionLoading}
              className="cursor-pointer bg-rose-600 text-white hover:bg-rose-700"
            >
              {t("Gửi báo cáo chênh lệch")}
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
