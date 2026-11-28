export const localeCookie = 'swiftroute_locale';

export type Locale = 'vi' | 'en';

export function resolveLocale(value?: string | null): Locale {
  return value === 'en' ? 'en' : 'vi';
}

export function formatLocale(locale: Locale): string {
  return locale === 'en' ? 'en-GB' : 'vi-VN';
}
