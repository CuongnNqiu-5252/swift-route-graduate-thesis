'use client';

import { Check, Circle } from 'lucide-react';
import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/application/contexts/auth-context';
import { Progress, tenantWorkflow } from '@/infrastructure/repositories/tenant-workflow.repository';

export function SetupProgress() {
  const { user } = useAuth();
  const [progress, setProgress] = useState<Progress | null>(null);
  const [loaded, setLoaded] = useState(false);
  useEffect(() => {
    if (!user?.tenantId) return;
    let active = true;
    tenantWorkflow.progress(user.tenantId)
      .then((data) => {
        if (active) {
          setProgress(data);
          setLoaded(true);
        }
      })
      .catch(() => {
        if (active) {
          setProgress(null);
          setLoaded(true);
        }
      });
    return () => {
      active = false;
    };
  }, [user?.tenantId]);

  const steps = useMemo(() => [
    { label: 'Email', done: Boolean(user?.emailVerified), href: '/profile' },
    { label: 'Workspace', done: Boolean(user?.tenantId), href: '/organizations' },
    { label: 'Hồ sơ', done: Boolean(progress?.completed_steps.includes('BUSINESS_PROFILE')), href: '/onboarding' },
    { label: 'Điểm lấy hàng', done: Boolean(progress?.completed_steps.includes('PICKUP_POINT')), href: '/locations' },
    { label: 'Xét duyệt', done: Boolean(progress?.tenant.status === 'APPROVED'), href: '/onboarding' },
  ], [progress, user]);
  const completed = steps.filter(step => step.done).length;
  const ready = Boolean(progress?.readiness.ready_for_operations);
  if (!user?.tenantId || !loaded || ready) return null;

  return <section className="mb-6 rounded-xl border border-slate-200 bg-slate-50 p-4" aria-label="Tiến độ thiết lập workspace">
    <div className="flex flex-wrap items-end justify-between gap-3"><div><p className="text-sm font-semibold text-slate-950">Thiết lập workspace</p><p className="mt-1 text-xs text-slate-600">Hoàn tất các bước để mở toàn bộ chức năng vận hành.</p></div><p className="text-sm font-semibold tabular-nums text-slate-950">{completed}/5 bước</p></div>
    <div className="mt-4 h-1.5 overflow-hidden rounded-full bg-slate-200"><div className="h-full rounded-full bg-blue-600 transition-[width]" style={{width:`${completed*20}%`}}/></div>
    <ol className="mt-4 grid grid-cols-2 gap-2 sm:grid-cols-5">{steps.map(step=><li key={step.label}><Link href={step.href} className={`flex min-h-10 items-center gap-2 rounded-lg px-2 text-xs font-medium ${step.done?'text-slate-900':'text-slate-500 hover:bg-white'}`}>{step.done?<Check size={15} className="text-emerald-600"/>:<Circle size={15}/>}<span>{step.label}</span></Link></li>)}</ol>
  </section>;
}
