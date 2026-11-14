'use client';

import { useLocale } from '@/i18n/provider';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { ShieldCheck, Truck } from 'lucide-react';
import { useAuth } from '@/application/contexts/auth-context';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { isSystemAdmin, portalHome } from '@/lib/portal-access';

export function PortalLoginForm({ portal }: { portal: 'tenant' | 'admin' }) {
  const { t } = useLocale();
  const admin = portal === 'admin';
  const { user, token, login, isLoading } = useAuth();
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (busy || isLoading || !token || !user) return;
    if (!admin || isSystemAdmin(user)) router.replace(portalHome(user));
  }, [admin, busy, isLoading, router, token, user]);

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setError('');
    const fields = new FormData(event.currentTarget);
    try {
      const profile = await login(String(fields.get('email')).trim(), String(fields.get('password')));
      if (admin && !isSystemAdmin(profile)) {
        setError(t("Tài khoản này không có quyền quản trị hệ thống. Hãy đăng nhập tại cổng khách thuê."));
        return;
      }
      router.replace(portalHome(profile));
    } catch (err) {
      setError(err instanceof Error ? err.message : t("Đăng nhập thất bại"));
    } finally {
      setBusy(false);
    }
  }

  const Icon = admin ? ShieldCheck : Truck;
  return <main className={`flex min-h-screen items-center justify-center p-4 ${admin ? 'bg-slate-950' : 'bg-slate-50'}`}>
    <div className="w-full max-w-md space-y-6">
      <div className="text-center">
        <div className={`mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-xl text-white ${admin ? 'bg-indigo-600' : 'bg-blue-600'}`}><Icon size={26}/></div>
        <h1 className={`text-2xl font-bold ${admin ? 'text-white' : 'text-slate-900'}`}>SwiftRoute {admin ? t("Quản trị nền tảng") : t("Doanh nghiệp")}</h1>
        <p className={`mt-2 text-sm ${admin ? 'text-slate-300' : 'text-slate-600'}`}>{admin ? t("Cổng quản trị và xét duyệt doanh nghiệp") : t("Không gian quản lý doanh nghiệp và giao hàng")}</p>
      </div>
      <Card><CardHeader><CardTitle>{admin ? t("Đăng nhập quản trị hệ thống") : t("Đăng nhập khách thuê")}</CardTitle></CardHeader><CardContent>
        <form onSubmit={submit} className="space-y-4">
          <Input name="email" label="Email" type="email" autoComplete="username" required/>
          <Input name="password" label={t("Mật khẩu")} type="password" autoComplete="current-password" required minLength={6}/>
          {!admin && <div className="text-right"><Link href="/forgot-password" className="text-sm font-medium text-blue-700 underline-offset-4 hover:underline">{t("Quên mật khẩu?")}</Link></div>}
          {error && <p role="alert" className="rounded bg-red-50 p-3 text-sm text-red-700">{error}</p>}
          {admin && token && user && !isSystemAdmin(user) && !error && <p role="alert" className="text-sm text-amber-700">{t("Phiên hiện tại thuộc khách thuê. Hãy dùng tài khoản quản trị hệ thống.")}</p>}
          <Button type="submit" className="w-full" isLoading={busy} disabled={isLoading}>{t("Đăng nhập")}</Button>
          {!admin && <p className="text-center text-sm text-slate-600">{t("Chưa có tài khoản?")}{" "}<Link href="/register" className="text-blue-600 underline">{t("Tạo tài khoản quản trị")}</Link></p>}
        </form>
      </CardContent></Card>
      <p className={`text-center text-sm ${admin ? 'text-slate-300' : 'text-slate-600'}`}>
        <Link href={admin ? '/login' : '/admin/login'} className="underline">{admin ? t("Đến cổng khách thuê") : t("Quản trị hệ thống")}</Link>
      </p>
    </div>
  </main>;
}
