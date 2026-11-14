import type { Locale } from './locale';
import english from './messages.en.json';

export type Translator = (text: string) => string;

export function translate(locale: Locale, text: string): string {
  if (locale === 'en' && Object.hasOwn(english, text)) {
    return english[text as keyof typeof english];
  }
  return text;
}
