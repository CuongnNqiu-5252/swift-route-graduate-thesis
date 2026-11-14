'use client';

import { createContext, useContext } from 'react';
import { translate, type Translator } from './translate';
import { formatLocale, type Locale } from './locale';

const LocaleContext = createContext<Locale>('vi');

const preferences = {
  vi: { locale: 'vi', intlLocale: formatLocale('vi'), t: (text: string) => text },
  en: { locale: 'en', intlLocale: formatLocale('en'), t: (text: string) => translate('en', text) },
} satisfies Record<Locale, { locale: Locale; intlLocale: string; t: Translator }>;

export function LocaleProvider({ locale, children }: { locale: Locale; children: React.ReactNode }) {
  return <LocaleContext.Provider value={locale}>{children}</LocaleContext.Provider>;
}

export function useLocale() {
  return preferences[useContext(LocaleContext)];
}
