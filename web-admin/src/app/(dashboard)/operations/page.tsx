'use client';

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

type TabKey = 'incidents' | 'returns' | 'cod';

type CodItem = {
  id: string;
  order_id: string;
  order_code: string | null;
  driver_id: string;
  collected_amount: number;
  declared_amount: number;
  notes: string | null;
  declared_at: string;
};

const formatCurrency = (amount: number) =>
  new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(amount);

const formatDate = (isoStr?: string | null) => {
  if (!isoStr) return '—';
  try {
    return new Date(isoStr).toLocaleString('vi-VN', {
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

const severityBadge = (severity: string) => {
  switch (severity?.toUpperCase()) {
    case 'CRITICAL':
      return <Badge className="bg-red-600 text-white font-semibold">Khẩn cấp</Badge>;
    case 'HIGH':
      return <Badge className="bg-rose-100 text-rose-800 border border-rose-300">Cao</Badge>;
    case 'MEDIUM':
      return <Badge className="bg-amber-100 text-amber-800 border border-amber-300">Trung bình</Badge>;
    case 'LOW':
      return <Badge className="bg-blue-100 text-blue-800 border border-blue-300">Thấp</Badge>;
    default:
      return <Badge className="bg-slate-100 text-slate-700">{severity}</Badge>;
  }
};

const incidentStatusBadge = (status: string) => {
  switch (status?.toUpperCase()) {
    case 'OPEN':
      return <Badge className="bg-rose-50 text-rose-700 border border-rose-200">Đang chờ xử lý</Badge>;
    case 'IN_PROGRESS':
      return <Badge className="bg-amber-50 text-amber-700 border border-amber-200">Đang xử lý</Badge>;
    case 'ESCALATED':
      return <Badge className="bg-purple-100 text-purple-800 border border-purple-300">Đã leo thang</Badge>;
    case 'RESOLVED':
      return <Badge className="bg-emerald-50 text-emerald-700 border border-emerald-200">Đã giải quyết</Badge>;
    case 'CANCELLED':
      return <Badge className="bg-slate-100 text-slate-600">Đã hủy</Badge>;
    default:
      return <Badge className="bg-slate-100 text-slate-700">{status}</Badge>;
  }
};

const returnStatusBadge = (status: string) => {
  switch (status?.toUpperCase()) {
    case 'REQUESTED':
      return <Badge className="bg-amber-100 text-amber-800 border border-amber-300">Chờ duyệt</Badge>;
    case 'APPROVED':
      return <Badge className="bg-blue-50 text-blue-700 border border-blue-200">Đã duyệt</Badge>;
    case 'IN_EXECUTION':
    case 'ASSIGNED':
      return <Badge className="bg-sky-50 text-sky-700 border border-sky-200">Đang hoàn về</Badge>;
    case 'ARRIVED':
      return <Badge className="bg-indigo-50 text-indigo-700 border border-indigo-200">Đã đến điểm hoàn</Badge>;
    case 'AWAITING_RECEIPT':
      return <Badge className="bg-orange-100 text-orange-800 border border-orange-300 animate-pulse">Chờ kiểm nhận</Badge>;
    case 'DISCREPANCY':
      return <Badge className="bg-rose-600 text-white font-semibold">Có chênh lệch / Hỏng</Badge>;
    case 'COMPLETED':
      return <Badge className="bg-emerald-50 text-emerald-700 border border-emerald-200">Đã nhận hàng vào kho</Badge>;
    case 'REJECTED':
      return <Badge className="bg-slate-100 text-slate-600">Bị từ chối</Badge>;
    default:
      return <Badge className="bg-slate-100 text-slate-700">{status}</Badge>;
  }
};

export default function OperationsPage() {
  const [activeTab, setActiveTab] = useState<TabKey>('incidents');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [incidents, setIncidents] = useState<IncidentDto[]>([]);
  const [returns, setReturns] = useState<ReturnRequestDto[]>([]);
  const [codList, setCodList] = useState<CodItem[]>([]);

  // Filters
  const [incidentStatusFilter, setIncidentStatusFilter] = useState<string>('ALL');
  const [incidentSeverityFilter, setIncidentSeverityFilter] = useState<string>('ALL');
  const [returnStatusFilter, setReturnStatusFilter] = useState<string>('ALL');

  // Modals & Drawers
  const [selectedIncident, setSelectedIncident] = useState<IncidentDto | null>(null);
  const [selectedReturn, setSelectedReturn] = useState<ReturnRequestDto | null>(null);

  // Form states in modals
  const [resolvingAction, setResolvingAction] = useState<ResolveIncidentParams['resolution_type'] | null>(null);
  const [resolutionNotes, setResolutionNotes] = useState('');
  const [actionLoading, setActionLoading] = useState(false);

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
      const [incRes, retRes, revRes] = await Promise.all([
        operationsRepository.getIncidents(),
        operationsRepository.getReturns(),
        api.get<unknown>('/operations/review'),
      ]);
      setIncidents(incRes);
      setReturns(retRes);

      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const rawRev = (revRes.data as any)?.data ?? revRes.data;
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const rawCod = (rawRev?.cod_collections ?? rawRev?.codCollections ?? []).map((item: any) => ({
        id: String(item.id ?? ''),
        order_id: String(item.order_id ?? item.orderId ?? ''),
        order_code: (item.order_code ?? item.orderCode ?? null) as string | null,
        driver_id: String(item.driver_id ?? item.driverId ?? ''),
        collected_amount: Number(item.collected_amount ?? item.collectedAmount ?? 0),
        declared_amount: Number(item.declared_amount ?? item.declaredAmount ?? 0),
        notes: (item.notes ?? null) as string | null,
        declared_at: String(item.declared_at ?? item.declaredAt ?? ''),
      }));
      setCodList(rawCod);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không thể tải dữ liệu điều hành');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    let active = true;
    Promise.all([
      operationsRepository.getIncidents(),
      operationsRepository.getReturns(),
      api.get<unknown>('/operations/review'),
    ]).then(([incRes, retRes, revRes]) => {
      if (!active) return;
      setIncidents(incRes);
      setReturns(retRes);
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const rawRev = (revRes.data as any)?.data ?? revRes.data;
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const rawCod = (rawRev?.cod_collections ?? rawRev?.codCollections ?? []).map((item: any) => ({
        id: String(item.id ?? ''),
        order_id: String(item.order_id ?? item.orderId ?? ''),
        order_code: (item.order_code ?? item.orderCode ?? null) as string | null,
        driver_id: String(item.driver_id ?? item.driverId ?? ''),
        collected_amount: Number(item.collected_amount ?? item.collectedAmount ?? 0),
        declared_amount: Number(item.declared_amount ?? item.declaredAmount ?? 0),
        notes: (item.notes ?? null) as string | null,
        declared_at: String(item.declared_at ?? item.declaredAt ?? ''),
      }));
      setCodList(rawCod);
    }).catch((err) => {
      if (active) setError(err instanceof Error ? err.message : 'Không thể tải dữ liệu điều hành');
    }).finally(() => {
      if (active) setLoading(false);
    });
    return () => { active = false; };
  }, []);

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
      setError(err instanceof Error ? err.message : 'Lỗi khi xử lý sự cố');
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
      setError(err instanceof Error ? err.message : 'Lỗi khi duyệt hoàn hàng');
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
      setError(err instanceof Error ? err.message : 'Lỗi khi xác nhận nhận hàng');
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
      setError(err instanceof Error ? err.message : 'Lỗi khi báo cáo chênh lệch');
    } finally {
      setActionLoading(false);
    }
  };

  // Handle Verify COD
  const handleVerifyCod = async (codId: string, amount: number = 0) => {
    setActionLoading(true);
    setError(null);
    try {
      await operationsRepository.verifyCod(codId, amount);
      await loadData();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Lỗi khi xác minh COD');
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

  const filteredReturns = returns.filter((r) => {
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
            Trung tâm Điều hành & Xử lý Ngoại lệ
          </h1>
          <p className="mt-1 text-sm text-slate-600">
            Giám sát sự cố phát sinh trong chuyến giao, quản lý quy trình hoàn hàng về kho (RTS) và đối soát COD.
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
            Làm mới
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
            <span className="text-sm font-medium text-slate-500">Sự cố cần giải quyết</span>
            <span className="rounded-lg bg-rose-50 p-2 text-rose-600">
              <AlertTriangle size={18} />
            </span>
          </div>
          <div className="mt-2 flex items-baseline gap-2">
            <span className="text-2xl font-bold tracking-tight text-slate-900">{openIncidentsCount}</span>
            <span className="text-xs text-slate-500">trên {incidents.length} sự cố</span>
          </div>
        </div>

        <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
          <div className="flex items-center justify-between">
            <span className="text-sm font-medium text-slate-500">Hoàn hàng chờ duyệt</span>
            <span className="rounded-lg bg-amber-50 p-2 text-amber-600">
              <RotateCcw size={18} />
            </span>
          </div>
          <div className="mt-2 flex items-baseline gap-2">
            <span className="text-2xl font-bold tracking-tight text-slate-900">
              {returns.filter((r) => r.status === 'REQUESTED').length}
            </span>
            <span className="text-xs text-slate-500">yêu cầu mới</span>
          </div>
        </div>

        <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
          <div className="flex items-center justify-between">
            <span className="text-sm font-medium text-slate-500">Kho chờ kiểm nhận</span>
            <span className="rounded-lg bg-indigo-50 p-2 text-indigo-600">
              <Package size={18} />
            </span>
          </div>
          <div className="mt-2 flex items-baseline gap-2">
            <span className="text-2xl font-bold tracking-tight text-slate-900">
              {returns.filter((r) => r.status === 'AWAITING_RECEIPT' || r.status === 'ARRIVED').length}
            </span>
            <span className="text-xs text-slate-500">đang đợi thủ kho</span>
          </div>
        </div>

        <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
          <div className="flex items-center justify-between">
            <span className="text-sm font-medium text-slate-500">COD chờ xác minh</span>
            <span className="rounded-lg bg-emerald-50 p-2 text-emerald-600">
              <Banknote size={18} />
            </span>
          </div>
          <div className="mt-2 flex items-baseline gap-2">
            <span className="text-2xl font-bold tracking-tight text-slate-900">{codList.length}</span>
            <span className="text-xs text-slate-500">giao dịch nộp tiền</span>
          </div>
        </div>
      </div>

      {/* Main Tabs Navigation */}
      <div className="border-b border-slate-200">
        <nav className="-mb-px flex gap-6" aria-label="Tabs">
          <button
            onClick={() => setActiveTab('incidents')}
            className={`flex items-center gap-2 border-b-2 py-3 text-sm font-semibold transition-colors ${
              activeTab === 'incidents'
                ? 'border-rose-600 text-rose-600'
                : 'border-transparent text-slate-500 hover:border-slate-300 hover:text-slate-700'
            }`}
          >
            <AlertTriangle size={17} />
            <span>Trung tâm Sự cố (Exception Center)</span>
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
            <span>Quản lý Hoàn hàng (RTS Management)</span>
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
            <span>Đối soát COD</span>
            {codList.length > 0 && (
              <span className="rounded-full bg-emerald-100 px-2 py-0.5 text-xs font-bold text-emerald-700">
                {codList.length}
              </span>
            )}
          </button>
        </nav>
      </div>

      {/* ========================================================================= */}
      {/* TAB 1: EXCEPTION CENTER (INCIDENTS) */}
      {/* ========================================================================= */}
      {activeTab === 'incidents' && (
        <div className="space-y-4">
          {/* Filters Bar */}
          <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-slate-200 bg-white p-3">
            <div className="flex flex-wrap items-center gap-3">
              <div className="flex items-center gap-1.5 text-xs font-medium text-slate-500">
                <span>Trạng thái:</span>
                <select
                  value={incidentStatusFilter}
                  onChange={(e) => setIncidentStatusFilter(e.target.value)}
                  className="rounded-md border border-slate-300 bg-white px-2.5 py-1 text-xs text-slate-800 focus:outline-none focus:ring-1 focus:ring-rose-500"
                >
                  <option value="ALL">Tất cả trạng thái</option>
                  <option value="OPEN">Chờ xử lý (OPEN)</option>
                  <option value="IN_PROGRESS">Đang xử lý (IN_PROGRESS)</option>
                  <option value="ESCALATED">Leo thang (ESCALATED)</option>
                  <option value="RESOLVED">Đã giải quyết (RESOLVED)</option>
                </select>
              </div>

              <div className="flex items-center gap-1.5 text-xs font-medium text-slate-500">
                <span>Mức độ:</span>
                <select
                  value={incidentSeverityFilter}
                  onChange={(e) => setIncidentSeverityFilter(e.target.value)}
                  className="rounded-md border border-slate-300 bg-white px-2.5 py-1 text-xs text-slate-800 focus:outline-none focus:ring-1 focus:ring-rose-500"
                >
                  <option value="ALL">Tất cả mức độ</option>
                  <option value="CRITICAL">Khẩn cấp</option>
                  <option value="HIGH">Cao</option>
                  <option value="MEDIUM">Trung bình</option>
                  <option value="LOW">Thấp</option>
                </select>
              </div>
            </div>

            <div className="text-xs text-slate-500">
              Hiển thị <span className="font-semibold text-slate-800">{filteredIncidents.length}</span> sự cố
            </div>
          </div>

          {/* Incidents Table */}
          <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm text-slate-600">
                <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wider text-slate-500">
                  <tr>
                    <th className="px-5 py-3 font-semibold">Mã đơn / Tuyến</th>
                    <th className="px-5 py-3 font-semibold">Loại sự cố</th>
                    <th className="px-5 py-3 font-semibold">Mức độ</th>
                    <th className="px-5 py-3 font-semibold">Khả năng tiếp tục</th>
                    <th className="px-5 py-3 font-semibold">Thời gian</th>
                    <th className="px-5 py-3 font-semibold">Trạng thái</th>
                    <th className="px-5 py-3 text-right font-semibold">Thao tác</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {filteredIncidents.map((inc) => (
                    <tr key={inc.id} className="hover:bg-slate-50/75 transition-colors">
                      <td className="px-5 py-3.5">
                        <div className="font-semibold text-slate-900">
                          {inc.order_code || (inc.order_id ? `#${inc.order_id.slice(0, 8)}` : 'Sự cố tuyến đường')}
                        </div>
                        {inc.dispatch_plan_id && (
                          <div className="text-xs text-slate-500">
                            Tuyến: {inc.dispatch_plan_id.slice(0, 8)}
                          </div>
                        )}
                      </td>
                      <td className="px-5 py-3.5">
                        <div className="font-medium text-slate-800">{inc.incident_type}</div>
                        {inc.notes && (
                          <div className="max-w-xs truncate text-xs text-slate-500" title={inc.notes}>
                            {inc.notes}
                          </div>
                        )}
                      </td>
                      <td className="px-5 py-3.5">{severityBadge(inc.severity)}</td>
                      <td className="px-5 py-3.5">
                        {inc.can_continue_delivery ? (
                          <span className="inline-flex items-center gap-1 text-xs font-medium text-emerald-700">
                            <CheckCircle2 size={13} /> Tiếp tục được
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1 text-xs font-medium text-rose-700">
                            <XCircle size={13} /> Tạm dừng đơn
                          </span>
                        )}
                      </td>
                      <td className="px-5 py-3.5 text-xs text-slate-500">
                        {formatDate(inc.created_at)}
                      </td>
                      <td className="px-5 py-3.5">{incidentStatusBadge(inc.status)}</td>
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
                          Xem & Xử lý
                        </Button>
                      </td>
                    </tr>
                  ))}

                  {filteredIncidents.length === 0 && (
                    <tr>
                      <td colSpan={7} className="px-5 py-12 text-center text-sm text-slate-500">
                        Không có sự cố nào phù hợp với bộ lọc.
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
                <span>Trạng thái hoàn:</span>
                <select
                  value={returnStatusFilter}
                  onChange={(e) => setReturnStatusFilter(e.target.value)}
                  className="rounded-md border border-slate-300 bg-white px-2.5 py-1 text-xs text-slate-800 focus:outline-none focus:ring-1 focus:ring-indigo-500"
                >
                  <option value="ALL">Tất cả trạng thái</option>
                  <option value="REQUESTED">Chờ duyệt (REQUESTED)</option>
                  <option value="IN_EXECUTION">Đang hoàn về (IN_EXECUTION)</option>
                  <option value="AWAITING_RECEIPT">Chờ kiểm nhận (AWAITING_RECEIPT)</option>
                  <option value="DISCREPANCY">Có chênh lệch / Hỏng</option>
                  <option value="COMPLETED">Đã nhận hàng vào kho (COMPLETED)</option>
                </select>
              </div>
            </div>

            <div className="text-xs text-slate-500">
              Hiển thị <span className="font-semibold text-slate-800">{filteredReturns.length}</span> yêu cầu
            </div>
          </div>

          {/* Returns Table */}
          <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm text-slate-600">
                <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wider text-slate-500">
                  <tr>
                    <th className="px-5 py-3 font-semibold">Mã đơn</th>
                    <th className="px-5 py-3 font-semibold">Lý do hoàn</th>
                    <th className="px-5 py-3 font-semibold">Điểm hoàn về</th>
                    <th className="px-5 py-3 font-semibold">Người giữ hàng (Tài xế)</th>
                    <th className="px-5 py-3 font-semibold">Số kiện</th>
                    <th className="px-5 py-3 font-semibold">Trạng thái</th>
                    <th className="px-5 py-3 text-right font-semibold">Thao tác</th>
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
                          Loại: {ret.request_type === 'REDELIVERY' ? 'Giao lại' : 'Hoàn về kho'}
                        </div>
                      </td>
                      <td className="px-5 py-3.5">
                        <div className="max-w-xs text-sm font-medium text-slate-800 truncate" title={ret.reason}>
                          {ret.reason}
                        </div>
                        <div className="text-xs text-slate-400">Yêu cầu lúc: {formatDate(ret.created_at)}</div>
                      </td>
                      <td className="px-5 py-3.5">
                        <div className="font-medium text-slate-800">{ret.return_location_name || 'Kho lấy ban đầu'}</div>
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
                          <span className="text-xs text-slate-400">Đã bàn giao về kho</span>
                        )}
                      </td>
                      <td className="px-5 py-3.5 font-semibold text-slate-800">
                        {ret.package_count} kiện
                      </td>
                      <td className="px-5 py-3.5">{returnStatusBadge(ret.status)}</td>
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
                              Duyệt hoàn
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
                                Nhận hàng
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
                                Báo hỏng/lệch
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
                            Chi tiết
                          </Button>
                        </div>
                      </td>
                    </tr>
                  ))}

                  {filteredReturns.length === 0 && (
                    <tr>
                      <td colSpan={7} className="px-5 py-12 text-center text-sm text-slate-500">
                        Không có yêu cầu hoàn hàng nào.
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
            <h2 className="text-base font-semibold text-slate-900">Đối soát tiền thu hộ (COD)</h2>
            <p className="mt-1 text-sm text-slate-500">
              Danh sách các khoản tiền mặt COD do tài xế thu và đã kê khai nộp về quầy thu ngân / thủ kho.
            </p>

            <div className="mt-4 divide-y divide-slate-100">
              {codList.map((item) => (
                <div key={item.id} className="flex flex-col gap-3 py-4 sm:flex-row sm:items-center sm:justify-between">
                  <div>
                    <div className="flex items-center gap-2">
                      <span className="font-semibold text-slate-900">
                        {item.order_code || `#${item.order_id.slice(0, 8)}`}
                      </span>
                      <Badge className="bg-emerald-50 text-emerald-700 border border-emerald-200">
                        {formatCurrency(item.declared_amount)}
                      </Badge>
                    </div>
                    <div className="mt-1 text-xs text-slate-500">
                      Tài xế: {item.driver_id.slice(0, 8)} · Kê khai lúc: {formatDate(item.declared_at)}
                      {item.notes ? ` · Ghi chú: ${item.notes}` : ''}
                    </div>
                  </div>

                  <Button
                    size="sm"
                    onClick={() => void handleVerifyCod(item.id, item.declared_amount)}
                    isLoading={actionLoading}
                    className="shrink-0 bg-emerald-600 hover:bg-emerald-700 text-white"
                  >
                    <CheckCircle2 size={15} className="mr-1" />
                    Xác nhận đã nhận tiền
                  </Button>
                </div>
              ))}

              {codList.length === 0 && (
                <p className="py-8 text-center text-sm text-slate-500">
                  Không có khoản COD nào đang chờ xác nhận.
                </p>
              )}
            </div>
          </div>
        </div>
      )}

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
        title={`Chi tiết sự cố: ${selectedIncident?.order_code || (selectedIncident?.order_id ? `#${selectedIncident.order_id.slice(0, 8)}` : 'Tuyến đường')}`}
        description="Thông tin sự cố do tài xế hoặc hệ thống báo cáo và các hành động xử lý chuẩn SaaS."
        className="max-w-2xl"
      >
        {selectedIncident && (
          <div className="space-y-5">
            {/* Incident Summary Card */}
            <div className="rounded-lg border border-slate-200 bg-slate-50 p-4 space-y-2.5 text-sm">
              <div className="flex items-center justify-between">
                <span className="font-semibold text-slate-900">{selectedIncident.incident_type}</span>
                <div className="flex gap-2">
                  {severityBadge(selectedIncident.severity)}
                  {incidentStatusBadge(selectedIncident.status)}
                </div>
              </div>

              {selectedIncident.notes && (
                <p className="text-slate-700 bg-white p-2.5 rounded border border-slate-200 text-xs">
                  <span className="font-semibold">Mô tả:</span> {selectedIncident.notes}
                </p>
              )}

              <div className="grid grid-cols-2 gap-2 text-xs text-slate-600 pt-1">
                <div>
                  <span className="text-slate-400">Người báo cáo:</span> {selectedIncident.reported_by.slice(0, 8)}
                </div>
                <div>
                  <span className="text-slate-400">Thời gian:</span> {formatDate(selectedIncident.created_at)}
                </div>
                <div>
                  <span className="text-slate-400">Tài xế phụ trách:</span>{' '}
                  {selectedIncident.driver_id ? selectedIncident.driver_id.slice(0, 8) : 'Chưa gán'}
                </div>
                <div>
                  <span className="text-slate-400">Có thể tiếp tục giao:</span>{' '}
                  {selectedIncident.can_continue_delivery ? 'Có' : 'Không (Tạm dừng đơn)'}
                </div>
              </div>

              {/* Supporting Evidence Images (Cloudinary) */}
              {selectedIncident.evidence_urls && selectedIncident.evidence_urls.length > 0 && (
                <div className="pt-2">
                  <span className="text-xs font-semibold text-slate-700 block mb-1.5">Ảnh bằng chứng hiện trường:</span>
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
                          alt="Bằng chứng sự cố"
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
                <h3 className="text-sm font-semibold text-slate-900">Chọn giải pháp xử lý nghiệp vụ:</h3>

                <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
                  <Button
                    size="sm"
                    variant={resolvingAction === 'CONTINUE_DELIVERY' ? 'default' : 'outline'}
                    onClick={() => setResolvingAction('CONTINUE_DELIVERY')}
                    className="text-xs justify-start h-auto py-2"
                  >
                    <CheckCircle2 size={14} className="mr-1.5 text-emerald-600 shrink-0" />
                    <div>
                      <div className="font-semibold text-left">Tiếp tục giao</div>
                      <div className="text-[10px] text-slate-500 font-normal">Khách gọi lại / đã xử lý</div>
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
                      <div className="font-semibold text-left">Giao lại ca sau</div>
                      <div className="text-[10px] text-slate-500 font-normal">Hẹn lại giờ giao</div>
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
                      <div className="font-semibold text-left">Yêu cầu hoàn hàng</div>
                      <div className="text-[10px] text-slate-500 font-normal">Khách từ chối / hủy đơn</div>
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
                      <div className="text-[10px] text-slate-500 font-normal">Mất hàng / hỏng nặng</div>
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
                      <div className="font-semibold text-left">Đóng sự cố</div>
                      <div className="text-[10px] text-slate-500 font-normal">Không cần thao tác thêm</div>
                    </div>
                  </Button>
                </div>

                {resolvingAction && (
                  <div className="mt-3 space-y-2 rounded-lg border border-indigo-100 bg-indigo-50/50 p-3">
                    <label className="text-xs font-semibold text-slate-800">
                      Ghi chú quyết định xử lý (Bắt buộc):
                    </label>
                    <textarea
                      value={resolutionNotes}
                      onChange={(e) => setResolutionNotes(e.target.value)}
                      rows={2}
                      placeholder="Nhập lý do hoặc chi tiết điều phối..."
                      className="w-full rounded-md border border-slate-300 p-2 text-xs text-slate-900 focus:outline-none focus:ring-1 focus:ring-indigo-500"
                    />
                    <div className="flex justify-end gap-2 pt-1">
                      <Button size="sm" variant="outline" onClick={() => setResolvingAction(null)}>
                        Hủy
                      </Button>
                      <Button
                        size="sm"
                        onClick={() => void handleResolveIncident(resolvingAction)}
                        isLoading={actionLoading}
                        className="bg-indigo-600 text-white hover:bg-indigo-700"
                      >
                        Xác nhận xử lý
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
        title={`Yêu cầu hoàn hàng: ${selectedReturn?.order_code || (selectedReturn?.order_id ? `#${selectedReturn.order_id.slice(0, 8)}` : '')}`}
        description="Theo dõi lộ trình hoàn về kho, xác nhận lưu ký và kiểm nhận hàng hóa."
        className="max-w-2xl"
      >
        {selectedReturn && (
          <div className="space-y-4 text-sm">
            <div className="rounded-lg border border-slate-200 bg-slate-50 p-4 space-y-2">
              <div className="flex items-center justify-between">
                <span className="font-semibold text-slate-900">Lý do: {selectedReturn.reason}</span>
                {returnStatusBadge(selectedReturn.status)}
              </div>

              <div className="grid grid-cols-2 gap-2 text-xs text-slate-600 pt-2 border-t border-slate-200">
                <div>
                  <span className="text-slate-400">Kho hoàn về:</span>{' '}
                  <span className="font-medium text-slate-800">{selectedReturn.return_location_name || 'Kho ban đầu'}</span>
                </div>
                <div>
                  <span className="text-slate-400">Địa chỉ:</span>{' '}
                  <span className="font-medium text-slate-800">{selectedReturn.return_address || 'Địa chỉ kho gửi'}</span>
                </div>
                <div>
                  <span className="text-slate-400">Người đang cầm hàng:</span>{' '}
                  <span className="font-medium text-slate-800">
                    {selectedReturn.current_holder_id ? `Tài xế (${selectedReturn.current_holder_id.slice(0, 8)})` : 'Đã nhập kho'}
                  </span>
                </div>
                <div>
                  <span className="text-slate-400">Số lượng kiện:</span>{' '}
                  <span className="font-semibold text-slate-800">{selectedReturn.package_count} kiện</span>
                </div>
                <div>
                  <span className="text-slate-400">Người tạo yêu cầu:</span> {selectedReturn.requested_by.slice(0, 8)}
                </div>
                <div>
                  <span className="text-slate-400">Thời gian tạo:</span> {formatDate(selectedReturn.created_at)}
                </div>
              </div>

              {/* Discrepancy Alert Banner if reported */}
              {selectedReturn.status === 'DISCREPANCY' && (
                <div className="mt-2 rounded border border-rose-200 bg-rose-50 p-3 text-xs text-rose-800">
                  <div className="font-semibold">⚠️ Hàng hoàn có chênh lệch / hư hỏng:</div>
                  <div className="mt-1">
                    Loại: {selectedReturn.discrepancy_type}{selectedReturn.actual_quantity !== null && selectedReturn.actual_quantity !== undefined ? ` · Thực nhận: ${selectedReturn.actual_quantity} kiện` : ''}
                  </div>
                  {selectedReturn.discrepancy_notes && <div className="mt-0.5">Ghi chú: {selectedReturn.discrepancy_notes}</div>}
                  {selectedReturn.discrepancy_evidence_url && (
                    <div className="mt-2">
                      <a
                        href={selectedReturn.discrepancy_evidence_url}
                        target="_blank"
                        rel="noreferrer"
                        className="text-rose-700 underline font-medium"
                      >
                        Xem ảnh chụp hàng hỏng
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
                  Phê duyệt hoàn hàng
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
                    Có chênh lệch / Hàng hỏng
                  </Button>

                  <Button
                    onClick={() => void handleConfirmReceipt(selectedReturn.id)}
                    isLoading={actionLoading}
                    className="bg-emerald-600 hover:bg-emerald-700 text-white"
                  >
                    <CheckCircle2 size={15} className="mr-1.5" />
                    Xác nhận đã nhận hàng đủ
                  </Button>
                </>
              )}

              <Button variant="outline" onClick={() => setSelectedReturn(null)}>
                Đóng
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
        title="Báo cáo chênh lệch / Hàng hoàn bị hỏng"
        description="Ghi nhận sai lệch số lượng hoặc hàng hóa hư hỏng khi nhận bàn giao từ tài xế."
        className="max-w-lg"
      >
        <div className="space-y-4 text-sm">
          <div>
            <label className="text-xs font-semibold text-slate-700 block mb-1">Loại chênh lệch:</label>
            <select
              value={discrepancyType}
              onChange={(e) => setDiscrepancyType(e.target.value)}
              className="w-full rounded-md border border-slate-300 p-2 text-xs text-slate-900 focus:outline-none focus:ring-1 focus:ring-rose-500"
            >
              <option value="DAMAGED">Hàng hóa bị vỡ / hư hỏng</option>
              <option value="MISSING_ITEMS">Thiếu kiện hàng / thiếu sản phẩm</option>
              <option value="WRONG_ITEMS">Sai sản phẩm hoàn về</option>
              <option value="REFUSED_BY_RECIPIENT">Bao bì rách nát / khách từ chối</option>
              <option value="OTHER">Lý do khác</option>
            </select>
          </div>

          <div>
            <label className="text-xs font-semibold text-slate-700 block mb-1">
              Số lượng kiện thực nhận (Gốc: {selectedReturn?.package_count || 1} kiện):
            </label>
            <input
              type="number"
              min={0}
              max={selectedReturn?.package_count || 10}
              value={actualQuantity}
              onChange={(e) => setActualQuantity(parseInt(e.target.value) || 0)}
              className="w-full rounded-md border border-slate-300 p-2 text-xs text-slate-900 focus:outline-none focus:ring-1 focus:ring-rose-500"
            />
          </div>

          <div>
            <label className="text-xs font-semibold text-slate-700 block mb-1">Ghi chú chi tiết:</label>
            <textarea
              rows={2}
              value={discrepancyNotes}
              onChange={(e) => setDiscrepancyNotes(e.target.value)}
              placeholder="Mô tả cụ thể lý do chênh lệch hoặc tình trạng hàng (VD: Hộp rách vỡ, thiếu 1 sản phẩm bên trong...)"
              className="w-full rounded-md border border-slate-300 p-2 text-xs text-slate-900 focus:outline-none focus:ring-1 focus:ring-rose-500"
            />
          </div>

          {/* Cloudinary Upload Field */}
          <div>
            <label className="text-xs font-semibold text-slate-700 block mb-1">
              Ảnh chụp bằng chứng (Tải lên Cloudinary):
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
                <span className="text-xs text-emerald-600 font-medium">✓ Đã chọn file</span>
              )}
            </div>
          </div>

          <div className="flex justify-end gap-2 border-t border-slate-200 pt-4">
            <Button variant="outline" size="sm" onClick={() => setDiscrepancyModalOpen(false)}>
              Hủy
            </Button>
            <Button
              size="sm"
              onClick={() => void handleSubmitDiscrepancy()}
              isLoading={actionLoading}
              className="bg-rose-600 hover:bg-rose-700 text-white"
            >
              Gửi báo cáo chênh lệch
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
