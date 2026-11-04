'use client';

import { useCallback, useEffect, useState } from 'react';
import { useAuth } from '@/application/contexts/auth-context';
import {
  PageResult,
  tenantWorkflow,
  Verification,
  VerificationDetail,
} from '@/infrastructure/repositories/tenant-workflow.repository';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { isSystemAdmin } from '@/lib/portal-access';

const STATUS_LABEL: Record<string, string> = {
  SUBMITTED: 'Mới gửi',
  UNDER_REVIEW: 'Đang xem xét',
  CHANGES_REQUIRED: 'Cần bổ sung',
  APPROVED: 'Đã duyệt',
  REJECTED: 'Từ chối',
};

function formatTime(value?: string) {
  if (!value) return '—';
  return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value));
}

function parseSnapshot(value?: string) {
  if (!value) return null;
  try { return JSON.parse(value); } catch { return null; }
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
    setBusy(true);
    setError('');
    try { setQueue(await tenantWorkflow.reviewQueue(status, search, page)); }
    catch (e) { setError(e instanceof Error ? e.message : 'Không tải được hàng đợi xét duyệt'); }
    finally { setBusy(false); }
  }, [allowed, page, search, status]);

  useEffect(() => { void load(); }, [load]);

  async function openDetail(requestId: string) {
    setBusy(true);
    setError('');
    try { setDetail(await tenantWorkflow.reviewDetail(requestId)); }
    catch (e) { setError(e instanceof Error ? e.message : 'Không tải được chi tiết hồ sơ'); }
    finally { setBusy(false); }
  }

  async function confirmDecision() {
    if (!detail || !decision) return;
    setBusy(true);
    setError('');
    try {
      await tenantWorkflow.review(detail.request, decision, reason.trim());
      setDecision('');
      setReason('');
      setDetail(null);
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Không thể ghi nhận quyết định');
      try { setDetail(await tenantWorkflow.reviewDetail(detail.request.request_id)); } catch { /* keep primary error */ }
    } finally { setBusy(false); }
  }

  if (!allowed) return <p className="rounded-lg bg-red-50 p-4 text-sm text-red-700">Chỉ System Admin có quyền xét duyệt doanh nghiệp.</p>;

  const items = queue?.content ?? [];
  const selectedSnapshot = parseSnapshot(detail?.request.snapshot);
  const requiresReason = decision === 'REJECTED' || decision === 'CHANGES_REQUIRED';

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-gray-950">Xét duyệt doanh nghiệp</h1>
          <p className="mt-1 text-sm text-gray-600">Ưu tiên hồ sơ mới, đọc snapshot đã nộp và ghi nhận quyết định có lịch sử.</p>
        </div>
        <Button variant="outline" onClick={load} isLoading={busy}>Làm mới</Button>
      </div>

      {error && <p role="alert" className="rounded-lg bg-red-50 p-3 text-sm text-red-700">{error}</p>}

      <div className="grid gap-3 md:grid-cols-[220px_minmax(0,1fr)_auto]">
        <label className="text-sm font-medium text-gray-800">Trạng thái
          <select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }} className="mt-1 min-h-11 w-full rounded-lg border border-gray-300 bg-white px-3">
            <option value="">Tất cả</option>
            {Object.entries(STATUS_LABEL).map(([value, label]) => <option value={value} key={value}>{label}</option>)}
          </select>
        </label>
        <Input label="Tìm theo tên doanh nghiệp" value={searchDraft} onChange={(e) => setSearchDraft(e.target.value)} placeholder="Nhập tên doanh nghiệp" />
        <Button className="md:mb-px" onClick={() => { setSearch(searchDraft.trim()); setPage(0); }}>Tìm kiếm</Button>
      </div>

      <div className="overflow-hidden rounded-xl border border-gray-200 bg-white">
        <div className="grid grid-cols-[minmax(0,1fr)_150px_160px_110px] gap-4 border-b border-gray-200 px-4 py-3 text-xs font-semibold uppercase tracking-wide text-gray-500 max-md:hidden">
          <span>Doanh nghiệp</span><span>Trạng thái</span><span>Ngày gửi</span><span className="text-right">Thao tác</span>
        </div>
        {items.length === 0 && !busy && <div className="p-8 text-center"><p className="font-medium text-gray-900">Không có hồ sơ phù hợp</p><p className="mt-1 text-sm text-gray-500">Thử đổi trạng thái hoặc xóa nội dung tìm kiếm.</p></div>}
        <div className="divide-y divide-gray-100">
          {items.map((item) => (
            <div key={item.request_id} className="grid items-center gap-3 px-4 py-4 md:grid-cols-[minmax(0,1fr)_150px_160px_110px] md:gap-4">
              <div className="min-w-0"><p className="truncate font-medium text-gray-950">{item.business_name || 'Doanh nghiệp chưa đặt tên'}</p><p className="mt-1 truncate text-xs text-gray-500">{item.tenant_id}</p></div>
              <span className="w-fit rounded-full bg-gray-100 px-2.5 py-1 text-xs font-semibold text-gray-700">{STATUS_LABEL[item.status] || item.status}</span>
              <span className="text-sm text-gray-600">{formatTime(item.submitted_at)}</span>
              <Button variant="outline" onClick={() => openDetail(item.request_id)}>Mở hồ sơ</Button>
            </div>
          ))}
        </div>
      </div>

      {queue && queue.total_pages > 1 && (
        <div className="flex items-center justify-between text-sm text-gray-600">
          <span>{queue.total_elements} hồ sơ · Trang {queue.page_number + 1}/{queue.total_pages}</span>
          <div className="flex gap-2"><Button variant="outline" disabled={queue.is_first} onClick={() => setPage((value) => value - 1)}>Trước</Button><Button variant="outline" disabled={queue.is_last} onClick={() => setPage((value) => value + 1)}>Sau</Button></div>
        </div>
      )}

      {detail && (
        <div role="dialog" aria-modal="true" aria-labelledby="verification-detail-title" className="fixed inset-0 z-50 overflow-y-auto bg-black/40 p-4">
          <div className="mx-auto my-6 max-w-4xl rounded-xl bg-white p-6 shadow-xl">
            <div className="flex flex-wrap items-start justify-between gap-4 border-b border-gray-200 pb-4">
              <div><h2 id="verification-detail-title" className="text-xl font-semibold text-gray-950">{detail.tenant.business_name}</h2><p className="mt-1 text-sm text-gray-600">{STATUS_LABEL[detail.request.status] || detail.request.status} · gửi {formatTime(detail.request.submitted_at)}</p></div>
              <Button variant="outline" onClick={() => { setDetail(null); setDecision(''); setReason(''); }}>Đóng</Button>
            </div>

            <div className="grid gap-6 py-6 lg:grid-cols-[minmax(0,1.35fr)_minmax(280px,0.65fr)]">
              <section><h3 className="font-semibold text-gray-950">Thông tin đã nộp</h3>{selectedSnapshot ? <dl className="mt-3 grid gap-x-5 gap-y-3 rounded-lg bg-gray-50 p-4 sm:grid-cols-2">{Object.entries(selectedSnapshot).filter(([key]) => key !== 'pickup_points').map(([key, value]) => <div key={key}><dt className="text-xs font-medium uppercase tracking-wide text-gray-500">{key.replaceAll('_', ' ')}</dt><dd className="mt-1 break-words text-sm text-gray-900">{typeof value === 'object' ? JSON.stringify(value) : String(value ?? '—')}</dd></div>)}</dl> : <p className="mt-3 text-sm text-gray-500">Snapshot không đọc được.</p>}</section>
              <section><h3 className="font-semibold text-gray-950">Lịch sử xử lý</h3><div className="mt-3 space-y-3">{detail.timeline.length === 0 ? <p className="text-sm text-gray-500">Chưa có hoạt động xét duyệt.</p> : detail.timeline.map((event, index) => <div key={`${event.action}-${event.created_at}-${index}`} className="border-b border-gray-100 pb-3"><p className="text-sm font-medium text-gray-900">{event.action.replaceAll('_', ' ')}</p><p className="mt-1 text-xs text-gray-500">{formatTime(event.created_at)}</p></div>)}</div></section>
            </div>

            {['SUBMITTED', 'UNDER_REVIEW'].includes(detail.request.status) && (
              <section className="border-t border-gray-200 pt-5">
                <h3 className="font-semibold text-gray-950">Quyết định</h3>
                <div className="mt-3 flex flex-wrap gap-2">{['UNDER_REVIEW', 'APPROVED', 'CHANGES_REQUIRED', 'REJECTED'].map((value) => <Button key={value} variant={decision === value ? 'default' : value === 'REJECTED' ? 'destructive' : 'outline'} onClick={() => setDecision(value)}>{STATUS_LABEL[value] || value}</Button>)}</div>
                {decision && <label className="mt-4 block text-sm font-medium text-gray-800">Lý do / phản hồi {requiresReason && <span className="text-red-600">*</span>}<textarea value={reason} onChange={(e) => setReason(e.target.value)} maxLength={2000} rows={4} className="mt-2 w-full rounded-lg border border-gray-300 p-3 text-base" placeholder={requiresReason ? 'Nêu cụ thể nội dung doanh nghiệp cần bổ sung hoặc lý do từ chối' : 'Ghi chú nội bộ hoặc phản hồi cho doanh nghiệp'} /></label>}
                {decision && <div className="mt-4 flex justify-end"><Button onClick={confirmDecision} isLoading={busy} disabled={requiresReason && !reason.trim()}>Xác nhận quyết định</Button></div>}
              </section>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
