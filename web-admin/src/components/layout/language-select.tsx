'use client';

import { Languages } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useTransition } from 'react';
import { localeCookie, resolveLocale } from '@/i18n/locale';
import { useLocale } from '@/i18n/provider';

export function LanguageSelect() {
  const { locale, t } = useLocale();
  const router = useRouter();
  const [pending, startTransition] = useTransition();

  return (
    <label className="inline-flex shrink-0 items-center gap-1.5 rounded-lg border border-slate-300 bg-white px-2 text-slate-800">
      <Languages size={16} aria-hidden="true" />
      <span className="sr-only">{t('Ngôn ngữ')}</span>
      <select
        value={locale}
        disabled={pending}
        className="min-h-10 bg-transparent text-sm focus-visible:outline-2 focus-visible:outline-blue-600"
        onChange={(event) => {
          const nextLocale = resolveLocale(event.target.value);
          const secure = window.location.protocol === 'https:' ? '; Secure' : '';
          document.cookie = `${localeCookie}=${nextLocale}; Path=/; Max-Age=31536000; SameSite=Lax${secure}`;
          startTransition(() => router.refresh());
        }}
      >
        <option value="vi" lang="vi">Tiếng Việt</option>
        <option value="en" lang="en">English</option>
      </select>
    </label>
  );
}
