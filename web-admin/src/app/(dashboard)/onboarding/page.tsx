'use client';
import { useCallback, useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { useAuth } from '@/application/contexts/auth-context';
import { Progress, TenantProfile, tenantWorkflow } from '@/infrastructure/repositories/tenant-workflow.repository';
import { pickupLocationRepository } from '@/infrastructure/repositories/pickup-location.repository';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import LocationMapPicker from '@/components/locations/location-map-picker';

const labels: Record<string,string> = { BUSINESS_PROFILE:'Hồ sơ doanh nghiệp',PICKUP_POINT:'Điểm lấy hàng',DELIVERY_MODE:'Phương thức giao hàng',DELIVERY_SETTINGS:'Cấu hình giao hàng',DRIVER_SETUP:'Thiết lập tài xế',VERIFICATION_SUBMISSION:'Gửi xét duyệt' };
export default function OnboardingPage() {
  const { user } = useAuth(); const id=user?.tenantId;
  const [progress,setProgress]=useState<Progress|null>(null); const [profile,setProfile]=useState<TenantProfile|null>(null);
  const [step,setStep]=useState('BUSINESS_PROFILE');const [error,setError]=useState('');const [busy,setBusy]=useState(false);
  const initializedTenant=useRef<string | null>(null);
  const [owner,setOwner]=useState(false);const [pickup,setPickup]=useState({id:'',name:'',address:'',lat:'',lng:''});
  const load=useCallback(async () => {
    if(!id)return;
    try {const p=await tenantWorkflow.progress(id);setProgress(p);setProfile(p.profile);const members=await tenantWorkflow.memberships();setOwner(members.some(m=>m.tenant_id===id&&m.role==='OWNER'));
      if(initializedTenant.current!==id){
        initializedTenant.current=id;setStep(p.next_action==='VIEW_VERIFICATION'?'VERIFICATION_SUBMISSION':p.next_action);
        const locations=await pickupLocationRepository.getPickupLocations('ACTIVE');const depot=locations.find(x=>x.is_default)||locations[0];
        if(depot)setPickup({id:depot.id,name:depot.name,address:depot.address,lat:String(depot.lat),lng:String(depot.lng)});
      }}
    catch(e){setError(e instanceof Error?e.message:'Không tải được tiến độ');}
  },[id]);
  useEffect(()=>{const task=window.setTimeout(()=>{void load();},0);return ()=>window.clearTimeout(task);},[load]);
  const steps=Object.keys(labels).filter(s=>s!=='DRIVER_SETUP'||profile?.delivery_mode!=='SHARED_POOL');
  const index=steps.indexOf(step);
  async function save(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();if(!id||!profile)return;setBusy(true);setError('');
    try {
      if(step==='PICKUP_POINT') {
        if(!pickup.lat||!pickup.lng)throw new Error('Chọn tọa độ điểm lấy hàng trên bản đồ hoặc nhập tọa độ.');
        const payload={name:pickup.name,address:pickup.address,lat:Number(pickup.lat),lng:Number(pickup.lng),contactPhone:profile.contact_phone,isDefault:true};
        const saved=pickup.id?await pickupLocationRepository.updatePickupLocation(pickup.id,payload):await pickupLocationRepository.createPickupLocation(payload);
        setPickup({...pickup,id:saved.id});
      } else if(step==='BUSINESS_PROFILE') {
        await tenantWorkflow.save(id,{business_name:profile.business_name,business_type:profile.business_type,contact_phone:profile.contact_phone,address:profile.address,country_code:profile.country_code});
      } else if(step==='DELIVERY_MODE') await tenantWorkflow.save(id,{delivery_mode:profile.delivery_mode});
      else if(step==='DELIVERY_SETTINGS') await tenantWorkflow.save(id,{delivery_settings:profile.delivery_settings});
      await load();setStep(steps[Math.min(index+1,steps.length-1)]);
    } catch(e){setError(e instanceof Error?e.message:'Lưu thất bại');}finally{setBusy(false);}
  }
  async function submit() {
    if(!id)return;setBusy(true);setError('');
    const storageKey=`verification-key:${id}`;
    const current=progress?.verification_requests[0];const intent=`${current?.request_id||'initial'}:${current?.status||'DRAFT'}`;
    let cached: { intent: string; key: string } | null=null;
    try{cached=JSON.parse(sessionStorage.getItem(storageKey)||'null');}catch{/* replace an obsolete draft */}
    const key=cached?.intent===intent?cached.key:crypto.randomUUID();sessionStorage.setItem(storageKey,JSON.stringify({intent,key}));
    try{await tenantWorkflow.submit(id,key);sessionStorage.removeItem(storageKey);await load();}catch(e){setError(e instanceof Error?e.message:'Gửi thất bại');}finally{setBusy(false);}
  }
  if(!id)return <p>Chọn hoặc tạo tổ chức tại <Link href="/organizations" className="text-blue-600 underline">Tổ chức của bạn</Link>.</p>;
  if(!progress||!profile)return <div><p role="status">Đang tải workspace…</p>{error&&<p role="alert">{error}</p>}<Button onClick={load}>Thử lại</Button></div>;
  const latest=progress.verification_requests[0];
  function field(name: keyof Pick<TenantProfile,'business_name'|'contact_phone'|'address'|'country_code'>,label:string,minLength:number) {
    return <Input label={label} required minLength={minLength} value={profile![name]||''} onChange={e=>setProfile({...profile!,[name]:e.target.value})}/>;
  }
  return <div className="mx-auto max-w-4xl space-y-5">
    <div className="flex items-center justify-between"><h1 className="text-2xl font-semibold">Thiết lập {progress.tenant.business_name}</h1><Button variant="outline" onClick={load}>Cập nhật trạng thái</Button></div>
    {error&&<p role="alert" className="rounded bg-red-50 p-3 text-red-700">{error}</p>}
    <Card><CardContent className="space-y-2 pt-6"><p>Doanh nghiệp: <strong>{progress.tenant.status}</strong> · Hồ sơ xét duyệt: <strong>{latest?.status||'CHƯA GỬI'}</strong></p>
      {latest?.reason&&<p className="rounded bg-amber-50 p-3">Phản hồi: {latest.reason}</p>}
      <p>{progress.readiness.ready_for_operations?'Sẵn sàng vận hành.':'Điều kiện còn thiếu: '+progress.readiness.missing_requirements.join(', ')}</p>
      <p className="text-sm text-gray-500">Bạn có thể tiếp tục thiết lập khi đang chờ duyệt. Các tính năng hiện có được mở sau phê duyệt và khi đáp ứng điều kiện nghiệp vụ.</p>
    </CardContent></Card>
    <div aria-label="Tiến độ onboarding" className="flex flex-wrap gap-2">{steps.map(s=><button key={s} onClick={()=>setStep(s)} className={`rounded border px-3 py-2 text-sm ${step===s?'border-blue-600 bg-blue-50':''}`}>{progress.completed_steps.includes(s)?'? ':''}{labels[s]}</button>)}</div>
    <p role="status">Đã hoàn tất {progress.completed_steps.length}/{steps.length} bước. Tiếp theo: {labels[progress.next_action]||'Xem kết quả xét duyệt'}.</p>
    {!owner&&<p>Bạn có thể xem tiến độ. Chủ sở hữu tổ chức thực hiện thiết lập và gửi hồ sơ.</p>}
    <Card><CardHeader><CardTitle>{labels[step]}</CardTitle></CardHeader><CardContent>
      <form onSubmit={save} className="space-y-4"><fieldset disabled={!owner||busy} className="space-y-4">
      {step==='BUSINESS_PROFILE'&&<>{field('business_name','Tên doanh nghiệp',2)}{field('contact_phone','Số điện thoại',9)}{field('address','Địa chỉ',5)}{field('country_code','Mã quốc gia',2)}<label className="block">Loại hình<select className="ml-3 rounded border p-2" value={profile.business_type} onChange={e=>setProfile({...profile,business_type:e.target.value})}>{['RESTAURANT','CAFE','RETAIL','PHARMACY','OTHER'].map(x=><option key={x}>{x}</option>)}</select></label></>}
      {step==='PICKUP_POINT'&&<><p><Link href="/locations" className="text-blue-600 underline">Quản lý hoặc chỉnh sửa điểm lấy hàng đã có</Link></p><Input label="Tên điểm lấy hàng" required value={pickup.name} onChange={e=>setPickup({...pickup,name:e.target.value})}/><Input label="Địa chỉ" required value={pickup.address} onChange={e=>setPickup({...pickup,address:e.target.value})}/><LocationMapPicker lat={pickup.lat?Number(pickup.lat):10.0452} lng={pickup.lng?Number(pickup.lng):105.7469} address={pickup.address} onChange={(lat,lng,address)=>setPickup(p=>({...p,lat:String(lat),lng:String(lng),address:address||p.address}))}/><div className="grid grid-cols-2 gap-3"><Input label="Vĩ độ" required type="number" step="any" min={-90} max={90} value={pickup.lat} onChange={e=>setPickup({...pickup,lat:e.target.value})}/><Input label="Kinh độ" required type="number" step="any" min={-180} max={180} value={pickup.lng} onChange={e=>setPickup({...pickup,lng:e.target.value})}/></div></>}
      {step==='DELIVERY_MODE'&&<><label>Phương thức<select className="ml-3 rounded border p-2" value={profile.delivery_mode} onChange={e=>setProfile({...profile,delivery_mode:e.target.value})}>{['OWN_FLEET','SHARED_POOL','HYBRID'].map(x=><option key={x}>{x}</option>)}</select></label><p>OWN_FLEET sử dụng đội xe riêng. HYBRID hiện chỉ vận hành bằng đội xe riêng. Shared Pool chưa sẵn sàng; lựa chọn được lưu nhưng chưa thể giao qua mạng dùng chung.</p></>}
      {step==='DELIVERY_SETTINGS'&&<><label className="block"><input type="checkbox" checked={profile.delivery_settings.preparation_enabled} onChange={e=>setProfile({...profile,delivery_settings:{...profile.delivery_settings,preparation_enabled:e.target.checked}})}/> Có chuẩn bị hàng</label><Input label="Thời gian chuẩn bị (phút)" type="number" min={0} max={240} required value={profile.delivery_settings.preparation_minutes} onChange={e=>setProfile({...profile,delivery_settings:{...profile.delivery_settings,preparation_minutes:Number(e.target.value)}})}/>{(['delivery_verification_required','sensitive_handling'] as const).map(k=><label key={k} className="block"><input type="checkbox" checked={profile.delivery_settings[k]} onChange={e=>setProfile({...profile,delivery_settings:{...profile.delivery_settings,[k]:e.target.checked}})}/> {k==='sensitive_handling'?'Xử lý hàng nhạy cảm':'Yêu cầu xác nhận giao hàng'}</label>)}</>}
      {step==='DRIVER_SETUP'&&<p><Link href="/members" className="text-blue-600 underline">Mời thành viên tài xế</Link> rồi <Link href="/drivers" className="text-blue-600 underline">thiết lập tài xế</Link>. Hệ thống tự kiểm tra tài xế hợp lệ; không dùng cờ hoàn tất từ trình duyệt.</p>}
      {step==='VERIFICATION_SUBMISSION'&&<><p>Kiểm tra thông tin trước khi gửi. Hồ sơ đã gửi được lưu thành snapshot để xét duyệt.</p><pre className="overflow-auto rounded bg-gray-50 p-3 text-xs">{JSON.stringify(profile,null,2)}</pre><Button type="button" onClick={submit} isLoading={busy} disabled={!!latest&&['SUBMITTED','UNDER_REVIEW','APPROVED'].includes(latest.status)}>Gửi / Gửi lại hồ sơ xét duyệt</Button></>}
      </fieldset><div className="flex justify-between"><Button variant="outline" type="button" disabled={index===0} onClick={()=>setStep(steps[index-1])}>Quay lại</Button>{step!=='VERIFICATION_SUBMISSION'&&<Button type="submit" disabled={!owner} isLoading={busy}>{step==='DRIVER_SETUP'?'Kiểm tra và tiếp tục':'Lưu và tiếp tục'}</Button>}</div></form>
    </CardContent></Card>
    <Card><CardHeader><CardTitle>Lịch sử xét duyệt</CardTitle></CardHeader><CardContent>{progress.verification_requests.length===0?<p>Chưa có hồ sơ. Hoàn tất thông tin và gửi xét duyệt.</p>:progress.verification_requests.map(v=><div key={v.request_id} className="space-y-1 border-b py-3"><p>{v.status} · {v.submitted_at} · {v.reason}</p><details><summary>Thông tin đã gửi</summary><pre className="overflow-auto text-xs">{JSON.stringify(JSON.parse(v.snapshot),null,2)}</pre></details></div>)}</CardContent></Card>
  </div>;
}
