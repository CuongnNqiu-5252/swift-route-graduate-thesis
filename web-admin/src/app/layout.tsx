import type { Metadata } from 'next';
import { cookies } from 'next/headers';
import { Inter } from 'next/font/google';
import './globals.css';
import { AuthProvider } from '@/application/contexts/auth-context';
import { Toaster } from 'sonner';
import { LocaleProvider } from '@/i18n/provider';
import { localeCookie, resolveLocale } from '@/i18n/locale';
import { translate } from '@/i18n/translate';

const inter = Inter({
  variable: '--font-geist-sans',
  subsets: ['latin', 'vietnamese'],
});

async function requestLocale() {
  return resolveLocale((await cookies()).get(localeCookie)?.value);
}

export async function generateMetadata(): Promise<Metadata> {
  const locale = await requestLocale();
  return {
    title: translate(locale, 'SwiftRoute — Điều phối và vận hành'),
    description: translate(locale, 'Nền tảng điều phối và quản lý giao hàng'),
  };
}

export default async function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const locale = await requestLocale();
  return (
    <html lang={locale} className={`${inter.variable} h-full`}>
      <body className="min-h-full bg-gray-50 text-gray-900 antialiased font-sans" suppressHydrationWarning>
        <LocaleProvider locale={locale}>
          <AuthProvider>
            {children}
            <Toaster richColors position="top-right" />
          </AuthProvider>
        </LocaleProvider>
      </body>
    </html>
  );
}
