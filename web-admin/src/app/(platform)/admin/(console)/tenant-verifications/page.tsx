'use client';

import { useCallback, useEffect, useState } from 'react';
import {
  AlertTriangle, Building2, Check, CheckCircle2, ChevronRight, Clock3,
  FileCheck2, MapPin, Phone, ShieldCheck, Store, Truck, UserRoundCheck, X, XCircle,
} from 'lucide-react';
import { useAuth } from '@/application/contexts/auth-context';
import { PageResult, tenantWorkflow, Verification, VerificationDetail } from '@/infrastructure/repositories/tenant-workflow.repository';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { isSystemAdmin } from '@/lib/portal-access';

const STATUS_LABEL: Record<string, string> = {
  SUBMITTED: 'Mới gửi', UNDER_REVIEW: 'Đang xem xét', CHANGES_REQUIRED: 'Cần bổ sung', APPROVED: 'Đã duyệt', REJECTED: 'Từ chối',
};

const ACTION_LABEL: Record<string, string> = {
  VERIFICATION_SUBMITTED: 'Doanh nghiệp đã gửi hồ sơ',
  VERIFICATION_UNDER_REVIEW: 'Hồ sơ được nhận xem xét',
  VERIFICATION_APPROVED: 'Hồ sơ đã được phê duyệt',
  VERIFICATION_CHANGES_REQUIRED: 'Đã yêu cầu bổ sung hồ sơ',
  VERIFICATION_REJECTED: 'Hồ sơ đã bị từ chối',
};

const BUSINESS_TYPE_LABEL: Record<string, string> = {
  RESTAURANT: 'Nhà hàng / F&B', RETAIL: 'Bán lẻ', PHARMACY: 'Nhà thuốc', ECOMMERCE: 'Thương mại điện tử', OTHER: 'Khác',
};

const DELIVERY_MODE_LABEL: Record<string, string> = {
  OWN_FLEET: 'Đội xe nội bộ', SHARED_NETWORK: 'Mạng lưới tài xế dùng chung', HYBRID: 'Kết hợp nội bộ và dùng chung',
};

const DECISIONS = [
  { value: 'UNDER_REVIEW', label: 'Nhận xem xét', description: 'Ghi nhận hồ sơ đang được xử lý', icon: Clock3 },
  { value: 'APPROVED', label: 'Phê duyệt', description: 'Cho phép doanh nghiệp tiếp tục kích hoạt', icon: CheckCircle2 },
  { value: 'CHANGES_REQUIRED', label: 'Yêu cầu bổ sung', description: 'Trả hồ sơ kèm nội dung cần chỉnh', icon: AlertTriangle },
  { value: 'REJECTED', label: 'Từ chối', description: 'Kết thúc hồ sơ với lý do rõ ràng', icon: XCircle },
] as const;

type Snapshot = Record<string, unknown>;
type PickupPoint = Record<string, unknown>;

function formatTime(value?: string) {
  if (!value) return '—';
  return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value));
}

function parseSnapshot(value?: string): Snapshot | null {
  if (!value) return null;
  try {
    const parsed = JSON.parse(value);
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed as Snapshot : null;
  } catch { return null; }
}

function parseDetails(value?: string): Record<string, unknown> {
  if (!value) return {};
  try {
    const parsed = JSON.parse(value);
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed as Record<string, unknown> : {};
  } catch { return {}; }
}

function stringValue(value: unknown, fallback = 'Chưa cung cấp') {
  return typeof value === 'string' && value.trim() ? value : fallback;
}

function recordValue(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {};
}

function statusTone(status: string) {
  if (status === 'APPROVED') return 'bg-emerald-50 text-emerald-700 ring-emerald-200';
  if (status === 'REJECTED') return 'bg-rose-50 text-rose-700 ring-rose-200';
  if (status === 'CHANGES_REQUIRED') return 'bg-amber-50 text-amber-800 ring-amber-200';
  if (status === 'UNDER_REVIEW') return 'bg-blue-50 text-blue-700 ring-blue-200';
  return 'bg-slate-100 text-slate-700 ring-slate-200';
}

function SettingRow({ enabled, label, detail }: { enabled: boolean; label: string; detail: string }) {
  return <div className="flex gap-3 py-3 first:pt-0 last:pb-0">
    <span className={`mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full ${enabled ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-100 text-slate-400'}`}>
      {enabled ? <Check className="h-3.5 w-3.5" aria-hidden="true" /> : <X className="h-3.5 w-3.5" aria-hidden="true" />}
    </span>
    <div><p className="text-sm font-medium text-slate-900">{label}</p><p className="mt-0.5 text-xs leading-5 text-slate-500">{detail}</p></div>
  </div>;
}

export default function TenantVerificationsPage() {
  const { user } = useAuth();
  const allowed = isSystemAdmin(user?.role);
  const [queue, setQueue] = useState<PageResult<Verification> | null>(null);
  const [status, setStatus] = useState('SUBMITTED');
  const [searchDraft, setSearchDraft] = useState('');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [detail, setDetail] = useState<VerificationDetail | null>(null);
  const [decision, setDecision] = useState('');
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    if (!allowed) return;
    setBusy(true); setError('');
    try { setQueue(await tenantWorkflow.reviewQueue(status, search, page)); }
    catch (e) { setError(e instanceof Error ? e.message : 'Không tải được hàng đợi xét duyệt'); }
    finally { setBusy(false); }
  }, [allowed, page, search, status]);

  const closeDetail = useCallback(() => {
    setDetail(null); setDecision(''); setReason(''); setError('');
  }, []);

  useEffect(() => {
    const frame = window.requestAnimationFrame(() => { void load(); });
    return () => window.cancelAnimationFrame(frame);
  }, [load]);

  useEffect(() => {
    if (!detail) return;
    const handleKeyDown = (event: KeyboardEvent) => { if (event.key === 'Escape' && !busy) closeDetail(); };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [busy, closeDetail, detail]);

  async function openDetail(requestId: string) {
    setBusy(true); setError('');
    try { setDetail(await tenantWorkflow.reviewDetail(requestId)); }
    catch (e) { setError(e instanceof Error ? e.message : 'Không tải được chi tiết hồ sơ'); }
    finally { setBusy(false); }
  }

  async function confirmDecision() {
    if (!detail || !decision) return;
    setBusy(true); setError('');
    try {
      await tenantWorkflow.review(detail.request, decision, reason.trim());
      closeDetail();
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Không thể ghi nhận quyết định');
      try { setDetail(await tenantWorkflow.reviewDetail(detail.request.request_id)); } catch { /* Preserve the primary decision error. */ }
    } finally { setBusy(false); }
  }

  if (!allowed) return <p className="rounded-lg bg-red-50 p-4 text-sm text-red-700">Chỉ System Admin có quyền xét duyệt doanh nghiệp.</p>;

  const items = queue?.content ?? [];
  const selectedSnapshot = parseSnapshot(detail?.request.snapshot);
  const deliverySettings = recordValue(selectedSnapshot?.delivery_settings);
  const pickupPoints = Array.isArray(selectedSnapshot?.pickup_points) ? selectedSnapshot.pickup_points as PickupPoint[] : [];
  const requiresReason = decision === 'REJECTED' || decision === 'CHANGES_REQUIRED';
  const actionButtonLabel = decision === 'APPROVED' ? 'Phê duyệt doanh nghiệp' : decision === 'CHANGES_REQUIRED' ? 'Gửi yêu cầu bổ sung' : decision === 'REJECTED' ? 'Từ chối hồ sơ' : 'Nhận xử lý hồ sơ';

  return <div className="space-y-5">
    <div className="flex flex-wrap items-end justify-between gap-4">
      <div><h1 className="text-2xl font-semibold text-gray-950">Xét duyệt doanh nghiệp</h1><p className="mt-1 text-sm text-gray-600">Ưu tiên hồ sơ mới, kiểm tra thông tin vận hành và ghi nhận quyết định có lịch sử.</p></div>
      <Button variant="outline" onClick={load} isLoading={busy}>Làm mới</Button>
    </div>

    {error && !detail && <p role="alert" className="rounded-lg bg-red-50 p-3 text-sm text-red-700">{error}</p>}

    <div className="grid gap-3 md:grid-cols-[220px_minmax(0,1fr)_auto]">
      <label className="text-sm font-medium text-gray-800">Trạng thái<select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }} className="mt-1 min-h-11 w-full rounded-lg border border-gray-300 bg-white px-3"><option value="">Tất cả</option>{Object.entries(STATUS_LABEL).map(([value, label]) => <option value={value} key={value}>{label}</option>)}</select></label>
      <Input label="Tìm theo tên doanh nghiệp" value={searchDraft} onChange={(e) => setSearchDraft(e.target.value)} placeholder="Nhập tên doanh nghiệp" />
      <Button className="md:mb-px" onClick={() => { setSearch(searchDraft.trim()); setPage(0); }}>Tìm kiếm</Button>
    </div>

    <div className="overflow-hidden rounded-xl border border-gray-200 bg-white">
      <div className="grid grid-cols-[minmax(0,1fr)_150px_160px_110px] gap-4 border-b border-gray-200 px-4 py-3 text-xs font-semibold uppercase tracking-wide text-gray-500 max-md:hidden"><span>Doanh nghiệp</span><span>Trạng thái</span><span>Ngày gửi</span><span className="text-right">Thao tác</span></div>
      {items.length === 0 && !busy && <div className="p-8 text-center"><p className="font-medium text-gray-900">Không có hồ sơ phù hợp</p><p className="mt-1 text-sm text-gray-500">Thử đổi trạng thái hoặc xóa nội dung tìm kiếm.</p></div>}
      <div className="divide-y divide-gray-100">{items.map((item) => <div key={item.request_id} className="grid items-center gap-3 px-4 py-4 md:grid-cols-[minmax(0,1fr)_150px_160px_110px] md:gap-4"><div className="min-w-0"><p className="truncate font-medium text-gray-950">{item.business_name || 'Doanh nghiệp chưa đặt tên'}</p><p className="mt-1 truncate text-xs text-gray-500">{item.tenant_id}</p></div><span className={`w-fit rounded-full px-2.5 py-1 text-xs font-semibold ring-1 ring-inset ${statusTone(item.status)}`}>{STATUS_LABEL[item.status] || item.status}</span><span className="text-sm text-gray-600">{formatTime(item.submitted_at)}</span><Button variant="outline" onClick={() => openDetail(item.request_id)}>Mở hồ sơ</Button></div>)}</div>
    </div>

    {queue && queue.total_pages > 1 && <div className="flex items-center justify-between text-sm text-gray-600"><span>{queue.total_elements} hồ sơ · Trang {queue.page_number + 1}/{queue.total_pages}</span><div className="flex gap-2"><Button variant="outline" disabled={queue.is_first} onClick={() => setPage((value) => value - 1)}>Trước</Button><Button variant="outline" disabled={queue.is_last} onClick={() => setPage((value) => value + 1)}>Sau</Button></div></div>}

    {detail && <div role="dialog" aria-modal="true" aria-labelledby="verification-detail-title" className="fixed inset-0 z-50 overflow-y-auto bg-slate-950/55 p-0 sm:p-4">
      <div className="mx-auto min-h-full max-w-6xl bg-white shadow-2xl sm:my-4 sm:min-h-0 sm:rounded-2xl">
        <header className="sticky top-0 z-20 flex items-start justify-between gap-4 border-b border-slate-200 bg-white px-5 py-4 sm:rounded-t-2xl sm:px-7 sm:py-5">
          <div className="min-w-0"><div className="flex flex-wrap items-center gap-2"><h2 id="verification-detail-title" className="truncate text-xl font-semibold tracking-tight text-slate-950 sm:text-2xl">{detail.tenant.business_name}</h2><span className={`rounded-full px-2.5 py-1 text-xs font-semibold ring-1 ring-inset ${statusTone(detail.request.status)}`}>{STATUS_LABEL[detail.request.status] || detail.request.status}</span></div><p className="mt-1.5 text-sm text-slate-500">Gửi lúc {formatTime(detail.request.submitted_at)} · Hồ sơ #{detail.request.request_id.slice(0, 8)}</p></div>
          <button type="button" onClick={closeDetail} disabled={busy} aria-label="Đóng hồ sơ" className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg text-slate-500 transition-colors hover:bg-slate-100 hover:text-slate-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 disabled:opacity-50"><X className="h-5 w-5" aria-hidden="true" /></button>
        </header>

        <div className="grid lg:grid-cols-[minmax(0,1fr)_360px]">
          <main className="min-w-0 space-y-8 px-5 py-6 sm:px-7 sm:py-8">
            {error && <div role="alert" className="flex gap-3 rounded-xl bg-rose-50 p-4 text-sm text-rose-800"><AlertTriangle className="mt-0.5 h-5 w-5 shrink-0" aria-hidden="true" /><div><p className="font-semibold">Chưa thể ghi nhận quyết định</p><p className="mt-1">{error}</p></div></div>}
            {!selectedSnapshot ? <div className="rounded-xl bg-amber-50 p-5 text-sm text-amber-900"><p className="font-semibold">Không đọc được dữ liệu hồ sơ</p><p className="mt-1">Snapshot đã nộp không đúng định dạng. Chưa nên đưa ra quyết định cho tới khi dữ liệu được kiểm tra.</p></div> : <>
              <section aria-labelledby="business-information-title">
                <div className="flex items-center gap-2"><Building2 className="h-5 w-5 text-blue-600" aria-hidden="true" /><h3 id="business-information-title" className="text-base font-semibold text-slate-950">Thông tin doanh nghiệp</h3></div>
                <dl className="mt-4 grid gap-x-8 gap-y-5 sm:grid-cols-2">
                  <div><dt className="text-xs font-medium text-slate-500">Tên đăng ký</dt><dd className="mt-1 text-sm font-medium text-slate-950">{stringValue(selectedSnapshot.business_name)}</dd></div>
                  <div><dt className="text-xs font-medium text-slate-500">Loại hình kinh doanh</dt><dd className="mt-1 text-sm font-medium text-slate-950">{BUSINESS_TYPE_LABEL[stringValue(selectedSnapshot.business_type, '')] || stringValue(selectedSnapshot.business_type)}</dd></div>
                  <div><dt className="text-xs font-medium text-slate-500">Số điện thoại liên hệ</dt><dd className="mt-1 flex items-center gap-2 text-sm font-medium text-slate-950"><Phone className="h-4 w-4 text-slate-400" aria-hidden="true" />{stringValue(selectedSnapshot.contact_phone)}</dd></div>
                  <div><dt className="text-xs font-medium text-slate-500">Quốc gia hoạt động</dt><dd className="mt-1 text-sm font-medium text-slate-950">{selectedSnapshot.country_code === 'VN' ? 'Việt Nam' : stringValue(selectedSnapshot.country_code)}</dd></div>
                  <div className="sm:col-span-2"><dt className="text-xs font-medium text-slate-500">Địa chỉ doanh nghiệp</dt><dd className="mt-1 flex items-start gap-2 text-sm font-medium leading-6 text-slate-950"><MapPin className="mt-1 h-4 w-4 shrink-0 text-slate-400" aria-hidden="true" />{stringValue(selectedSnapshot.address)}</dd></div>
                </dl>
              </section>

              <section aria-labelledby="operations-title" className="border-t border-slate-200 pt-7">
                <div className="flex items-center gap-2"><Truck className="h-5 w-5 text-blue-600" aria-hidden="true" /><h3 id="operations-title" className="text-base font-semibold text-slate-950">Mô hình vận hành</h3></div>
                <div className="mt-4 grid gap-6 md:grid-cols-[minmax(0,0.8fr)_minmax(0,1.2fr)]">
                  <div className="rounded-xl bg-slate-50 p-4"><p className="text-xs font-medium text-slate-500">Phương thức giao hàng</p><p className="mt-2 text-base font-semibold text-slate-950">{DELIVERY_MODE_LABEL[stringValue(selectedSnapshot.delivery_mode, '')] || stringValue(selectedSnapshot.delivery_mode)}</p><p className="mt-2 text-xs leading-5 text-slate-500">Ưu tiên điều phối: {deliverySettings.service_priority === 'BALANCED' ? 'Cân bằng thời gian và quãng đường' : stringValue(deliverySettings.service_priority)}</p></div>
                  <div className="divide-y divide-slate-100"><SettingRow enabled={deliverySettings.preparation_enabled === true} label="Chuẩn bị đơn trước khi lấy" detail={deliverySettings.preparation_enabled === true ? `Thời gian chuẩn bị dự kiến ${Number(deliverySettings.preparation_minutes) || 0} phút` : 'Không thiết lập thời gian chuẩn bị'} /><SettingRow enabled={deliverySettings.delivery_verification_required === true} label="Xác nhận khi giao hàng" detail="Yêu cầu tài xế ghi nhận bằng chứng hoàn tất giao hàng" /><SettingRow enabled={deliverySettings.sensitive_handling === true} label="Xử lý hàng nhạy cảm" detail="Áp dụng lưu ý đặc biệt trong quá trình giao nhận" /><SettingRow enabled={deliverySettings.routing_approval_required === true} label="Duyệt tuyến đường thủ công" detail="Điều phối viên cần xác nhận tuyến trước khi thực hiện" /></div>
                </div>
              </section>

              <section aria-labelledby="pickup-points-title" className="border-t border-slate-200 pt-7">
                <div className="flex items-center justify-between gap-3"><div className="flex items-center gap-2"><Store className="h-5 w-5 text-blue-600" aria-hidden="true" /><h3 id="pickup-points-title" className="text-base font-semibold text-slate-950">Điểm lấy hàng</h3></div><span className="text-xs font-medium text-slate-500">{pickupPoints.length} địa điểm</span></div>
                {pickupPoints.length === 0 ? <div className="mt-4 flex gap-3 rounded-xl bg-amber-50 p-4 text-sm text-amber-900"><AlertTriangle className="mt-0.5 h-5 w-5 shrink-0" aria-hidden="true" /><div><p className="font-semibold">Chưa có điểm lấy hàng</p><p className="mt-1 leading-5">Doanh nghiệp cần bổ sung ít nhất một địa điểm hoạt động trước khi sẵn sàng vận hành.</p></div></div> : <div className="mt-4 divide-y divide-slate-100 rounded-xl border border-slate-200">{pickupPoints.map((point, index) => <div key={stringValue(point.id, String(index))} className="flex items-start gap-3 p-4"><span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-blue-50 text-blue-700"><MapPin className="h-4 w-4" aria-hidden="true" /></span><div className="min-w-0 flex-1"><div className="flex flex-wrap items-center gap-2"><p className="font-medium text-slate-950">{stringValue(point.name, `Điểm lấy hàng ${index + 1}`)}</p>{point.is_default === true && <span className="rounded-full bg-blue-50 px-2 py-0.5 text-xs font-semibold text-blue-700">Mặc định</span>}</div><p className="mt-1 text-sm leading-5 text-slate-600">{stringValue(point.address)}</p>{Boolean(point.contact_name || point.contact_phone) && <p className="mt-1 text-xs text-slate-500">Liên hệ: {[point.contact_name, point.contact_phone].filter(Boolean).join(' · ')}</p>}</div></div>)}</div>}
              </section>
              <p className="border-t border-slate-200 pt-4 text-xs text-slate-400">Dữ liệu được giữ nguyên theo thời điểm gửi · Phiên bản hồ sơ {String(selectedSnapshot.schema_version ?? '—')}</p>
            </>}
          </main>

          <aside className="border-t border-slate-200 bg-slate-50/70 lg:border-l lg:border-t-0">
            <div className="space-y-7 px-5 py-6 sm:px-7 lg:sticky lg:top-[89px] lg:max-h-[calc(100vh-120px)] lg:overflow-y-auto">
              <section aria-labelledby="timeline-title"><div className="flex items-center gap-2"><FileCheck2 className="h-5 w-5 text-slate-500" aria-hidden="true" /><h3 id="timeline-title" className="font-semibold text-slate-950">Lịch sử xử lý</h3></div><div className="mt-4">{detail.timeline.length === 0 ? <p className="text-sm text-slate-500">Chưa có hoạt động xét duyệt.</p> : detail.timeline.map((event, index) => {
                const eventReason = stringValue(parseDetails(event.details).reason, '');
                return <div key={`${event.action}-${event.created_at}-${index}`} className="relative flex gap-3 pb-5 last:pb-0">{index < detail.timeline.length - 1 && <span className="absolute left-[7px] top-5 h-[calc(100%-12px)] w-px bg-slate-200" aria-hidden="true" />}<span className={`relative mt-1 h-4 w-4 shrink-0 rounded-full border-2 ${index === 0 ? 'border-blue-600 bg-blue-100' : 'border-slate-300 bg-white'}`} aria-hidden="true" /><div className="min-w-0"><p className="text-sm font-medium text-slate-900">{ACTION_LABEL[event.action] || event.action.replaceAll('_', ' ')}</p><p className="mt-1 text-xs text-slate-500">{formatTime(event.created_at)}</p>{eventReason && <p className="mt-2 rounded-lg bg-white p-2.5 text-xs leading-5 text-slate-600 ring-1 ring-slate-200">{eventReason}</p>}</div></div>;
              })}</div></section>

              {['SUBMITTED', 'UNDER_REVIEW'].includes(detail.request.status) ? <section aria-labelledby="decision-title" className="border-t border-slate-200 pt-6">
                <div className="flex items-center gap-2"><UserRoundCheck className="h-5 w-5 text-slate-500" aria-hidden="true" /><h3 id="decision-title" className="font-semibold text-slate-950">Quyết định xét duyệt</h3></div><p className="mt-1 text-xs leading-5 text-slate-500">Chọn một kết quả sau khi đã đối chiếu toàn bộ thông tin.</p>
                <div className="mt-4 space-y-2">{DECISIONS.map((item) => {
                  const Icon = item.icon; const selected = decision === item.value;
                  return <button key={item.value} type="button" aria-pressed={selected} onClick={() => setDecision(item.value)} className={`flex w-full items-center gap-3 rounded-xl p-3 text-left transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 ${selected ? 'bg-slate-900 text-white' : 'bg-white text-slate-900 ring-1 ring-slate-200 hover:bg-slate-50'}`}><Icon className={`h-5 w-5 shrink-0 ${selected ? 'text-white' : item.value === 'REJECTED' ? 'text-rose-600' : item.value === 'CHANGES_REQUIRED' ? 'text-amber-600' : 'text-blue-600'}`} aria-hidden="true" /><span className="min-w-0 flex-1"><span className="block text-sm font-semibold">{item.label}</span><span className={`mt-0.5 block text-xs leading-4 ${selected ? 'text-slate-300' : 'text-slate-500'}`}>{item.description}</span></span>{selected ? <Check className="h-4 w-4 shrink-0" aria-hidden="true" /> : <ChevronRight className="h-4 w-4 shrink-0 text-slate-400" aria-hidden="true" />}</button>;
                })}</div>
                {decision && <div className="mt-4"><label className="block text-sm font-medium text-slate-800">{requiresReason ? 'Phản hồi cho doanh nghiệp' : 'Ghi chú xét duyệt'} {requiresReason && <span className="text-rose-600">*</span>}<textarea value={reason} onChange={(e) => setReason(e.target.value)} maxLength={2000} rows={4} className="mt-2 w-full resize-none rounded-xl border border-slate-300 bg-white p-3 text-sm text-slate-900 outline-none placeholder:text-slate-400 focus:border-blue-500 focus:ring-2 focus:ring-blue-100" placeholder={requiresReason ? 'Nêu rõ từng nội dung cần bổ sung hoặc lý do từ chối…' : 'Ghi chú nội bộ hoặc phản hồi cho doanh nghiệp…'} /></label><p className="mt-1 text-right text-xs tabular-nums text-slate-400">{reason.length}/2000</p><Button onClick={confirmDecision} isLoading={busy} disabled={requiresReason && !reason.trim()} variant={decision === 'REJECTED' ? 'destructive' : 'default'} className="mt-3 min-h-11 w-full">{actionButtonLabel}</Button><p className="mt-2 text-center text-xs leading-5 text-slate-500">Hành động sẽ được lưu vào lịch sử và thông báo cho doanh nghiệp.</p></div>}
              </section> : <section className="border-t border-slate-200 pt-6"><div className="flex gap-3 rounded-xl bg-white p-4 ring-1 ring-slate-200"><ShieldCheck className="h-5 w-5 shrink-0 text-emerald-600" aria-hidden="true" /><div><p className="text-sm font-semibold text-slate-900">Hồ sơ đã có quyết định</p><p className="mt-1 text-xs leading-5 text-slate-500">Quyết định này được khóa để bảo toàn lịch sử xét duyệt.</p></div></div></section>}
            </div>
          </aside>
        </div>
      </div>
    </div>}
  </div>;
}
