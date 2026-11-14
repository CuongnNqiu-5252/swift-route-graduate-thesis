'use client';

import { useLocale } from '@/i18n/provider';

import axios from 'axios';
import { CheckCircle2, Mail, Route } from 'lucide-react';
import Link from 'next/link';
import { FormEvent, useEffect, useRef, useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';

export default function ForgotPasswordPage() {
  const { t } = useLocale();
  const [email, setEmail] = useState('');
  const [submittedEmail, setSubmittedEmail] = useState('');
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState('');
  const statusRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (done) statusRef.current?.focus();
  }, [done]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (busy) return;
    const normalizedEmail = email.trim().toLowerCase();
    setBusy(true);
    setError('');
    try {
      const baseUrl = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8000/api';
      await axios.post(`${baseUrl}/auth/password/forgot`, { email: normalizedEmail });
      setSubmittedEmail(normalizedEmail);
      setDone(true);
    } catch {
      setError(t("Chưa thể gửi yêu cầu. Kiểm tra kết nối và thử lại."));
    } finally {
      setBusy(false);
    }
  }

  return <main className="flex min-h-dvh items-center justify-center bg-slate-100 p-4">
    <section className="w-full max-w-md rounded-2xl bg-white p-7 shadow-sm sm:p-10">
      <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-slate-950 text-white"><Route size={22}/></div>
      {done ? <div ref={statusRef} role="status" aria-live="polite" tabIndex={-1} className="mt-8 outline-none">
        <CheckCircle2 size={42} className="text-emerald-700"/>
        <h1 className="mt-5 text-3xl font-semibold tracking-tight text-slate-950">{t("Kiểm tra hộp thư")}</h1>
        <p className="mt-3 text-sm leading-6 text-slate-600">{t("Nếu")}{" "}{submittedEmail}{" "}{t("thuộc một tài khoản SwiftRoute, email đặt lại mật khẩu đã được gửi. Hãy kiểm tra cả thư rác.")}</p>
        <Link href="/login" className="mt-7 inline-flex min-h-11 items-center rounded-lg bg-slate-950 px-5 text-sm font-medium text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-600">{t("Về đăng nhập")}</Link>
      </div> : <>
        <Mail size={38} className="mt-8 text-blue-700"/>
        <h1 className="mt-5 text-3xl font-semibold tracking-tight text-slate-950">{t("Quên mật khẩu")}</h1>
        <p className="mt-3 text-sm leading-6 text-slate-600">{t("Nhập email tài khoản. Liên kết đặt lại mật khẩu có hiệu lực trong 15 phút.")}</p>
        <form onSubmit={submit} className="mt-7 space-y-4">
          <Input id="email" label={t("Email tài khoản")} type="email" autoComplete="email" required disabled={busy} value={email} onChange={(event)=>setEmail(event.target.value)}/>
          {error && <p role="alert" className="rounded-lg bg-red-50 p-3 text-sm leading-5 text-red-700">{error}</p>}
          <Button type="submit" size="lg" isLoading={busy} className="min-h-12 w-full">{t("Gửi liên kết đặt lại")}</Button>
        </form>
        <Link href="/login" className="mt-5 inline-flex text-sm font-medium text-slate-700 underline-offset-4 hover:underline">{t("Trở về đăng nhập")}</Link>
      </>}
    </section>
  </main>;
}
