'use client';

import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import { Pause, Play, Search } from 'lucide-react';
import { AUDIT_CATEGORIES, matchesAudit, mergeAuditEntries } from '@/application/platform-audit';
import { PlatformAuditRow } from '@/components/admin/platform-audit-row';
import { PlatformConnection, usePlatformLive } from '@/components/admin/platform-live-provider';
import { Button } from '@/components/ui/button';
import { useLocale } from '@/i18n/provider';
import { platformRepository, type PlatformAuditEntry, type PlatformAuditFilter, type PlatformAuditPage as AuditHistoryPage } from '@/infrastructure/repositories/platform.repository';

const initial: PlatformAuditFilter = { query: '', category: '', severity: '', days: 7 };

export default function PlatformAuditPage() {
  const { t } = useLocale();
  const { snapshot, generation } = usePlatformLive();
  const [draft, setDraft] = useState(initial);
  const [filter, setFilter] = useState(initial);
  const [storedHistory, setHistory] = useState<{ key: string; page: AuditHistoryPage } | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);
  const [paused, setPaused] = useState<PlatformAuditEntry[] | null>(null);
  const request = useRef<AbortController | null>(null);
  const historyKey = `${JSON.stringify(filter)}:${generation}`;
  const history = storedHistory?.key === historyKey ? storedHistory.page : null;

  useEffect(() => {
    const controller = new AbortController(); request.current?.abort(); request.current = controller;
    platformRepository.history(filter, controller.signal).then(
      (data) => { if (!controller.signal.aborted) { setHistory({ key: historyKey, page: data }); setLoading(false); setError(false); } },
      () => { if (!controller.signal.aborted) setError(true); },
    );
    return () => controller.abort();
  }, [filter, historyKey]);
  useEffect(() => () => request.current?.abort(), []);

  const live = useMemo(() => (snapshot?.audit.entries ?? []).filter((entry) => matchesAudit(entry, filter)), [snapshot, filter]);
  const rows = mergeAuditEntries(history?.entries ?? [], live);
  const visible = paused ?? rows;
  const pending = paused ? rows.filter((entry) => !paused.some((old) => old.id === entry.id)).length : 0;

  function apply(event: FormEvent) {
    event.preventDefault(); setHistory(null); setPaused(null); setLoading(false); setError(false); setFilter({ ...draft });
  }
  async function loadMore() {
    const before = history?.entries.at(-1);
    if (!before || loading) return;
    const controller = new AbortController(); request.current?.abort(); request.current = controller;
    setLoading(true);
    try {
      const data = await platformRepository.history(filter, controller.signal, before);
      if (!controller.signal.aborted) { setHistory((current) => ({ key: historyKey, page: { entries: mergeAuditEntries(current?.key === historyKey ? current.page.entries : [], data.entries), has_more: data.has_more } })); setError(false); }
    } catch { if (!controller.signal.aborted) setError(true); }
    finally { if (request.current === controller) setLoading(false); }
  }

  return <div className="mx-auto max-w-6xl space-y-6">
    <header className="flex flex-wrap items-start justify-between gap-4"><div><h1 className="text-2xl font-semibold tracking-tight text-slate-950">{t('Nhật ký hoạt động')}</h1>
      <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-600">{t('Theo dõi xét duyệt, vòng đời doanh nghiệp, cấp workspace, vùng giao và luồng lập kế hoạch. Chỉ nhận thay đổi sau khi giao dịch thành công.')}</p></div><PlatformConnection /></header>
    <form onSubmit={apply} className="grid gap-3 rounded-xl bg-slate-50 p-4 sm:grid-cols-2 xl:grid-cols-[minmax(12rem,1fr)_auto_auto_auto_auto]">
      <label className="text-xs font-medium text-slate-600">{t('Tìm trong nhật ký')}
        <input value={draft.query} maxLength={120} onChange={(event) => setDraft({ ...draft, query: event.target.value })} placeholder={t('Doanh nghiệp, hành động hoặc mã tài khoản')} className="mt-1 block w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 focus-visible:outline-2 focus-visible:outline-teal-700" />
      </label>
      <label className="text-xs font-medium text-slate-600">{t('Nhóm hoạt động')}<select value={draft.category} onChange={(event) => setDraft({ ...draft, category: event.target.value })} className="mt-1 block w-full rounded-lg border border-slate-300 bg-white px-2 py-2 text-sm text-slate-900"><option value="">{t('Tất cả nhóm')}</option>{Object.entries(AUDIT_CATEGORIES).map(([key, label]) => <option key={key} value={key}>{t(label)}</option>)}</select></label>
      <label className="text-xs font-medium text-slate-600">{t('Mức độ')}<select value={draft.severity} onChange={(event) => setDraft({ ...draft, severity: event.target.value })} className="mt-1 block w-full rounded-lg border border-slate-300 bg-white px-2 py-2 text-sm text-slate-900"><option value="">{t('Tất cả mức độ')}</option><option value="INFO">{t('Thông tin')}</option><option value="WARNING">{t('Cần chú ý')}</option><option value="ERROR">{t('Lỗi')}</option></select></label>
      <label className="text-xs font-medium text-slate-600">{t('Khoảng thời gian')}<select value={draft.days} onChange={(event) => setDraft({ ...draft, days: Number(event.target.value) })} className="mt-1 block w-full rounded-lg border border-slate-300 bg-white px-2 py-2 text-sm text-slate-900">{[1, 7, 30, 90].map((days) => <option key={days} value={days}>{days} {t('ngày gần nhất')}</option>)}</select></label>
      <Button type="submit" className="self-end"><Search size={15} aria-hidden="true" />{t('Lọc nhật ký')}</Button>
    </form>
    <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 pb-3"><p className="text-sm text-slate-600">{visible.length} {t('sự kiện đang hiển thị')}{paused && <span className="ml-2 font-medium text-amber-900">{t('Đã tạm dừng hiển thị')}</span>}</p>
      <Button type="button" size="sm" variant="outline" onClick={() => {
        if (paused) { setPaused(null); setHistory(null); setLoading(false); setFilter({ ...filter }); }
        else setPaused(rows);
      }}>{paused ? <Play size={14} /> : <Pause size={14} />}{paused ? pending ? `${t('Hiển thị sự kiện mới')} (${pending})` : t('Tiếp tục hiển thị') : t('Tạm dừng hiển thị')}</Button>
    </div>
    {error && <p role="alert" className="text-sm text-rose-800">{t('Chưa tải được lịch sử nhật ký. Bạn có thể lọc lại để thử lại; sự kiện trực tiếp vẫn được giữ.')}</p>}
    <div>{visible.map((entry) => <PlatformAuditRow key={entry.id} entry={entry} />)}</div>
    {!visible.length && <p role="status" className="py-10 text-center text-sm text-slate-500">{t(history ? 'Không có sự kiện khớp bộ lọc.' : error ? 'Lịch sử nhật ký chưa sẵn sàng.' : 'Đang tải nhật ký…')}</p>}
    {history?.has_more && <div className="flex justify-center"><Button variant="outline" onClick={() => void loadMore()} isLoading={loading} disabled={!!paused}>{t('Tải lịch sử cũ hơn')}</Button></div>}
    <p className="text-xs leading-5 text-slate-500">{t('Tạm dừng chỉ giữ nguyên danh sách đang xem; kết nối trực tiếp vẫn hoạt động. Dữ liệu nhạy cảm và nội dung đăng nhập không được hiển thị trong nhật ký này.')}</p>
  </div>;
}
