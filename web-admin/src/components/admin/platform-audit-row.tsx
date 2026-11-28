'use client';

import { AUDIT_ACTIONS, AUDIT_CATEGORIES } from '@/application/platform-audit';
import { useLocale } from '@/i18n/provider';
import type { PlatformAuditEntry } from '@/infrastructure/repositories/platform.repository';
import { formatVNDate } from '@/lib/utils';
import Link from 'next/link';

export function PlatformAuditRow({ entry, compact = false }: { entry: PlatformAuditEntry; compact?: boolean }) {
  const { t } = useLocale();
  const warning = entry.severity !== 'INFO';
  return <article className="grid gap-2 border-b border-slate-100 py-4 last:border-0 sm:grid-cols-[9rem_1fr]">
    <time dateTime={entry.created_at} className="text-xs leading-5 tabular-nums text-slate-500">{formatVNDate(entry.created_at)}</time>
    <div className="min-w-0">
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
        <p className={`text-sm font-semibold ${warning ? entry.severity === 'ERROR' ? 'text-rose-800' : 'text-amber-900' : 'text-slate-900'}`}>
          {t(AUDIT_ACTIONS[entry.action] || entry.action)}
        </p>
        <span className="text-xs text-slate-500">{t(AUDIT_CATEGORIES[entry.category] || entry.category)}</span>
      </div>
      <p className="mt-1 break-words text-sm text-slate-700">{entry.tenant_name || entry.tenant_id}</p>
      {!compact && <p className="mt-1 break-words text-xs text-slate-500">
        {t('Thực hiện bởi:')} {entry.actor_id ? `${t('Tài khoản')} ${entry.actor_id}` : t('Tác vụ hệ thống')}
      </p>}
      {!compact && <details className="mt-2 text-xs">
        <summary className="w-fit cursor-pointer rounded py-1 text-slate-600 focus-visible:outline-2 focus-visible:outline-teal-700">{t('Chi tiết sự kiện')}</summary>
        <dl className="mt-2 grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 rounded-sm bg-slate-50 p-3 text-slate-700">
          <dt>{t('Mã sự kiện')}</dt><dd className="break-all">{entry.id}</dd>
          <dt>{t('Hành động')}</dt><dd className="break-all">{entry.action}</dd>
          <dt>{t('Đối tượng')}</dt><dd className="break-all">{entry.aggregate_type} · {entry.aggregate_id}</dd>
          {Object.entries(entry.details).map(([key, value]) => <div key={key} className="contents"><dt>{key}</dt><dd className="break-words whitespace-pre-wrap">{String(value)}</dd></div>)}
        </dl>
      </details>}
      {entry.aggregate_type === 'TENANT_VERIFICATION' && entry.action.startsWith('VERIFICATION_') && (
        <Link href={`/admin/tenant-verifications?request=${encodeURIComponent(entry.aggregate_id)}`}
          className="mt-2 inline-block rounded text-xs font-semibold text-[#17766c] underline underline-offset-4 focus-visible:outline-2 focus-visible:outline-teal-700">
          {t('Mở hồ sơ xét duyệt')}
        </Link>
      )}
    </div>
  </article>;
}
