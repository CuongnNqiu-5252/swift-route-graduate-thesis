'use client';

import type { Translator } from "@/i18n/translate";
import { useLocale } from '@/i18n/provider';

import { useAuth } from '@/application/contexts/auth-context';
import { memberRepository } from '@/infrastructure/repositories/member.repository';
import { useCallback, useEffect, useState } from 'react';
import {
  AlertTriangle,
  RotateCcw,
  Banknote,
  CheckCircle2,
  XCircle,
  Eye,
  RefreshCw,
  Clock,
  ShieldAlert,
  Package,
  UserCheck,
  AlertOctagon,
} from 'lucide-react';
import api from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Modal } from '@/components/ui/modal';
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

const incidentLabels: Record<string, string> = {
  CUSTOMER_NOT_AVAILABLE: 'Không thấy khách',
  CUSTOMER_ABSENT: 'Không thấy khách',
  CUSTOMER_REFUSED: 'Khách từ chối nhận hàng',
  WRONG_ADDRESS: 'Sai địa chỉ',
  VEHICLE_BREAKDOWN: 'Xe gặp sự cố',
};

function incidentLabel(code: string, t: Translator) {
  return t(incidentLabels[code] ?? code);
}

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
      return <Badge className="bg-slate-100 text-slate-700">{severity}</Badge>;
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
      return <Badge className="bg-slate-100 text-slate-700">{status}</Badge>;
  }
};

const returnStatusBadge = (status: string, t: Translator) => {
  switch (status?.toUpperCase()) {
    case 'REQUESTED':
      return <Badge className="bg-amber-100 text-amber-800 border border-amber-300">{t("Chờ duyệt")}</Badge>;
    case 'APPROVED':
      return <Badge className="bg-blue-50 text-blue-700 border border-blue-200">{t("Đã duyệt")}</Badge>;
    case 'IN_EXECUTION':
    case 'ASSIGNED':
      return <Badge className="bg-sky-50 text-sky-700 border border-sky-200">{t("Đang hoàn về")}</Badge>;
    case 'ARRIVED':
      return <Badge className="bg-indigo-50 text-indigo-700 border border-indigo-200">{t("Đã đến điểm hoàn")}</Badge>;
    case 'AWAITING_RECEIPT':
      return <Badge className="bg-orange-100 text-orange-800 border border-orange-300 animate-pulse">{t("Chờ kiểm nhận")}</Badge>;
    case 'DISCREPANCY':
      return <Badge className="bg-rose-600 text-white font-semibold">{t("Có chênh lệch / Hỏng")}</Badge>;
    case 'COMPLETED':
      return <Badge className="bg-emerald-50 text-emerald-700 border border-emerald-200">{t("Đã nhận hàng vào kho")}</Badge>;
    case 'REJECTED':
      return <Badge className="bg-slate-100 text-slate-600">{t("Bị từ chối")}</Badge>;
    default:
      return <Badge className="bg-slate-100 text-slate-700">{status}</Badge>;
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
              {t("Tài xế gửi:")}{" "}{drivers.find((driver) => driver.user_id === request.from_driver_id)?.name ?? request.from_driver_id}
              {request.to_driver_id && (
                <>{" "}{t("· Tài xế nhận:")}{" "}{drivers.find((driver) => driver.user_id === request.to_driver_id)?.name ?? request.to_driver_id}</>
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
            <label className="block text-sm">
              {t("Tài xế nhận")}
              <select className="ml-2 rounded border p-2" value={driverId} onChange={(event) => setDriverId(event.target.value)}>
                <option value="">{t("Chọn tài xế")}</option>
                {drivers.filter((driver) => driver.onboarding_status === 'ACTIVE' && driver.user_id
                  && driver.user_id !== selected.from_driver_id).map((driver) => (
                  <option key={driver.user_id} value={driver.user_id}>{driver.name}</option>
                ))}
              </select>
            </label>
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
  const [memberDirectory, setMemberDirectory] = useState<{ tenantId: string; names: Map<string, string> }>();
  const tenantId = user?.tenantId;
  useEffect(() => {
    if (!tenantId) return;
    let active = true;
    memberRepository.getMembers(tenantId).then((members) => {
      if (active) {
        setMemberDirectory({ tenantId, names: new Map(members.map((member) => [member.user_id, member.full_name.trim()])) });
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

  // Discrepancy Form
  const [discrepancyModalOpen, setDiscrepancyModalOpen] = useState(false);
  const [discrepancyType, setDiscrepancyType] = useState('DAMAGED');
  const [actualQuantity, setActualQuantity] = useState<number>(1);
  const [discrepancyNotes, setDiscrepancyNotes] = useState('');
  const [discrepancyFile, setDiscrepancyFile] = useState<File | null>(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [incRes, retRes, revRes, drivers] = await Promise.all([
        operationsRepository.getIncidents(),
        operationsRepository.getReturns(),
        api.get<unknown>('/operations/review'),
        driverRepository.getDrivers().catch(() => []),
      ]);
      setIncidents(incRes);
      setReturns(retRes);

      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const rawRev = (revRes.data as any)?.data ?? revRes.data;
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
      api.get<unknown>('/operations/review'),
      driverRepository.getDrivers().catch(() => []),
    ]).then(([incRes, retRes, revRes, drivers]) => {
      if (!active) return;
      setIncidents(incRes);
      setReturns(retRes);
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const rawRev = (revRes.data as any)?.data ?? revRes.data;
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

  // Handle Warehouse Confirm Receipt
  const handleConfirmReceipt = async (returnId: string, notes?: string) => {
    setActionLoading(true);
    setError(null);
    try {
      await operationsRepository.confirmReturnReceipt(returnId, notes);
      setSelectedReturn(null);
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
        actual_quantity: actualQuantity,
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
                <select
                  value={incidentStatusFilter}
                  onChange={(e) => setIncidentStatusFilter(e.target.value)}
                  className="rounded-md border border-slate-300 bg-white px-2.5 py-1 text-xs text-slate-800 focus:outline-none focus:ring-1 focus:ring-rose-500"
                >
                  <option value="ALL">{t("Tất cả trạng thái")}</option>
                  <option value="OPEN">{t("Chờ xử lý (OPEN)")}</option>
                  <option value="IN_PROGRESS">{t("Đang xử lý (IN_PROGRESS)")}</option>
                  <option value="ESCALATED">Leo thang (ESCALATED)</option>
                  <option value="RESOLVED">{t("Đã giải quyết (RESOLVED)")}</option>
                </select>
              </div>

              <div className="flex items-center gap-1.5 text-xs font-medium text-slate-500">
                <span>{t("Mức độ:")}</span>
                <select
                  value={incidentSeverityFilter}
                  onChange={(e) => setIncidentSeverityFilter(e.target.value)}
                  className="rounded-md border border-slate-300 bg-white px-2.5 py-1 text-xs text-slate-800 focus:outline-none focus:ring-1 focus:ring-rose-500"
                >
                  <option value="ALL">{t("Tất cả mức độ")}</option>
                  <option value="CRITICAL">{t("Khẩn cấp")}</option>
                  <option value="HIGH">{t("Cao")}</option>
                  <option value="MEDIUM">{t("Trung bình")}</option>
                  <option value="LOW">{t("Thấp")}</option>
                </select>
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
                    <th className="px-5 py-3 font-semibold">{t("Khả năng tiếp tục")}</th>
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
                        <div className="font-medium text-slate-800">{incidentLabel(inc.incident_type, t)}</div>
                        {inc.notes && (
                          <div className="max-w-xs truncate text-xs text-slate-500" title={inc.notes}>
                            {inc.notes}
                          </div>
                        )}
                      </td>
                      <td className="px-5 py-3.5">{severityBadge(inc.severity, t)}</td>
                      <td className="px-5 py-3.5">
                        {inc.can_continue_delivery ? (
                          <span className="inline-flex items-center gap-1 text-xs font-medium text-emerald-700">
                            <CheckCircle2 size={13} />{" "}{t("Tiếp tục được")}
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1 text-xs font-medium text-rose-700">
                            <XCircle size={13} />{" "}{t("Tạm dừng đơn")}
                          </span>
                        )}
                      </td>
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
                      <td colSpan={7} className="px-5 py-12 text-center text-sm text-slate-500">
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
                <select
                  value={returnStatusFilter}
                  onChange={(e) => setReturnStatusFilter(e.target.value)}
                  className="rounded-md border border-slate-300 bg-white px-2.5 py-1 text-xs text-slate-800 focus:outline-none focus:ring-1 focus:ring-indigo-500"
                >
                  <option value="ALL">{t("Tất cả trạng thái")}</option>
                  <option value="ACTIVE">{t("Đang xử lý")}</option>
                  <option value="REQUESTED">{t("Chờ duyệt")}</option>
                  <option value="IN_EXECUTION">{t("Đang hoàn về")}</option>
                  <option value="AWAITING_RECEIPT">{t("Chờ kiểm nhận")}</option>
                  <option value="DISCREPANCY">{t("Có chênh lệch / Hỏng")}</option>
                  <option value="COMPLETED">{t("Đã nhận hàng vào kho")}</option>
                </select>
              </div>
            </div>

            <div className="text-xs text-slate-500">
              {t("Hiển thị")}{" "}<span className="font-semibold text-slate-800">{filteredReturns.length}</span>{" "}{t("yêu cầu")}
            </div>
          </div>

          {/* Returns Table */}
          <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm text-slate-600">
                <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wider text-slate-500">
                  <tr>
                    <th className="px-5 py-3 font-semibold">{t("Mã đơn")}</th>
                    <th className="px-5 py-3 font-semibold">{t("Lý do hoàn")}</th>
                    <th className="px-5 py-3 font-semibold">{t("Điểm hoàn về")}</th>
                    <th className="px-5 py-3 font-semibold">{t("Người giữ hàng (Tài xế)")}</th>
                    <th className="px-5 py-3 font-semibold">{t("Số kiện")}</th>
                    <th className="px-5 py-3 font-semibold">{t("Trạng thái")}</th>
                    <th className="px-5 py-3 text-right font-semibold">{t("Thao tác")}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {filteredReturns.map((ret) => (
                    <tr key={ret.id} className="hover:bg-slate-50/75 transition-colors">
                      <td className="px-5 py-3.5">
                        <div className="font-semibold text-slate-900">
                          {ret.order_code || `#${ret.order_id.slice(0, 8)}`}
                        </div>
                        <div className="text-xs text-slate-500">
                          {t("Loại:")}{" "}{ret.request_type === 'REDELIVERY' ? t("Giao lại") : t("Hoàn về kho")}
                        </div>
                      </td>
                      <td className="px-5 py-3.5">
                        <div className="max-w-xs text-sm font-medium text-slate-800 truncate" title={ret.reason}>
                          {ret.reason}
                        </div>
                        <div className="text-xs text-slate-400">{t("Yêu cầu lúc:")}{" "}{formatDate(ret.created_at, intlLocale)}</div>
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
                        {ret.current_holder_id ? (
                          <span className="inline-flex items-center gap-1 text-xs font-medium text-slate-700">
                            <UserCheck size={13} className="text-blue-600" />
                            TX: {ret.current_holder_id.slice(0, 8)}
                          </span>
                        ) : (
                          <span className="text-xs text-slate-400">{t("Đã bàn giao về kho")}</span>
                        )}
                      </td>
                      <td className="px-5 py-3.5 font-semibold text-slate-800">
                        {t("1 kiện")}
                      </td>
                      <td className="px-5 py-3.5">{returnStatusBadge(ret.status, t)}</td>
                      <td className="px-5 py-3.5 text-right">
                        <div className="flex items-center justify-end gap-1.5">
                          {ret.status === 'REQUESTED' && (
                            <Button
                              size="sm"
                              onClick={() => void handleApproveReturn(ret.id)}
                              isLoading={actionLoading}
                              className="text-xs bg-indigo-600 hover:bg-indigo-700"
                            >
                              <CheckCircle2 size={13} className="mr-1" />
                              {t("Duyệt hoàn")}
                            </Button>
                          )}

                          {(ret.status === 'IN_EXECUTION' || ret.status === 'AWAITING_RECEIPT' || ret.status === 'ARRIVED') && (
                            <>
                              <Button
                                size="sm"
                                onClick={() => void handleConfirmReceipt(ret.id)}
                                isLoading={actionLoading}
                                className="text-xs bg-emerald-600 hover:bg-emerald-700 text-white"
                              >
                                <CheckCircle2 size={13} className="mr-1" />
                                {t("Nhận hàng")}
                              </Button>
                              <Button
                                size="sm"
                                variant="outline"
                                onClick={() => {
                                  setSelectedReturn(ret);
                                  setDiscrepancyModalOpen(true);
                                }}
                                className="text-xs border-rose-300 text-rose-700 hover:bg-rose-50"
                              >
                                <ShieldAlert size={13} className="mr-1" />
                                {t("Báo hỏng/lệch")}
                              </Button>
                            </>
                          )}

                          <Button
                            size="sm"
                            variant="outline"
                            onClick={() => setSelectedReturn(ret)}
                            className="text-xs"
                          >
                            <Eye size={14} className="mr-1" />
                            {t("Chi tiết")}
                          </Button>
                        </div>
                      </td>
                    </tr>
                  ))}

                  {filteredReturns.length === 0 && (
                    <tr>
                      <td colSpan={7} className="px-5 py-12 text-center text-sm text-slate-500">
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
                      {t("Tài xế:")}{" "}{codDrivers.find((driver) => driver.user_id === item.driver_id)?.name?.trim() || t("Chưa có tên")}{" "}{t("· Gửi lúc:")}{" "}{formatDate(item.declared_at, intlLocale)}
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
                  <select
                    id="cod-variance-resolution"
                    value={codVarianceResolution}
                    onChange={(event) => setCodVarianceResolution(event.target.value as CodVarianceResolution)}
                    className="h-11 w-full rounded-lg border border-slate-300 bg-white px-3 text-slate-900 outline-none focus:border-slate-700 focus:ring-2 focus:ring-slate-200"
                  >
                    <option value="BUSINESS_ADJUSTMENT">{t("Doanh nghiệp chấp nhận điều chỉnh")}</option>
                    <option value="DRIVER_RESPONSIBLE">{t("Ghi nhận tài xế chịu trách nhiệm")}</option>
                    <option value="OTHER">{t("Xử lý theo quyết định khác")}</option>
                  </select>
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
        title={`Chi tiết sự cố: ${selectedIncident?.order_code || (selectedIncident?.order_id ? `#${selectedIncident.order_id.slice(0, 8)}` : t("Tuyến đường"))}`}
        description={t("Thông tin sự cố do tài xế hoặc hệ thống báo cáo và các hành động xử lý chuẩn SaaS.")}
        className="max-w-2xl"
      >
        {selectedIncident && (
          <div className="space-y-5">
            {/* Incident Summary Card */}
            <div className="rounded-lg border border-slate-200 bg-slate-50 p-4 space-y-2.5 text-sm">
              <div className="flex items-center justify-between">
                <span className="font-semibold text-slate-900">{incidentLabel(selectedIncident.incident_type, t)}</span>
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
                  <span className="text-slate-400">{t("Người báo cáo:")}</span> {selectedIncident.reported_by.slice(0, 8)}
                </div>
                <div>
                  <span className="text-slate-400">{t("Thời gian:")}</span> {formatDate(selectedIncident.created_at, intlLocale)}
                </div>
                <div>
                  <span className="text-slate-400">{t("Tài xế phụ trách:")}</span>{' '}
                  {selectedIncident.driver_id ? selectedIncident.driver_id.slice(0, 8) : t("Chưa gán")}
                </div>
                <div>
                  <span className="text-slate-400">{t("Có thể tiếp tục giao:")}</span>{' '}
                  {selectedIncident.can_continue_delivery ? t("Có") : t("Không (Tạm dừng đơn)")}
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
                      <div className="font-semibold text-left">Leo thang</div>
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
          if (!open) setSelectedReturn(null);
        }}
        title={`${t("Yêu cầu hoàn hàng:")} ${selectedReturn?.order_code || (selectedReturn?.order_id ? `#${selectedReturn.order_id.slice(0, 8)}` : '')}`}
        description={t("Theo dõi lộ trình hoàn về kho, xác nhận lưu ký và kiểm nhận hàng hóa.")}
        className="max-w-2xl"
      >
        {selectedReturn && (
          <div className="space-y-4 text-sm">
            <div className="rounded-lg border border-slate-200 bg-slate-50 p-4 space-y-2">
              <div className="flex items-center justify-between">
                <span className="font-semibold text-slate-900">{t("Lý do:")}{" "}{selectedReturn.reason}</span>
                {returnStatusBadge(selectedReturn.status, t)}
              </div>

              <div className="grid grid-cols-2 gap-2 text-xs text-slate-600 pt-2 border-t border-slate-200">
                <div>
                  <span className="text-slate-400">{t("Kho hoàn về:")}</span>{' '}
                  <span className="font-medium text-slate-800">{selectedReturn.return_location_name || t("Kho ban đầu")}</span>
                </div>
                <div>
                  <span className="text-slate-400">{t("Địa chỉ:")}</span>{' '}
                  <span className="font-medium text-slate-800">{selectedReturn.return_address || t("Địa chỉ kho gửi")}</span>
                </div>
                <div>
                  <span className="text-slate-400">{t("Người đang cầm hàng:")}</span>{' '}
                  <span className="font-medium text-slate-800">
                    {selectedReturn.current_holder_id ? `${t("Tài xế")} (${selectedReturn.current_holder_id.slice(0, 8)})` : t("Đã nhập kho")}
                  </span>
                </div>
                <div>
                  <span className="text-slate-400">{t("Số lượng kiện:")}</span>{' '}
                  <span className="font-semibold text-slate-800">{t("1 kiện")}</span>
                </div>
                <div>
                  <span className="text-slate-400">{t("Người tạo yêu cầu:")}</span>{' '}
                  <span title={selectedReturn.requested_by || undefined}>
                    {(memberDirectory?.tenantId === tenantId && memberDirectory?.names.get(selectedReturn.requested_by))
                      || (selectedReturn.requested_by === user?.id && user?.name)
                      || (selectedReturn.requested_by ? `${t("Tài khoản")} #${selectedReturn.requested_by.slice(0, 8)}` : t("Chưa xác định"))}
                  </span>
                </div>
                <div>
                  <span className="text-slate-400">{t("Thời gian tạo:")}</span> {formatDate(selectedReturn.created_at, intlLocale)}
                </div>
              </div>

              {/* Discrepancy Alert Banner if reported */}
              {selectedReturn.status === 'DISCREPANCY' && (
                <div className="mt-2 rounded border border-rose-200 bg-rose-50 p-3 text-xs text-rose-800">
                  <div className="font-semibold">{t("⚠️ Hàng hoàn có chênh lệch / hư hỏng:")}</div>
                  <div className="mt-1">
                    {t("Loại:")}{" "}{selectedReturn.discrepancy_type}{selectedReturn.actual_quantity !== null && selectedReturn.actual_quantity !== undefined ? ` · Thực nhận: ${selectedReturn.actual_quantity} kiện` : ''}
                  </div>
                  {selectedReturn.discrepancy_notes && <div className="mt-0.5">{t("Ghi chú:")}{" "}{selectedReturn.discrepancy_notes}</div>}
                  {selectedReturn.discrepancy_evidence_url && (
                    <div className="mt-2">
                      <a
                        href={selectedReturn.discrepancy_evidence_url}
                        target="_blank"
                        rel="noreferrer"
                        className="text-rose-700 underline font-medium"
                      >
                        {t("Xem ảnh chụp hàng hỏng")}
                      </a>
                    </div>
                  )}
                </div>
              )}
            </div>

            {/* Actions for return */}
            <div className="flex flex-wrap items-center justify-end gap-2 border-t border-slate-200 pt-4">
              {selectedReturn.status === 'REQUESTED' && (
                <Button
                  onClick={() => void handleApproveReturn(selectedReturn.id)}
                  isLoading={actionLoading}
                  className="bg-indigo-600 hover:bg-indigo-700 text-white"
                >
                  <CheckCircle2 size={15} className="mr-1.5" />
                  {t("Phê duyệt hoàn hàng")}
                </Button>
              )}

              {(selectedReturn.status === 'IN_EXECUTION' || selectedReturn.status === 'AWAITING_RECEIPT' || selectedReturn.status === 'ARRIVED') && (
                <>
                  <Button
                    variant="outline"
                    onClick={() => setDiscrepancyModalOpen(true)}
                    className="border-rose-300 text-rose-700 hover:bg-rose-50"
                  >
                    <ShieldAlert size={15} className="mr-1.5" />
                    {t("Có chênh lệch / Hàng hỏng")}
                  </Button>

                  <Button
                    onClick={() => void handleConfirmReceipt(selectedReturn.id)}
                    isLoading={actionLoading}
                    className="bg-emerald-600 hover:bg-emerald-700 text-white"
                  >
                    <CheckCircle2 size={15} className="mr-1.5" />
                    {t("Xác nhận đã nhận hàng đủ")}
                  </Button>
                </>
              )}

              <Button variant="outline" onClick={() => setSelectedReturn(null)}>
                {t("Đóng")}
              </Button>
            </div>
          </div>
        )}
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
        description={t("Ghi nhận sai lệch số lượng hoặc hàng hóa hư hỏng khi nhận bàn giao từ tài xế.")}
        className="max-w-lg"
      >
        <div className="space-y-4 text-sm">
          <div>
            <label className="text-xs font-semibold text-slate-700 block mb-1">{t("Loại chênh lệch:")}</label>
            <select
              value={discrepancyType}
              onChange={(e) => setDiscrepancyType(e.target.value)}
              className="w-full rounded-md border border-slate-300 p-2 text-xs text-slate-900 focus:outline-none focus:ring-1 focus:ring-rose-500"
            >
              <option value="DAMAGED">{t("Hàng hóa bị vỡ / hư hỏng")}</option>
              <option value="MISSING_ITEMS">{t("Thiếu kiện hàng / thiếu sản phẩm")}</option>
              <option value="WRONG_ITEMS">{t("Sai sản phẩm hoàn về")}</option>
              <option value="REFUSED_BY_RECIPIENT">{t("Bao bì rách nát / khách từ chối")}</option>
              <option value="OTHER">{t("Lý do khác")}</option>
            </select>
          </div>

          <div>
            <label className="text-xs font-semibold text-slate-700 block mb-1">
              {t("Số lượng kiện thực nhận:")}
            </label>
            <input
              type="number"
              min={0}
              max={1}
              value={actualQuantity}
              onChange={(e) => setActualQuantity(parseInt(e.target.value) || 0)}
              className="w-full rounded-md border border-slate-300 p-2 text-xs text-slate-900 focus:outline-none focus:ring-1 focus:ring-rose-500"
            />
          </div>

          <div>
            <label className="text-xs font-semibold text-slate-700 block mb-1">{t("Ghi chú chi tiết:")}</label>
            <textarea
              rows={2}
              value={discrepancyNotes}
              onChange={(e) => setDiscrepancyNotes(e.target.value)}
              placeholder={t("Mô tả cụ thể lý do chênh lệch hoặc tình trạng hàng (VD: Hộp rách vỡ, thiếu 1 sản phẩm bên trong...)")}
              className="w-full rounded-md border border-slate-300 p-2 text-xs text-slate-900 focus:outline-none focus:ring-1 focus:ring-rose-500"
            />
          </div>

          {/* Cloudinary Upload Field */}
          <div>
            <label className="text-xs font-semibold text-slate-700 block mb-1">
              {t("Ảnh chụp bằng chứng (Tải lên Cloudinary):")}
            </label>
            <div className="flex items-center gap-3">
              <input
                type="file"
                accept="image/*"
                onChange={(e) => {
                  if (e.target.files && e.target.files[0]) {
                    setDiscrepancyFile(e.target.files[0]);
                  }
                }}
                className="text-xs text-slate-500 file:mr-3 file:py-1.5 file:px-3 file:rounded-md file:border-0 file:text-xs file:font-semibold file:bg-slate-100 file:text-slate-700 hover:file:bg-slate-200"
              />
              {discrepancyFile && (
                <span className="text-xs text-emerald-600 font-medium">{t("✓ Đã chọn file")}</span>
              )}
            </div>
          </div>

          <div className="flex justify-end gap-2 border-t border-slate-200 pt-4">
            <Button variant="outline" size="sm" onClick={() => setDiscrepancyModalOpen(false)}>
              {t("Hủy")}
            </Button>
            <Button
              size="sm"
              onClick={() => void handleSubmitDiscrepancy()}
              isLoading={actionLoading}
              className="bg-rose-600 hover:bg-rose-700 text-white"
            >
              {t("Gửi báo cáo chênh lệch")}
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
