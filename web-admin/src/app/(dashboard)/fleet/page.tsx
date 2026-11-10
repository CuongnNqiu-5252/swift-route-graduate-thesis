'use client';

import { FormEvent, useCallback, useEffect, useMemo, useState } from 'react';
import { Plus, RefreshCw, Truck, Wrench } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Driver } from '@/domain/entities/driver.entity';
import { Vehicle, VehicleStatus, VehicleType } from '@/domain/entities/vehicle.entity';
import { driverRepository } from '@/infrastructure/repositories/driver.repository';
import { vehicleRepository } from '@/infrastructure/repositories/vehicle.repository';

const labels: Record<VehicleStatus, string> = { AVAILABLE: 'Sẵn sàng', ASSIGNED: 'Đã phân công', MAINTENANCE: 'Bảo trì', INACTIVE: 'Ngừng hoạt động' };

function tenantId() {
  const direct = localStorage.getItem('tenant_id');
  if (direct) return direct;
  try { const user = JSON.parse(localStorage.getItem('auth_user') || '{}'); return user.tenantId || user.tenant_id || ''; } catch { return ''; }
}

export default function FleetPage() {
  const [vehicles, setVehicles] = useState<Vehicle[]>([]);
  const [drivers, setDrivers] = useState<Driver[]>([]);
  const [showForm, setShowForm] = useState(false);
  const [busy, setBusy] = useState(true);
  const [plate, setPlate] = useState(''); const [type, setType] = useState<VehicleType>('MOTORCYCLE');
  const [capacity, setCapacity] = useState(''); const [make, setMake] = useState(''); const [model, setModel] = useState('');

  const load = useCallback(async () => {
    try { const [fleet, people] = await Promise.all([vehicleRepository.list(), driverRepository.getDrivers()]); setVehicles(fleet); setDrivers(people); }
    catch (error) { toast.error(error instanceof Error ? error.message : 'Không thể tải đội xe'); }
    finally { setBusy(false); }
  }, []);
  useEffect(() => {
    let active = true;
    Promise.all([vehicleRepository.list(), driverRepository.getDrivers()])
      .then(([fleet, people]) => {
        if (active) {
          setVehicles(fleet);
          setDrivers(people);
        }
      })
      .catch((error) => {
        toast.error(error instanceof Error ? error.message : 'Không thể tải đội xe');
      })
      .finally(() => {
        if (active) setBusy(false);
      });
    return () => { active = false; };
  }, []);
  const counts = useMemo(() => ({ ready: vehicles.filter(v => v.status === 'AVAILABLE').length, assigned: vehicles.filter(v => v.status === 'ASSIGNED').length }), [vehicles]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    const tenant = tenantId(); if (!tenant) return toast.error('Tài khoản chưa có tenant để tạo phương tiện.');
    setBusy(true);
    try { await vehicleRepository.create({ tenantId: tenant, licensePlate: plate, vehicleType: type, capacity: capacity ? Number(capacity) : undefined, make, model }); toast.success('Đã thêm phương tiện'); setPlate(''); setShowForm(false); await load(); }
    catch (error) { toast.error(error instanceof Error ? error.message : 'Không thể tạo phương tiện'); setBusy(false); }
  }

  async function assign(vehicleId: string, driverId: string) {
    try { await vehicleRepository.assign(vehicleId, driverId || undefined); toast.success(driverId ? 'Đã phân công phương tiện' : 'Đã gỡ phân công'); await load(); }
    catch (error) { toast.error(error instanceof Error ? error.message : 'Không thể phân công'); }
  }

  return <div className="space-y-6 p-6">
    <header className="flex flex-col gap-4 border-b border-slate-200 pb-5 sm:flex-row sm:items-end sm:justify-between">
      <div><h1 className="text-2xl font-bold tracking-tight text-slate-950">Đội xe</h1><p className="mt-1 max-w-2xl text-sm text-slate-600">Quản lý tài sản vận hành, tình trạng và người đang phụ trách từng phương tiện.</p></div>
      <div className="flex gap-2"><Button variant="outline" onClick={() => void load()} disabled={busy}><RefreshCw className={`mr-2 h-4 w-4 ${busy ? 'animate-spin' : ''}`} />Làm mới</Button><Button onClick={() => setShowForm(v => !v)}><Plus className="mr-2 h-4 w-4" />Thêm phương tiện</Button></div>
    </header>
    <div className="flex gap-8 border-b border-slate-200 pb-4 text-sm"><span><strong className="mr-2 text-xl tabular-nums text-slate-950">{vehicles.length}</strong>Tổng đội xe</span><span><strong className="mr-2 text-xl tabular-nums text-emerald-700">{counts.ready}</strong>Sẵn sàng</span><span><strong className="mr-2 text-xl tabular-nums text-blue-700">{counts.assigned}</strong>Đã phân công</span></div>
    {showForm && <form onSubmit={submit} className="grid gap-4 border-b border-slate-200 bg-slate-50 p-5 sm:grid-cols-2 lg:grid-cols-6">
      <label className="text-xs font-semibold text-slate-700">Biển số<input required value={plate} onChange={e=>setPlate(e.target.value)} className="mt-1 w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm uppercase" /></label>
      <label className="text-xs font-semibold text-slate-700">Loại<select value={type} onChange={e=>setType(e.target.value as VehicleType)} className="mt-1 w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm"><option value="MOTORCYCLE">Xe máy</option><option value="CAR">Ô tô</option><option value="VAN">Xe tải nhẹ</option></select></label>
      <label className="text-xs font-semibold text-slate-700">Tải trọng<input type="number" min="0" value={capacity} onChange={e=>setCapacity(e.target.value)} className="mt-1 w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm" /></label>
      <label className="text-xs font-semibold text-slate-700">Hãng<input value={make} onChange={e=>setMake(e.target.value)} className="mt-1 w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm" /></label>
      <label className="text-xs font-semibold text-slate-700">Dòng xe<input value={model} onChange={e=>setModel(e.target.value)} className="mt-1 w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm" /></label>
      <div className="flex items-end"><Button type="submit" disabled={busy} className="w-full">Lưu phương tiện</Button></div>
    </form>}
    <div className="overflow-x-auto border-y border-slate-200"><table className="w-full text-left text-sm"><thead><tr className="border-b border-slate-200 text-xs uppercase tracking-wide text-slate-500"><th className="px-4 py-3">Phương tiện</th><th className="px-4 py-3">Tải trọng</th><th className="px-4 py-3">Trạng thái</th><th className="px-4 py-3">Tài xế phụ trách</th></tr></thead><tbody>
      {vehicles.map(v => <tr key={v.id} className="border-b border-slate-100 hover:bg-slate-50"><td className="px-4 py-4"><div className="flex items-center gap-3"><Truck className="h-5 w-5 text-blue-600"/><div><p className="font-semibold text-slate-950">{v.licensePlate}</p><p className="text-xs text-slate-500">{[v.make, v.model, v.vehicleType].filter(Boolean).join(' · ')}</p></div></div></td><td className="px-4 py-4 tabular-nums">{v.capacity ?? '—'}</td><td className="px-4 py-4"><span className="inline-flex items-center gap-1.5 rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-700">{v.status === 'MAINTENANCE' && <Wrench className="h-3 w-3"/>}{labels[v.status]}</span></td><td className="px-4 py-4"><select aria-label={`Tài xế cho ${v.licensePlate}`} value={v.assignedDriverId || ''} onChange={e=>void assign(v.id,e.target.value)} className="w-full max-w-xs rounded-lg border border-slate-300 bg-white px-3 py-2"><option value="">Chưa phân công</option>{drivers.map(d=><option key={d.id} value={d.id}>{d.name}</option>)}</select></td></tr>)}
      {!busy && vehicles.length === 0 && <tr><td colSpan={4} className="px-4 py-16 text-center text-slate-500">Chưa có phương tiện. Thêm xe đầu tiên để bắt đầu quản lý đội xe.</td></tr>}
    </tbody></table></div>
  </div>;
}
