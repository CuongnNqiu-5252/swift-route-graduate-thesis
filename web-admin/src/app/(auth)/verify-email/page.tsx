'use client';

import { useLocale } from '@/i18n/provider';

import { CheckCircle2, LoaderCircle, MailWarning, Route } from 'lucide-react';
import Link from 'next/link';
import { Suspense, useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import api from '@/lib/api';

type State = 'checking' | 'verified' | 'invalid';

export default function VerifyEmailPage() {
  return <Suspense fallback={<main className="flex min-h-dvh items-center justify-center bg-[#eef1f4]"><LoaderCircle className="animate-spin text-blue-700"/></main>}><VerificationContent/></Suspense>;
}

function VerificationContent() {
  const { t } = useLocale();
  const token=useSearchParams().get('token');
  const [state,setState]=useState<State>(token?'checking':'invalid');
  const [message,setMessage]=useState(token?"Đang xác minh liên kết an toàn…":"Liên kết xác minh không có token.");
  useEffect(()=>{
    if(!token)return;
    api.post('/auth/email/verify',{token}).then(()=>{window.history.replaceState(null,'','/verify-email');setState('verified');setMessage("Email đã được xác minh. Bạn có thể đăng nhập và tạo doanh nghiệp.");}).catch((error)=>{setState('invalid');setMessage(error instanceof Error?error.message:"Liên kết không hợp lệ hoặc đã hết hạn.");});
  },[token]);
  const Icon=state==='checking'?LoaderCircle:state==='verified'?CheckCircle2:MailWarning;
  return <main className="flex min-h-dvh items-center justify-center bg-[#eef1f4] p-4"><section className="w-full max-w-lg rounded-3xl border border-slate-200 bg-white p-8 text-center sm:p-12"><div className="mx-auto flex h-11 w-11 items-center justify-center rounded-xl bg-slate-950 text-white"><Route size={22}/></div><div className={`mx-auto mt-8 flex h-16 w-16 items-center justify-center rounded-full ${state==='verified'?'bg-emerald-50 text-emerald-700':state==='invalid'?'bg-red-50 text-red-700':'bg-blue-50 text-blue-700'}`}><Icon size={30} className={state==='checking'?'animate-spin':''}/></div><h1 className="mt-6 text-3xl font-semibold tracking-tight text-slate-950">{state==='checking'?t("Xác minh email"):state==='verified'?t("Xác minh thành công"):t("Chưa thể xác minh")}</h1><p role="status" className="mt-3 text-sm leading-6 text-slate-600">{t(message)}</p>{state==='verified'&&<Link href="/login" className="mt-8 inline-flex min-h-11 items-center rounded-lg bg-slate-950 px-5 text-sm font-medium text-white">{t("Đăng nhập và tiếp tục")}</Link>}{state==='invalid'&&<div className="mt-8 space-y-3"><Link href="/login" className="inline-flex min-h-11 items-center rounded-lg border border-slate-300 px-5 text-sm font-medium text-slate-800">{t("Đăng nhập để gửi lại email")}</Link><p className="text-xs text-slate-500">{t("Nếu liên kết đã dùng hoặc quá 24 giờ, hãy yêu cầu một email mới.")}</p></div>}</section></main>;
}
