'use client';

import { useLocale } from '@/i18n/provider';
import { formatVNDate } from '@/lib/utils';
import { ArrowRight, ClipboardCheck, ScrollText } from 'lucide-react';
import Link from 'next/link';
import { PlatformAuditRow } from './platform-audit-row';
import { PlatformConnection, usePlatformLive } from './platform-live-provider';

const statusLabels: Record<string, string> = { PENDING: 'Chờ xét duyệt', APPROVED: 'Đã phê duyệt', SUSPENDED: 'Tạm ngừng', REJECTED: 'Bị từ chối' };
const statusColors: Record<string, string> = { PENDING: 'bg-amber-500', APPROVED: 'bg-[#17766c]', SUSPENDED: 'bg-rose-600', REJECTED: 'bg-slate-400' };

export function PlatformOverview() {
  const { t, intlLocale } = useLocale();
  const { snapshot, status } = usePlatformLive();
  const number = new Intl.NumberFormat(intlLocale);
  const data = snapshot?.overview;
  const totalTenants = data?.tenant_statuses.reduce((sum, item) => sum + Number(item.total), 0) ?? 0;
  const activityPeak = Math.max(1, ...(data?.activity_days.map((day) => Number(day.total)) ?? []));

  return <div className="mx-auto max-w-[90rem] space-y-7">
    <header className="flex flex-wrap items-start justify-between gap-4">
      <div><h1 className="text-2xl font-semibold tracking-tight text-slate-950">{t('Tổng quan nền tảng')}</h1>
        <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-600">{t('Theo dõi vòng đời doanh nghiệp, ưu tiên hồ sơ cần xử lý và đối chiếu mọi thay đổi đã được ghi nhận.')}</p></div>
      <div className="space-y-2"><PlatformConnection />{snapshot && <p className="text-xs tabular-nums text-slate-500">{t('Dữ liệu lúc:')} {formatVNDate(snapshot.as_of)}</p>}</div>
    </header>
    {!snapshot ? <div role="status" className="rounded-xl bg-slate-50 px-6 py-12 text-sm text-slate-600">
      {t(status === 'connecting' ? 'Đang tải dữ liệu nền tảng…' : 'Chưa nhận được dữ liệu nền tảng. Hệ thống sẽ tự kết nối lại.')}
    </div> : data && <>
      <div className="grid grid-cols-3 gap-3 border-y border-slate-200 py-4 sm:gap-6 sm:py-6">
        {[{ value: totalTenants, label: 'Doanh nghiệp trên nền tảng', note: 'Tất cả trạng thái' },
        { value: data.pending_reviews, label: 'Hồ sơ chờ xử lý', note: 'Đã gửi hoặc đang xét duyệt' },
        { value: data.provisioning_retries, label: 'Workspace cần kiểm tra', note: 'Cấp workspace cần thử lại' }].map((metric) => <div key={metric.label}>
          <p className="h-14 text-xs leading-4 text-slate-600 sm:h-auto sm:text-sm sm:leading-5">{t(metric.label)}</p><p className="mt-2 text-3xl font-semibold tracking-tight tabular-nums text-slate-950 sm:text-4xl">{number.format(metric.value)}</p>
          <p className="mt-2 hidden text-xs text-slate-500 sm:block">{t(metric.note)}</p>
        </div>)}
      </div>
      <div className="grid items-start gap-8 xl:grid-cols-[minmax(18rem,0.8fr)_minmax(0,1.4fr)]">
        <div className="space-y-7">
          <section aria-labelledby="priority-review-title">
            <div className="flex items-center justify-between gap-3"><h2 id="priority-review-title" className="text-base font-semibold text-slate-950">{t('Hồ sơ chờ lâu nhất')}</h2><ClipboardCheck size={17} className="text-[#17766c]" aria-hidden="true" /></div>
            <p className="mt-1 text-xs leading-5 text-slate-500">{t('Ưu tiên theo thời gian gửi, không tự động phê duyệt.')}</p>
            <ul className="mt-3 divide-y divide-slate-100">{data.review_priority.map((item) => <li key={item.request_id}>
              <Link href={`/admin/tenant-verifications?request=${encodeURIComponent(item.request_id)}`} className="group flex items-center justify-between gap-3 rounded py-3 focus-visible:outline-2 focus-visible:outline-teal-700">
                <div className="min-w-0"><p className="break-words text-sm font-medium text-slate-900 group-hover:text-[#17766c]">{item.business_name}</p>
                  <p className={`mt-1 text-xs ${item.waiting_hours >= 24 ? 'font-semibold text-amber-900' : 'text-slate-500'}`}>{t('Đã chờ')} {number.format(item.waiting_hours)} {t('giờ')} · {t(item.status === 'UNDER_REVIEW' ? 'Đang xét duyệt' : 'Đã gửi hồ sơ')}</p></div>
                <ArrowRight size={16} className="shrink-0 text-slate-400" aria-hidden="true" />
              </Link>
            </li>)}</ul>
            {!data.review_priority.length && <p className="mt-4 text-sm text-slate-600">{t('Không có hồ sơ đang chờ xử lý.')}</p>}
            <Link href="/admin/tenant-verifications" className="mt-3 inline-block text-sm font-semibold text-[#17766c] underline underline-offset-4">{t('Mở hàng đợi xét duyệt')}</Link>
          </section>
          {data.provisioning_attention.length > 0 && <section className="rounded-sm bg-amber-50 p-4">
            <h2 className="font-semibold text-amber-950">{t('Cấp workspace cần kiểm tra')}</h2>
            <ul className="mt-2 space-y-3">{data.provisioning_attention.map((item) => <li key={item.tenant_id}>
              <p className="break-words text-sm font-medium text-amber-950">{item.business_name}</p>
              <p className="mt-1 break-words text-xs text-amber-900">{item.attempts} {t('lần thử')} · {item.last_error_code || t('Chưa có mã lỗi')}</p>
            </li>)}</ul>
            <p className="mt-3 text-xs leading-5 text-amber-900">{t('Chỉ hiển thị tình trạng cấp workspace, không tự thử lại hoặc thay đổi quyền.')}</p>
          </section>}
          <section aria-labelledby="tenant-distribution-title">
            <h2 id="tenant-distribution-title" className="text-base font-semibold text-slate-950">{t('Vòng đời doanh nghiệp')}</h2>
            <div className="mt-4 flex h-3 overflow-hidden rounded-full bg-slate-100" aria-hidden="true">{data.tenant_statuses.map((item) => <span key={item.status} className={statusColors[item.status] || 'bg-slate-300'} style={{ width: `${totalTenants ? Number(item.total) / totalTenants * 100 : 0}%` }} />)}</div>
            <ul className="mt-3 space-y-2">{data.tenant_statuses.map((item) => <li key={item.status} className="flex items-center justify-between text-sm"><span className="text-slate-600">{t(statusLabels[item.status] || item.status)}</span><span className="font-medium tabular-nums text-slate-900">{number.format(Number(item.total))}</span></li>)}</ul>
            {!totalTenants && <p className="mt-3 text-sm text-slate-500">{t('Chưa có doanh nghiệp.')}</p>}
          </section>
        </div>
        <div className="space-y-6">
          <section aria-labelledby="audit-activity-title" className="rounded-xl bg-slate-50 p-5">
            <div className="flex flex-wrap items-center justify-between gap-3"><h2 id="audit-activity-title" className="font-semibold text-slate-950">{t('Hoạt động 7 ngày')}</h2><span className="text-xs text-slate-500">{t('Theo giờ Việt Nam')}</span></div>
            <div className="mt-4 grid grid-cols-7 gap-2">{data.activity_days.map((day) => <div key={day.day} className="flex flex-col items-center gap-2">
              <span className="text-xs tabular-nums text-slate-700">{number.format(Number(day.total))}</span>
              <div className="flex h-16 w-full items-end rounded bg-slate-200/70" aria-hidden="true"><div className="w-full rounded bg-[#17766c]" style={{ height: `${Number(day.total) / activityPeak * 100}%` }} /></div>
              <time dateTime={day.day} className="text-xs text-slate-500">{day.day.slice(8)}/{day.day.slice(5, 7)}</time>
            </div>)}</div>
          </section>
          <section aria-labelledby="latest-audit-title">
            <div className="flex flex-wrap items-center justify-between gap-3"><h2 id="latest-audit-title" className="inline-flex items-center gap-2 font-semibold text-slate-950"><ScrollText size={17} className="text-[#17766c]" aria-hidden="true" />{t('Nhật ký mới nhất')}</h2><Link href="/admin/audit" className="text-sm font-semibold text-[#17766c] underline underline-offset-4">{t('Xem toàn bộ nhật ký')}</Link></div>
            <div className="mt-2">{snapshot.audit.entries.slice(0, 8).map((entry) => <PlatformAuditRow key={entry.id} entry={entry} compact />)}</div>
            {!snapshot.audit.entries.length && <p className="mt-4 text-sm text-slate-600">{t('Chưa có hoạt động được ghi nhận trong 7 ngày.')}</p>}
          </section>
        </div>
      </div>
    </>}
  </div>;
}
