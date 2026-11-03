'use client';
import { useCallback, useEffect, useState } from 'react';
import { useAuth } from '@/application/contexts/auth-context';
import { tenantWorkflow, Verification } from '@/infrastructure/repositories/tenant-workflow.repository';
import { Button } from '@/components/ui/button';
import { isSystemAdmin } from '@/lib/portal-access';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
export default function TenantVerificationsPage() {
  const { user }=useAuth();const [requests,setRequests]=useState<Verification[]>([]);const [error,setError]=useState('');
  const [reason,setReason]=useState('');const [selected,setSelected]=useState<Verification|null>(null);const [decision,setDecision]=useState('');const [busy,setBusy]=useState(false);
  const allowed=isSystemAdmin(user?.role);
  const load=useCallback(async()=>{if(!allowed)return;try{setRequests(await tenantWorkflow.reviews());}catch(e){setError(e instanceof Error?e.message:'Không tải được danh sách');}},[allowed]);
  useEffect(()=>{const task=window.setTimeout(()=>{void load();},0);return ()=>window.clearTimeout(task);},[load]);
  async function confirm(){if(!selected)return;setBusy(true);setError('');try{await tenantWorkflow.review(selected,decision,reason);setSelected(null);setReason('');await load();}catch(e){setError(e instanceof Error?e.message:'Xét duyệt thất bại');await load();}finally{setBusy(false);}}
  if(!allowed)return <p>Chỉ System Admin có quyền xét duyệt tổ chức.</p>;
  return <div className="mx-auto max-w-5xl space-y-5"><div className="flex justify-between"><h1 className="text-2xl font-semibold">Xét duyệt tổ chức</h1><Button onClick={load}>Tải lại</Button></div>{error&&<p role="alert" className="text-red-700">{error}</p>}{requests.length===0&&<p>Chưa có hồ sơ xét duyệt.</p>}
    {requests.map(v=><Card key={v.request_id}><CardHeader><CardTitle>{v.business_name} · {v.status}</CardTitle></CardHeader><CardContent className="space-y-3"><p>Gửi: {v.submitted_at} · Người duyệt: {v.reviewer_id||'—'} · {v.reviewed_at}</p>{v.reason&&<p>Phản hồi: {v.reason}</p>}<details><summary>Thông tin doanh nghiệp đã gửi</summary><pre className="overflow-auto rounded bg-gray-50 p-3 text-xs">{JSON.stringify(JSON.parse(v.snapshot),null,2)}</pre></details><p>Lịch sử: {requests.filter(x=>x.tenant_id===v.tenant_id).map(x=>x.status).join(' → ')}</p>{['SUBMITTED','UNDER_REVIEW'].includes(v.status)&&<div className="flex flex-wrap gap-2">{['UNDER_REVIEW','APPROVED','CHANGES_REQUIRED','REJECTED'].map(s=><Button key={s} variant={s==='REJECTED'?'destructive':'outline'} onClick={()=>{setSelected(v);setDecision(s);setReason('');}}>{s}</Button>)}</div>}</CardContent></Card>)}
    {selected&&<div role="dialog" aria-modal="true" aria-labelledby="review-title" className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"><div className="w-full max-w-lg space-y-4 rounded-xl bg-white p-6"><h2 id="review-title" className="text-lg font-semibold">Xác nhận {decision}</h2><p>{selected.business_name}. Quyết định sẽ được ghi vào lịch sử xét duyệt và thông báo đến chủ tổ chức.</p><label className="block">Lý do / phản hồi<textarea value={reason} onChange={e=>setReason(e.target.value)} maxLength={2000} className="mt-2 w-full rounded border p-2" required={['REJECTED','CHANGES_REQUIRED'].includes(decision)}/></label><div className="flex gap-3"><Button onClick={confirm} isLoading={busy} disabled={['REJECTED','CHANGES_REQUIRED'].includes(decision)&&!reason.trim()}>Xác nhận quyết định</Button><Button variant="outline" onClick={()=>setSelected(null)} disabled={busy}>Hủy</Button></div></div></div>}
  </div>;
}
