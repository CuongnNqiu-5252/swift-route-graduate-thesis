'use client';

import { useCallback, useEffect, useState } from 'react';
import { AlertTriangle, Banknote, Check, RotateCcw } from 'lucide-react';
import api from '@/lib/api';
import { Button } from '@/components/ui/button';

type Incident = { id: string; orderId: string; orderCode: string | null; incidentType: string; notes: string | null };
type ReturnRequest = { id: string; orderId: string; orderCode: string | null; requestType: string; reason: string; status: 'REQUESTED' | 'IN_EXECUTION' | 'AWAITING_RECEIPT' | 'EXECUTION_FAILED'; driverId: string | null };
type CodCollection = { id: string; orderId: string; orderCode: string | null; declaredAmount: number; notes: string | null };
type ReviewQueue = { incidents: Incident[]; returns: ReturnRequest[]; codCollections: CodCollection[] };
const emptyQueue: ReviewQueue = { incidents: [], returns: [], codCollections: [] };
const orderLabel = (code: string | null, id: string) => code || `#${id.slice(0, 8)}`;
const formatAmount = (amount: number) => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(amount);

// Order Service serializes DTOs with SNAKE_CASE; normalize once at the API boundary.
// eslint-disable-next-line @typescript-eslint/no-explicit-any
function normalizeQueue(raw: any): ReviewQueue {
  return {
    incidents: (raw?.incidents ?? []).map((item: Record<string, unknown>) => ({ id: String(item.id ?? ''), orderId: String(item.order_id ?? item.orderId ?? ''), orderCode: (item.order_code ?? item.orderCode ?? null) as string | null, incidentType: String(item.incident_type ?? item.incidentType ?? ''), notes: (item.notes ?? null) as string | null })),
    returns: (raw?.returns ?? []).map((item: Record<string, unknown>) => ({ id: String(item.id ?? ''), orderId: String(item.order_id ?? item.orderId ?? ''), orderCode: (item.order_code ?? item.orderCode ?? null) as string | null, requestType: String(item.request_type ?? item.requestType ?? ''), reason: String(item.reason ?? ''), status: String(item.status ?? 'REQUESTED') as ReturnRequest['status'], driverId: (item.driver_id ?? item.driverId ?? null) as string | null })),
    codCollections: (raw?.cod_collections ?? raw?.codCollections ?? []).map((item: Record<string, unknown>) => ({ id: String(item.id ?? ''), orderId: String(item.order_id ?? item.orderId ?? ''), orderCode: (item.order_code ?? item.orderCode ?? null) as string | null, declaredAmount: Number(item.declared_amount ?? item.declaredAmount ?? 0), notes: (item.notes ?? null) as string | null })),
  };
}

export default function OperationsReviewPage() {
  const [queue, setQueue] = useState<ReviewQueue>(emptyQueue);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [workingId, setWorkingId] = useState<string | null>(null);
  const loadQueue = useCallback(async () => { try { const response = await api.get<unknown>('/operations/review'); setQueue(normalizeQueue(response.data)); } catch (cause) { setError(cause instanceof Error ? cause.message : 'Không thể tải hàng đợi vận hành.'); } finally { setLoading(false); } }, []);
  // The request settles asynchronously; the state updates in loadQueue never occur during this effect's synchronous phase.
  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(() => { void loadQueue(); }, [loadQueue]);
  const complete = async (id: string, path: string) => { setWorkingId(id); setError(null); try { await api.patch(path, {}); await loadQueue(); } catch (cause) { setError(cause instanceof Error ? cause.message : 'Không thể hoàn tất thao tác.'); } finally { setWorkingId(null); } };

  return <div className="mx-auto max-w-6xl space-y-8 pb-10"><header className="flex flex-col justify-between gap-4 border-b border-slate-200 pb-5 sm:flex-row sm:items-end"><div><h1 className="text-2xl font-bold tracking-tight text-slate-950">Điều hành ngoại lệ & COD</h1><p className="mt-1 max-w-2xl text-sm leading-6 text-slate-600">Chỉ các việc còn chờ quyết định trong tenant hiện tại. Hoàn tất thao tác sẽ xóa việc khỏi hàng đợi.</p></div><Button variant="outline" onClick={() => { setLoading(true); setError(null); void loadQueue(); }} isLoading={loading}>Làm mới</Button></header>{error && <div role="alert" className="rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800">{error}</div>}<section aria-labelledby="incident-heading" className="overflow-hidden rounded-xl border border-slate-200 bg-white"><QueueHeading id="incident-heading" icon={<AlertTriangle size={18} />} title="Sự cố cần xử lý" count={queue.incidents.length} tone="text-rose-700" /><div className="divide-y divide-slate-100">{queue.incidents.map((item) => <IncidentRow key={item.id} item={item} loading={workingId === item.id} onResolve={() => void complete(item.id, `/operations/incidents/${item.id}/resolve`)} onCreated={loadQueue} />)}{!loading && queue.incidents.length === 0 && <EmptyRow message="Không có sự cố đang chờ xử lý." />}</div></section><section aria-labelledby="return-heading" className="overflow-hidden rounded-xl border border-slate-200 bg-white"><QueueHeading id="return-heading" icon={<RotateCcw size={18} />} title="Giao lại / hoàn về" count={queue.returns.length} tone="text-amber-700" /><div className="divide-y divide-slate-100">{queue.returns.map((item) => <ReturnQueueRow key={item.id} item={item} loading={workingId === item.id} onAction={(path) => void complete(item.id, path)} />)}{!loading && queue.returns.length === 0 && <EmptyRow message="Không có yêu cầu giao lại hoặc hoàn về." />}</div></section><section aria-labelledby="cod-heading" className="overflow-hidden rounded-xl border border-slate-200 bg-white"><QueueHeading id="cod-heading" icon={<Banknote size={18} />} title="COD chờ xác minh" count={queue.codCollections.length} tone="text-emerald-700" /><div className="divide-y divide-slate-100">{queue.codCollections.map((item) => <QueueRow key={item.id} title={orderLabel(item.orderCode, item.orderId)} detail={`${formatAmount(item.declaredAmount)} đã bàn giao${item.notes ? ` · ${item.notes}` : ''}`} action="Xác minh" loading={workingId === item.id} onAction={() => void complete(item.id, `/operations/cod-collections/${item.id}/verify`)} />)}{!loading && queue.codCollections.length === 0 && <EmptyRow message="Không có COD đang chờ xác minh." />}</div></section></div>;
}

function QueueHeading({ id, icon, title, count, tone }: { id: string; icon: React.ReactNode; title: string; count: number; tone: string }) { return <div className="flex items-center justify-between bg-slate-50 px-5 py-4"><h2 id={id} className={`flex items-center gap-2 text-sm font-semibold ${tone}`}>{icon}{title}</h2><span className="rounded-full bg-white px-2.5 py-1 text-xs font-semibold tabular-nums text-slate-700">{count}</span></div>; }
function IncidentRow({ item, loading, onResolve, onCreated }: { item: Incident; loading: boolean; onResolve: () => void; onCreated: () => Promise<void> }) { const [open, setOpen] = useState(false); const [type, setType] = useState('REDELIVERY'); const [reason, setReason] = useState(''); const [saving, setSaving] = useState(false); const submit = async () => { if (!reason.trim()) return; setSaving(true); try { await api.post(`/operations/orders/${item.orderId}/returns`, { request_type: type, reason: reason.trim() }); setOpen(false); setReason(''); await onCreated(); } finally { setSaving(false); } }; return <div className="px-5 py-4"><div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between"><div><p className="font-medium text-slate-900">{orderLabel(item.orderCode, item.orderId)}</p><p className="mt-1 text-sm text-slate-600">{item.incidentType}{item.notes ? ` · ${item.notes}` : ''}</p></div><div className="flex gap-2"><Button size="sm" variant="outline" onClick={() => setOpen(!open)}>Tạo yêu cầu</Button><Button size="sm" isLoading={loading} onClick={onResolve}><Check size={15} />Đã xử lý</Button></div></div>{open && <div className="mt-4 grid gap-3 border-t border-slate-100 pt-4 sm:grid-cols-[180px_1fr_auto]"><select aria-label="Loại yêu cầu" value={type} onChange={(event) => setType(event.target.value)} className="rounded-lg border border-slate-300 px-3 py-2 text-sm"><option value="REDELIVERY">Giao lại</option><option value="RETURN_TO_ORIGIN">Hoàn về nơi gửi</option></select><input value={reason} onChange={(event) => setReason(event.target.value)} placeholder="Lý do bắt buộc" className="rounded-lg border border-slate-300 px-3 py-2 text-sm" /><Button size="sm" onClick={() => void submit()} isLoading={saving} disabled={!reason.trim()}>Tạo yêu cầu</Button></div>}</div>; }
function ReturnQueueRow({ item, loading, onAction }: { item: ReturnRequest; loading: boolean; onAction: (path: string) => void }) {
  const [packages, setPackages] = useState<Array<{ code: string; status: string; custodian: string | null }> | null>(null);
  const [loadingPackages, setLoadingPackages] = useState(false);
  const awaitingReceipt = item.status === 'AWAITING_RECEIPT';
  const action = awaitingReceipt ? 'Xác nhận đã nhận hàng' : item.status === 'REQUESTED' ? 'Phê duyệt' : null;
  const detail = `${item.requestType === 'REDELIVERY' ? 'Giao lại' : 'Hoàn về'} · ${item.reason} · ${awaitingReceipt ? 'Chờ kho kiểm nhận' : item.status === 'REQUESTED' ? 'Chờ phê duyệt' : 'Cần điều phối xử lý'}`;
  const inspectPackages = async () => {
    if (packages) return setPackages(null);
    setLoadingPackages(true);
    try {
      const response = await api.get<unknown>(`/custody/orders/${item.orderId}/packages`);
      const raw = Array.isArray(response.data) ? response.data as Array<Record<string, unknown>> : [];
      setPackages(raw.map((unit) => ({ code: String(unit.package_code ?? unit.packageCode ?? ''), status: String(unit.status ?? ''), custodian: (unit.current_custodian_id ?? unit.currentCustodianId ?? null) as string | null })));
    } finally { setLoadingPackages(false); }
  };
  return <div className="px-5 py-4"><div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between"><div className="min-w-0"><p className="font-medium text-slate-900">{orderLabel(item.orderCode, item.orderId)}</p><p className="mt-1 break-words text-sm text-slate-600">{detail}</p></div><div className="flex shrink-0 gap-2"><Button size="sm" variant="outline" isLoading={loadingPackages} onClick={() => void inspectPackages()}>{packages ? 'Ẩn kiện' : 'Xem kiện'}</Button>{action && <Button size="sm" onClick={() => onAction(awaitingReceipt ? `/operations/returns/${item.id}/confirm-receipt` : `/operations/returns/${item.id}/approve`)} isLoading={loading}><Check size={15} />{action}</Button>}</div></div>{packages && <div className="mt-3 grid gap-2 border-t border-slate-100 pt-3 sm:grid-cols-2">{packages.length === 0 ? <p className="text-sm text-slate-500">Chưa phát sinh kiện trong chuỗi lưu ký.</p> : packages.map((unit) => <div key={unit.code} className="rounded-lg bg-slate-50 px-3 py-2 text-xs"><p className="font-semibold text-slate-800">{unit.code}</p><p className="mt-1 text-slate-500">{unit.status}{unit.custodian ? ` · giữ bởi ${unit.custodian.slice(0, 8)}` : ' · đã giải phóng'}</p></div>)}</div>}</div>;
}
function QueueRow({ title, detail, action, loading, onAction }: { title: string; detail: string; action: string; loading: boolean; onAction: () => void }) { return <div className="flex flex-col gap-3 px-5 py-4 sm:flex-row sm:items-center sm:justify-between"><div className="min-w-0"><p className="font-medium text-slate-900">{title}</p><p className="mt-1 break-words text-sm text-slate-600">{detail}</p></div><Button size="sm" onClick={onAction} isLoading={loading} className="shrink-0"><Check size={15} />{action}</Button></div>; }
function EmptyRow({ message }: { message: string }) { return <p className="px-5 py-6 text-sm text-slate-500">{message}</p>; }
