'use client';

import { Languages } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useTransition } from 'react';
import { CustomSelect } from '@/components/ui/select';
import { localeCookie, resolveLocale } from '@/i18n/locale';
import { useLocale } from '@/i18n/provider';

export function LanguageSelect() {
  const { locale, t } = useLocale();
  const router = useRouter();
  const [pending, startTransition] = useTransition();

  return (
    <CustomSelect
      aria-label={t('Ngôn ngữ')}
      value={locale}
      disabled={pending}
      leadingIcon={<Languages size={15} aria-hidden="true" />}
      className="w-auto min-w-[8.25rem] border-slate-300 text-slate-800"
      options={[
        { value: 'vi', label: 'Tiếng Việt', lang: 'vi' },
        { value: 'en', label: 'English', lang: 'en' },
      ]}
      onValueChange={(nextValue) => {
        const nextLocale = resolveLocale(nextValue);
        const secure = window.location.protocol === 'https:' ? '; Secure' : '';
        document.cookie = `${localeCookie}=${nextLocale}; Path=/; Max-Age=31536000; SameSite=Lax${secure}`;
        startTransition(() => router.refresh());
      }}
    />
  );
}
