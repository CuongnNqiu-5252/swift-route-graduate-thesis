import type { Metadata } from 'next';
import { Inter } from 'next/font/google';
import './globals.css';
import { AuthProvider } from '@/application/contexts/auth-context';
import { Toaster } from 'sonner';

const inter = Inter({
  variable: '--font-geist-sans',
  subsets: ['latin', 'vietnamese'],
});

export const metadata: Metadata = {
  title: 'SwiftRoute — Dispatch & Operations Dashboard',
  description: 'Nền tảng điều phối giao hàng thông minh với Dynamic Programming',
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="vi" className={`${inter.variable} h-full`} suppressHydrationWarning>
      <body className="min-h-full bg-gray-50 text-gray-900 antialiased font-sans" suppressHydrationWarning>
        <AuthProvider>
          {children}
          <Toaster richColors position="top-right" />
        </AuthProvider>
      </body>
    </html>
  );
}
