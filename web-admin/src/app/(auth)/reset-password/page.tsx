'use client';

import { useLocale } from '@/i18n/provider';

import { CheckCircle2, KeyRound, Route } from 'lucide-react';
import axios from 'axios';
import Link from 'next/link';
import { FormEvent, Suspense, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';

export default function ResetPasswordPage() {
  return <Suspense fallback={<main className="min-h-dvh bg-slate-100"/>}><ResetPasswordForm/></Suspense>;
}

function ResetPasswordForm() {
  const { t } = useLocale();
  const token = useSearchParams().get('token') ?? '';
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState('');
  const [invalidToken, setInvalidToken] = useState(false);
  const statusRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (done) statusRef.current?.focus();
  }, [done]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (password.length < 8) return setError(t("Mật khẩu cần ít nhất 8 ký tự."));
    if (password !== confirm) return setError(t("Mật khẩu xác nhận không khớp."));
    setBusy(true);
    setError('');
    setInvalidToken(false);
    try {
      const baseUrl = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8000/api';
      await axios.post(`${baseUrl}/auth/password/reset`, { token, new_password: password });
      window.history.replaceState(null, '', '/reset-password');
      setDone(true);
    } catch (requestError) {
      const status = axios.isAxiosError(requestError) ? requestError.response?.status : undefined;
      setInvalidToken(Boolean(status && status >= 400 && status < 500));
      setError(status && status >= 400 && status < 500
        ? t("Liên kết không hợp lệ, đã hết hạn hoặc đã được sử dụng. Hãy yêu cầu email mới.")
        : t("Máy chủ đang tạm thời không phản hồi. Vui lòng thử lại sau ít phút."));
    } finally {
      setBusy(false);
    }
  }

  if (!token) return <main className="flex min-h-dvh items-center justify-center bg-slate-100 p-4">
    <section className="w-full max-w-md rounded-2xl bg-white p-7 shadow-sm sm:p-10">
      <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-slate-950 text-white"><Route size={22}/></div>
      <KeyRound size={38} className="mt-8 text-amber-700"/>
      <h1 className="mt-5 text-3xl font-semibold tracking-tight text-slate-950">{t("Liên kết không hợp lệ")}</h1>
      <p className="mt-3 text-sm leading-6 text-slate-600">{t("Liên kết đặt lại mật khẩu bị thiếu hoặc không đầy đủ. Hãy yêu cầu một email mới để tiếp tục.")}</p>
      <div className="mt-7 flex flex-col gap-3 sm:flex-row">
        <Link href="/forgot-password" className="inline-flex min-h-11 items-center justify-center rounded-lg bg-slate-950 px-5 text-sm font-medium text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-600">{t("Yêu cầu email mới")}</Link>
        <Link href="/login" className="inline-flex min-h-11 items-center justify-center rounded-lg border border-slate-300 px-5 text-sm font-medium text-slate-800 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-600">{t("Về đăng nhập")}</Link>
      </div>
    </section>
  </main>;

  return <main className="flex min-h-dvh items-center justify-center bg-slate-100 p-4">
    <section className="w-full max-w-md rounded-2xl bg-white p-7 shadow-sm sm:p-10">
      <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-slate-950 text-white"><Route size={22}/></div>
      {done ? <div ref={statusRef} role="status" aria-live="polite" tabIndex={-1} className="mt-8 outline-none">
        <CheckCircle2 size={42} className="text-emerald-700"/>
        <h1 className="mt-5 text-3xl font-semibold tracking-tight text-slate-950">{t("Mật khẩu đã được đổi")}</h1>
        <p className="mt-3 text-sm leading-6 text-slate-600">{t("Mật khẩu mới đã được lưu và các phiên làm mới đăng nhập cũ đã bị thu hồi. Hãy đăng nhập lại.")}</p>
        <Link href="/login" className="mt-7 inline-flex min-h-11 items-center rounded-lg bg-slate-950 px-5 text-sm font-medium text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-600">{t("Đi đến đăng nhập")}</Link>
      </div> : <>
        <KeyRound size={38} className="mt-8 text-blue-700"/>
        <h1 className="mt-5 text-3xl font-semibold tracking-tight text-slate-950">{t("Đặt mật khẩu mới")}</h1>
        <p className="mt-3 text-sm leading-6 text-slate-600">{t("Liên kết chỉ dùng một lần và hết hạn sau 15 phút.")}</p>
        <form onSubmit={submit} className="mt-7 space-y-4">
          <Input id="new-password" label={t("Mật khẩu mới")} type="password" autoComplete="new-password" minLength={8} required value={password} onChange={(event)=>setPassword(event.target.value)}/>
          <Input id="confirm-password" label={t("Nhập lại mật khẩu")} type="password" autoComplete="new-password" minLength={8} required value={confirm} onChange={(event)=>setConfirm(event.target.value)}/>
          {error && <p role="alert" className="rounded-lg bg-red-50 p-3 text-sm leading-5 text-red-700">{error}</p>}
          {invalidToken && <Link href="/forgot-password" className="inline-flex min-h-11 items-center justify-center rounded-lg border border-slate-300 px-5 text-sm font-medium text-slate-800 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-600">{t("Yêu cầu email mới")}</Link>}
          <Button type="submit" size="lg" isLoading={busy} disabled={!token} className="min-h-12 w-full">{t("Đổi mật khẩu")}</Button>
        </form>
      </>}
    </section>
  </main>;
}
