'use client';

import { useRouter } from 'next/navigation';
import { useTransition } from 'react';
import { localeCookie, type Locale } from '@/i18n/locale';
import styles from './landing.module.css';

const options: { value: Locale; label: string; name: string }[] = [
  { value: 'vi', label: 'VI', name: 'Tiếng Việt' },
  { value: 'en', label: 'EN', name: 'English' },
];

/** Two-state language switch that writes the shared locale cookie and refreshes the server render. */
export function LanguageToggle({ locale, label }: { locale: Locale; label: string }) {
  const router = useRouter();
  const [pending, startTransition] = useTransition();

  return (
    <div className={styles.language} role="group" aria-label={label}>
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          lang={option.value}
          aria-label={option.name}
          aria-pressed={option.value === locale}
          disabled={pending}
          className={styles.languageButton}
          onClick={() => {
            if (option.value === locale) return;
            const secure = window.location.protocol === 'https:' ? '; Secure' : '';
            document.cookie = `${localeCookie}=${option.value}; Path=/; Max-Age=31536000; SameSite=Lax${secure}`;
            startTransition(() => router.refresh());
          }}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}
