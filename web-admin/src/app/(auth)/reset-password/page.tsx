'use client';

import { useLocale } from '@/i18n/provider';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { authRepository } from '@/infrastructure/repositories/auth.repository';
import { ApiError } from '@/lib/api';
import { CheckCircle2, KeyRound, Route } from 'lucide-react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { FormEvent, Suspense, useEffect, useRef, useState } from 'react';

export default function ResetPasswordPage() {
  return <Suspense fallback={<main className="min-h-dvh bg-slate-100" />}><ResetPasswordForm /></Suspense>;
}

function ResetPasswordForm() {
  const { t } = useLocale();
  const searchToken = useSearchParams().get('token') ?? '';
  const isPreviouslyReset =
    typeof window !== 'undefined' && sessionStorage.getItem('swiftroute_pwd_reset_done') === 'true';

  const [manualToken, setManualToken] = useState('');
  const token = searchToken || manualToken;
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [passwordError, setPasswordError] = useState('');
  const [confirmError, setConfirmError] = useState('');
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(isPreviouslyReset);
  const [error, setError] = useState('');
  const [invalidToken, setInvalidToken] = useState(false);
  const statusRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (done) statusRef.current?.focus();
  }, [done]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setPasswordError('');
    setConfirmError('');
    if (password.length < 8) {
      setPasswordError(t("Mật khẩu cần ít nhất 8 ký tự."));
      return;
    }
    if (password !== confirm) {
      setConfirmError(t("Mật khẩu xác nhận không khớp."));
      return;
    }
    setBusy(true);
    setError('');
    setInvalidToken(false);
    try {
      await authRepository.resetPassword(token, password);
      if (typeof window !== 'undefined') {
        sessionStorage.setItem('swiftroute_pwd_reset_done', 'true');
      }
      window.history.replaceState(null, '', '/reset-password');
      setDone(true);
    } catch (requestError) {
      const status = requestError instanceof ApiError ? requestError.status : undefined;
      setInvalidToken(Boolean(status && status >= 400 && status < 500));
      setError(status && status >= 400 && status < 500
        ? t("Liên kết không hợp lệ, đã hết hạn hoặc đã được sử dụng. Hãy yêu cầu email mới.")
        : t("Máy chủ đang tạm thời không phản hồi. Vui lòng thử lại sau ít phút."));
    } finally {
      setBusy(false);
    }
  }

  if (!done && !token) return <main className="flex min-h-dvh items-center justify-center bg-[#eef1f4] p-4">
    <section className="w-full max-w-md rounded-2xl border border-slate-200 bg-white p-7 sm:p-10">
      <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-slate-950 text-white"><Route size={22} aria-hidden="true" /></div>
      <KeyRound size={38} aria-hidden="true" className="mt-8 text-amber-700" />
      <h1 className="mt-5 text-3xl font-semibold tracking-tight text-slate-950">{t("Đặt lại mật khẩu")}</h1>
      <p className="mt-3 text-sm leading-6 text-slate-600">{t("Liên kết đặt lại mật khẩu bị thiếu token hoặc không đầy đủ. Nếu bạn có mã token từ liên kết email, hãy dán vào ô bên dưới:")}</p>
      <form onSubmit={(e) => { e.preventDefault(); const val = (e.currentTarget.elements.namedItem('tokenInput') as HTMLInputElement)?.value?.trim(); if (val) setManualToken(val); }} className="mt-6 space-y-4">
        <Input id="tokenInput" name="tokenInput" label={t("Mã token từ email")} placeholder="Dán mã token vào đây" required />
        <Button type="submit" className="w-full cursor-pointer">{t("Tiếp tục đặt lại mật khẩu")}</Button>
      </form>
      <div className="mt-7 flex flex-col gap-3 sm:flex-row">
        <Link href="/forgot-password" className="inline-flex min-h-11 cursor-pointer items-center justify-center rounded-lg bg-[#17766c] px-5 text-sm font-medium text-white transition-colors duration-150 hover:bg-[#136058] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#17766c]">{t("Yêu cầu email mới")}</Link>
        <Link href="/login" className="inline-flex min-h-11 cursor-pointer items-center justify-center rounded-lg border border-slate-300 px-5 text-sm font-medium text-slate-800 transition-colors duration-150 hover:bg-slate-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#17766c]">{t("Về đăng nhập")}</Link>
      </div>
    </section>
  </main>;

  return <main className="flex min-h-dvh items-center justify-center bg-[#eef1f4] p-4">
    <section className="w-full max-w-md rounded-2xl border border-slate-200 bg-white p-7 sm:p-10">
      <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-slate-950 text-white"><Route size={22} aria-hidden="true" /></div>
      {done ? <div ref={statusRef} role="status" aria-live="polite" tabIndex={-1} className="mt-8 outline-none">
        <CheckCircle2 size={42} aria-hidden="true" className="text-emerald-700" />
        <h1 className="mt-5 text-3xl font-semibold tracking-tight text-slate-950">{t("Mật khẩu đã được đổi")}</h1>
        <p className="mt-3 text-sm leading-6 text-slate-600">{t("Mật khẩu mới đã được lưu và các phiên làm mới đăng nhập cũ đã bị thu hồi. Hãy đăng nhập lại.")}</p>
        <Link href="/login" className="mt-7 inline-flex min-h-11 cursor-pointer items-center rounded-lg bg-[#17766c] px-5 text-sm font-medium text-white transition-colors duration-150 hover:bg-[#136058] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#17766c]">{t("Đi đến đăng nhập")}</Link>
      </div> : <>
        <KeyRound size={38} aria-hidden="true" className="mt-8 text-[#17766c]" />
        <h1 className="mt-5 text-3xl font-semibold tracking-tight text-slate-950">{t("Đặt mật khẩu mới")}</h1>
        <p className="mt-3 text-sm leading-6 text-slate-600">{t("Liên kết chỉ dùng một lần và hết hạn sau 15 phút.")}</p>
        <form onSubmit={submit} className="mt-7 space-y-4">
          <Input id="new-password" label={t("Mật khẩu mới")} type="password" autoComplete="new-password" minLength={8} required value={password} error={passwordError} onChange={(event) => { setPassword(event.target.value); if (passwordError) setPasswordError(''); }} />
          <Input id="confirm-password" label={t("Nhập lại mật khẩu")} type="password" autoComplete="new-password" minLength={8} required value={confirm} error={confirmError} onChange={(event) => { setConfirm(event.target.value); if (confirmError) setConfirmError(''); }} />
          {error && <p role="alert" className="rounded-lg bg-red-50 p-3 text-sm leading-5 text-red-700">{error}</p>}
          {invalidToken && <Link href="/forgot-password" className="inline-flex min-h-11 cursor-pointer items-center justify-center rounded-lg border border-slate-300 px-5 text-sm font-medium text-slate-800 transition-colors duration-150 hover:bg-slate-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#17766c]">{t("Yêu cầu email mới")}</Link>}
          <Button type="submit" size="lg" isLoading={busy} disabled={!token} className="min-h-12 w-full cursor-pointer">{t("Đổi mật khẩu")}</Button>
        </form>
      </>}
    </section>
  </main>;
}
