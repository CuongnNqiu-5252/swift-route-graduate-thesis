'use client';

import { useLocale } from '@/i18n/provider';

import { CheckCircle2, LoaderCircle, MailWarning, Route } from 'lucide-react';
import Link from 'next/link';
import { Suspense, useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { authRepository } from '@/infrastructure/repositories/auth.repository';

type State = 'checking' | 'verified' | 'invalid';

// Deduplicate verification requests per token so React StrictMode double-mounts
// share the single in-flight promise instead of consuming the one-time token twice.
const verificationRequests = new Map<string, Promise<void>>();

function verifyEmailOnce(token: string): Promise<void> {
  const existing = verificationRequests.get(token);
  if (existing) return existing;
  const promise = authRepository.verifyEmail(token);
  verificationRequests.set(token, promise);
  return promise;
}

function humanizeVerifyError(rawMsg: string | undefined): string {
  if (!rawMsg) return 'Liên kết xác minh không hợp lệ hoặc đã hết hạn.';
  const lower = rawMsg.toLowerCase();
  if (
    lower.includes('invalid') ||
    lower.includes('expired') ||
    lower.includes('already used') ||
    lower.includes('not found') ||
    lower.includes('invalid_verification_token')
  ) {
    return 'Liên kết xác minh không hợp lệ hoặc đã hết hạn. Nếu tài khoản đã được xác minh trước đó, bạn có thể đăng nhập ngay.';
  }
  return rawMsg;
}

export default function VerifyEmailPage() {
  return (
    <Suspense
      fallback={
        <main className="flex min-h-dvh items-center justify-center bg-[#eef1f4]">
          <LoaderCircle className="animate-spin text-[#17766c]" aria-hidden="true" />
        </main>
      }
    >
      <VerificationContent />
    </Suspense>
  );
}

function VerificationContent() {
  const { t } = useLocale();
  const token = useSearchParams().get('token');
  const isPreviouslyVerified =
    typeof window !== 'undefined' && sessionStorage.getItem('swiftroute_email_verified') === 'true';

  const [state, setState] = useState<State>(
    isPreviouslyVerified ? 'verified' : token ? 'checking' : 'invalid'
  );
  const [message, setMessage] = useState(
    isPreviouslyVerified
      ? 'Email đã được xác minh thành công. Bạn có thể đăng nhập để tiếp tục.'
      : token
        ? 'Đang xác minh liên kết an toàn…'
        : 'Liên kết xác minh không đầy đủ hoặc bị thiếu mã token.'
  );

  useEffect(() => {
    if (!token) return;
    let active = true;

    verifyEmailOnce(token)
      .then(() => {
        if (!active) return;
        if (typeof window !== 'undefined') {
          sessionStorage.setItem('swiftroute_email_verified', 'true');
        }
        window.history.replaceState(null, '', '/verify-email');
        setState('verified');
        setMessage('Email đã được xác minh thành công. Bạn có thể đăng nhập và tạo doanh nghiệp.');
      })
      .catch((error) => {
        if (!active) return;
        setState('invalid');
        const raw = error instanceof Error ? error.message : '';
        setMessage(humanizeVerifyError(raw));
      });

    return () => {
      active = false;
    };
  }, [token]);

  const Icon = state === 'checking' ? LoaderCircle : state === 'verified' ? CheckCircle2 : MailWarning;

  return (
    <main className="flex min-h-dvh items-center justify-center bg-[#eef1f4] p-4">
      <section className="w-full max-w-lg rounded-3xl border border-slate-200 bg-white p-8 text-center sm:p-12">
        <div className="mx-auto flex h-11 w-11 items-center justify-center rounded-xl bg-slate-950 text-white">
          <Route size={22} aria-hidden="true" />
        </div>
        <div
          className={`mx-auto mt-8 flex h-16 w-16 items-center justify-center rounded-full ${
            state === 'verified'
              ? 'bg-emerald-50 text-emerald-700'
              : state === 'invalid'
                ? 'bg-red-50 text-red-700'
                : 'bg-teal-50 text-[#17766c]'
          }`}
        >
          <Icon size={30} aria-hidden="true" className={state === 'checking' ? 'animate-spin' : ''} />
        </div>
        <h1 className="mt-6 text-3xl font-semibold tracking-tight text-slate-950">
          {state === 'checking'
            ? t('Xác minh email')
            : state === 'verified'
              ? t('Xác minh thành công')
              : t('Chưa thể xác minh')}
        </h1>
        <p role="status" className="mt-3 text-sm leading-6 text-slate-600">
          {t(message)}
        </p>
        {state === 'verified' && (
          <Link
            href="/login"
            className="mt-8 inline-flex min-h-11 cursor-pointer items-center rounded-lg bg-[#17766c] px-5 text-sm font-medium text-white transition-colors duration-150 hover:bg-[#136058]"
          >
            {t('Đăng nhập và tiếp tục')}
          </Link>
        )}
        {state === 'invalid' && (
          <div className="mt-8 space-y-4">
            <div className="flex flex-col gap-3 sm:flex-row sm:justify-center">
              <Link
                href="/login"
                className="inline-flex min-h-11 cursor-pointer items-center justify-center rounded-lg bg-[#17766c] px-5 text-sm font-medium text-white transition-colors duration-150 hover:bg-[#136058]"
              >
                {t('Đi đến đăng nhập')}
              </Link>
              <Link
                href="/register"
                className="inline-flex min-h-11 cursor-pointer items-center justify-center rounded-lg border border-slate-300 px-5 text-sm font-medium text-slate-800 transition-colors duration-150 hover:bg-slate-50"
              >
                {t('Đăng ký tài khoản')}
              </Link>
            </div>
            <p className="text-xs text-slate-500">
              {t('Nếu liên kết đã dùng hoặc quá 24 giờ, hãy đăng nhập để yêu cầu một email mới.')}
            </p>
          </div>
        )}
      </section>
    </main>
  );
}
