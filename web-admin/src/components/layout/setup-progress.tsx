'use client';

import { ArrowRight, Check, Circle } from 'lucide-react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useEffect, useState } from 'react';
import { useAuth } from '@/application/contexts/auth-context';
import { useLocale } from '@/i18n/provider';
import { tenantWorkflow, type Progress } from '@/infrastructure/repositories/tenant-workflow.repository';

export function SetupProgress() {
  const { t } = useLocale();
  const { user } = useAuth();
  const pathname = usePathname();
  const tenantId = user?.tenantId;
  const onSetupPage = pathname === '/onboarding';
  const [result, setResult] = useState<{ tenantId: string; progress: Progress } | null>(null);

  useEffect(() => {
    if (!tenantId || onSetupPage) return;
    let active = true;
    tenantWorkflow.progress(tenantId)
      .then((progress) => {
        if (active) setResult({ tenantId, progress });
      })
      .catch(() => {
        if (active) setResult(null);
      });
    return () => {
      active = false;
    };
  }, [tenantId, pathname, onSetupPage]);

  if (!user || onSetupPage || !result || result.tenantId !== tenantId) return null;

  const { progress } = result;
  const reviewStatus = progress.verification_requests[0]?.status;
  const awaitingReview = reviewStatus === 'SUBMITTED' || reviewStatus === 'UNDER_REVIEW';
  const isOwner = (user.tenantRole ?? user.role) === 'OWNER';
  const steps = [
    { id: 'email', label: 'Email', done: Boolean(user.emailVerified), href: '/profile', action: t('Xác minh email') },
    { id: 'tenant', label: t('Doanh nghiệp'), done: true },
    {
      id: 'profile',
      label: t('Hồ sơ'),
      done: progress.completed_steps.includes('BUSINESS_PROFILE'),
      href: '/onboarding',
      action: isOwner ? t('Hoàn thiện hồ sơ') : t('Xem tiến độ thiết lập'),
    },
    {
      id: 'pickup',
      label: t('Điểm lấy hàng'),
      done: progress.completed_steps.includes('PICKUP_POINT'),
      href: '/onboarding',
      action: isOwner ? t('Thiết lập điểm lấy hàng') : t('Xem tiến độ thiết lập'),
    },
    {
      id: 'review',
      label: t('Xét duyệt'),
      done: progress.tenant.status === 'APPROVED',
      href: '/onboarding',
      action: awaitingReview || progress.tenant.status === 'SUSPENDED' ? t('Xem trạng thái xét duyệt') : t('Mở hồ sơ xét duyệt'),
    },
  ];
  const nextStep = steps.find((step) => !step.done);
  if (!nextStep?.href) return null;

  const completed = steps.filter((step) => step.done).length;
  const reviewPending = nextStep.id === 'review' && awaitingReview;

  return (
    <section className="mb-5 rounded-xl border border-slate-200 bg-slate-50/70 px-4 py-3.5 sm:px-5" aria-labelledby="workspace-setup-title">
      <div className="flex flex-wrap items-center justify-between gap-x-5 gap-y-2">
        <div className="min-w-0">
          <h2 id="workspace-setup-title" className="text-sm font-semibold text-slate-950">{t('Thiết lập doanh nghiệp')}</h2>
          {reviewPending && <p className="mt-0.5 text-sm text-slate-600">{t('Hồ sơ đang chờ xét duyệt.')}</p>}
        </div>
        <div className="flex flex-wrap items-center gap-x-4 gap-y-1">
          <span aria-hidden="true" className="text-xs tabular-nums text-slate-600">
            <span className="font-semibold text-slate-950">{completed}/{steps.length}</span> {t('bước')}
          </span>
          <Link
            href={nextStep.href}
            className="inline-flex min-h-10 items-center gap-1.5 rounded-md px-2 text-sm font-semibold text-blue-700 hover:bg-blue-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
          >
            {nextStep.action}<ArrowRight aria-hidden="true" size={15} className="shrink-0" />
          </Link>
        </div>
      </div>
      <ol className="mt-2 grid grid-cols-2 gap-x-3 gap-y-2 md:grid-cols-5">
        {steps.map((step) => {
          const current = step === nextStep;
          return (
            <li key={step.id} aria-current={current ? 'step' : undefined} className="min-w-0">
              <span
                aria-hidden="true"
                className={`mb-2 block h-1 rounded-full ${step.done ? 'bg-blue-600' : current ? 'bg-blue-300' : 'bg-slate-200'}`}
              />
              <span className={`flex items-center gap-1.5 text-xs ${current ? 'font-semibold text-blue-800' : 'font-medium text-slate-600'}`}>
                {step.done
                  ? <Check aria-hidden="true" size={14} className="shrink-0 text-blue-700" />
                  : <Circle aria-hidden="true" size={12} className={`ml-px shrink-0 ${current ? 'fill-blue-700 text-blue-700' : 'text-slate-500'}`} />}
                <span className="min-w-0 break-words">{step.label}</span>
                <span className="sr-only"> {step.done ? t('Đã hoàn tất') : t('Chưa hoàn tất')}</span>
              </span>
            </li>
          );
        })}
      </ol>
    </section>
  );
}
