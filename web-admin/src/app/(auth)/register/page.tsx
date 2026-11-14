'use client';

import { useLocale } from '@/i18n/provider';

import { zodResolver } from '@hookform/resolvers/zod';
import { ArrowLeft, ArrowRight, Building2, Check, Mail, Route, ShieldCheck } from 'lucide-react';
import Link from 'next/link';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import * as z from 'zod';
import api from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';

const schema = z.object({
  fullName: z.string().trim().min(2, 'Nhập họ và tên đầy đủ'),
  phoneNumber: z.string().trim().regex(/^[0-9+\s.-]{9,20}$/, 'Số điện thoại không hợp lệ'),
  email: z.string().trim().email('Email không đúng định dạng'),
  password: z.string().min(8, 'Mật khẩu cần ít nhất 8 ký tự'),
  confirmPassword: z.string().min(8, 'Nhập lại mật khẩu'),
}).refine(data => data.password === data.confirmPassword, { message: 'Mật khẩu xác nhận không khớp', path: ['confirmPassword'] });

type FormValues = z.infer<typeof schema>;
const journey = [
  { label: 'Tạo tài khoản', detail: 'Thông tin người quản trị' },
  { label: 'Xác minh email', detail: 'Liên kết gửi qua Google Mail' },
  { label: 'Tạo doanh nghiệp', detail: 'Hồ sơ doanh nghiệp và vận hành' },
];

export default function RegisterPage() {
  const { t } = useLocale();
  const localizeError = (message?: string) => message ? t(message) : undefined;
  const [step, setStep] = useState(1);
  const [submittedEmail, setSubmittedEmail] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const form = useForm<FormValues>({ resolver: zodResolver(schema), mode: 'onBlur' });

  async function nextIdentity() {
    if (await form.trigger(['fullName', 'phoneNumber', 'email'])) setStep(2);
  }

  async function submit(values: FormValues) {
    setBusy(true); setError('');
    try {
      await api.post('/auth/register', { email: values.email.trim(), password: values.password, full_name: values.fullName.trim(), phone_number: values.phoneNumber.trim() });
      setSubmittedEmail(values.email.trim()); setStep(3);
    } catch (e) { setError(e instanceof Error ? e.message : t("Không thể tạo tài khoản")); }
    finally { setBusy(false); }
  }

  async function resend() {
    setBusy(true); setError(''); setNotice('');
    try { await api.post('/auth/email/resend-public', { email: submittedEmail }); setNotice(t("Đã gửi lại email. Vui lòng kiểm tra cả thư rác và mục Quảng cáo.")); }
    catch (e) { setError(e instanceof Error ? e.message : t("Chưa thể gửi lại email")); }
    finally { setBusy(false); }
  }

  const currentJourney = step === 3 ? 1 : 0;
  return <main className="min-h-dvh bg-[#eef1f4] p-3 sm:p-6"><div className="mx-auto grid min-h-[calc(100dvh-3rem)] max-w-6xl overflow-hidden rounded-3xl border border-slate-200 bg-white lg:grid-cols-[0.78fr_1.22fr]">
    <aside className="flex flex-col justify-between bg-slate-950 p-7 text-white sm:p-10"><div><Link href="/login" className="inline-flex items-center gap-2 text-lg font-semibold"><Route size={24}/>SwiftRoute</Link><h1 className="mt-14 max-w-sm text-4xl font-semibold leading-tight tracking-[-0.03em]">{t("Thiết lập đội giao hàng trong một luồng rõ ràng.")}</h1><p className="mt-5 max-w-md text-sm leading-6 text-slate-300">{t("Tạo tài khoản quản trị trước. Sau khi email được xác minh, SwiftRoute sẽ hướng dẫn tạo doanh nghiệp, điểm lấy hàng và đội vận hành.")}</p></div>
      <ol className="mt-12 space-y-1">{journey.map((item,index)=><li key={item.label} className="flex gap-4 border-t border-white/10 py-4"><span className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-xs font-semibold ${index <= currentJourney ? 'bg-white text-slate-950' : 'border border-white/20 text-slate-400'}`}>{index < currentJourney ? <Check size={14}/> : index+1}</span><div><p className="text-sm font-medium">{t(item.label)}</p><p className="mt-0.5 text-xs text-slate-400">{t(item.detail)}</p></div></li>)}</ol>
    </aside>
    <section className="flex items-center justify-center p-6 sm:p-12"><div className="w-full max-w-lg">
      {step < 3 && <><p className="text-sm font-medium text-blue-700">{t("Bước")}{" "}{step} / 2</p><h2 className="mt-2 text-3xl font-semibold tracking-tight text-slate-950">{step===1?t("Thông tin của bạn"):t("Bảo mật tài khoản")}</h2><p className="mt-2 text-sm text-slate-600">{step===1?t("Đây là tài khoản quản trị đầu tiên của doanh nghiệp."):t("Tạo mật khẩu và kiểm tra lại trước khi gửi email xác minh.")}</p></>}
      {step===1 && <div className="mt-8 space-y-5"><Input label={t("Họ và tên")} autoComplete="name" placeholder="Nguyễn Văn An" error={localizeError(form.formState.errors.fullName?.message)} {...form.register('fullName')}/><Input label={t("Số điện thoại")} type="tel" autoComplete="tel" placeholder="090 123 4567" error={localizeError(form.formState.errors.phoneNumber?.message)} {...form.register('phoneNumber')}/><Input label={t("Email công việc")} type="email" autoComplete="email" placeholder="admin@doanhnghiep.vn" error={localizeError(form.formState.errors.email?.message)} {...form.register('email')}/><Button type="button" size="lg" className="w-full" onClick={nextIdentity}>{t("Tiếp tục")}{" "}<ArrowRight size={17}/></Button></div>}
      {step===2 && <form onSubmit={form.handleSubmit(submit)} className="mt-8 space-y-5"><div className="rounded-xl border border-slate-200 bg-slate-50 p-4"><p className="text-xs text-slate-500">{t("Email xác minh sẽ được gửi tới")}</p><p className="mt-1 font-medium text-slate-950">{form.getValues('email')}</p></div><Input label={t("Mật khẩu")} type="password" autoComplete="new-password" placeholder={t("Tối thiểu 8 ký tự")} error={localizeError(form.formState.errors.password?.message)} {...form.register('password')}/><Input label={t("Xác nhận mật khẩu")} type="password" autoComplete="new-password" error={localizeError(form.formState.errors.confirmPassword?.message)} {...form.register('confirmPassword')}/>{error&&<p role="alert" className="rounded-lg bg-red-50 p-3 text-sm text-red-700">{t(error)}</p>}<div className="flex gap-3"><Button type="button" variant="outline" size="lg" onClick={()=>setStep(1)}><ArrowLeft size={17}/>{t("Quay lại")}</Button><Button type="submit" size="lg" className="flex-1" isLoading={busy}>{t("Tạo tài khoản")}</Button></div></form>}
      {step===3 && <div className="text-center"><div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-blue-50 text-blue-700"><Mail size={26}/></div><h2 className="mt-6 text-3xl font-semibold tracking-tight text-slate-950">{t("Kiểm tra hộp thư")}</h2><p className="mx-auto mt-3 max-w-md text-sm leading-6 text-slate-600">{t("SwiftRoute đã gửi liên kết xác minh tới")}{" "}<strong className="text-slate-900">{submittedEmail}</strong>{t(". Liên kết có hiệu lực trong 24 giờ và chỉ dùng một lần.")}</p><div className="mt-7 grid gap-3 text-left sm:grid-cols-2"><div className="rounded-xl border border-slate-200 p-4"><ShieldCheck size={20}/><p className="mt-3 text-sm font-medium">{t("Xác minh danh tính")}</p><p className="mt-1 text-xs leading-5 text-slate-500">{t("Bấm liên kết trong email để kích hoạt tài khoản.")}</p></div><div className="rounded-xl border border-slate-200 p-4"><Building2 size={20}/><p className="mt-3 text-sm font-medium">{t("Tạo doanh nghiệp")}</p><p className="mt-1 text-xs leading-5 text-slate-500">{t("Đăng nhập và tiếp tục hồ sơ doanh nghiệp.")}</p></div></div>{notice&&<p role="status" className="mt-4 text-sm text-emerald-700">{t(notice)}</p>}{error&&<p role="alert" className="mt-4 text-sm text-red-700">{t(error)}</p>}<div className="mt-7 flex flex-col gap-3"><Button onClick={resend} variant="outline" isLoading={busy}>{t("Gửi lại email xác minh")}</Button><Link href="/login" className="text-sm font-medium text-blue-700 underline underline-offset-4">{t("Tôi đã xác minh — đăng nhập")}</Link></div></div>}
      <p className="mt-8 text-center text-sm text-slate-500">{t("Đã có tài khoản?")}{" "}<Link href="/login" className="font-medium text-slate-900 underline underline-offset-4">{t("Đăng nhập")}</Link></p>
    </div></section>
  </div></main>;
}
