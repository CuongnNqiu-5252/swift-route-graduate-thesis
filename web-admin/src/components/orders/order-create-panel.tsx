'use client';

import { FormEvent, useEffect, useState } from 'react';
import { CheckCircle2, MapPin } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import LocationMapPicker from '@/components/locations/location-map-picker';
import { PickupLocation } from '@/domain/entities/pickup-location.entity';
import { pickupLocationRepository } from '@/infrastructure/repositories/pickup-location.repository';
import { orderRepository } from '@/infrastructure/repositories/order.repository';

export function OrderCreatePanel({ onCreated, onClose }: { onCreated: () => void; onClose: () => void }) {
  const [locations, setLocations] = useState<PickupLocation[]>([]);
  const [pickupId, setPickupId] = useState('');
  const [recipient, setRecipient] = useState('');
  const [phone, setPhone] = useState('');
  const [address, setAddress] = useState('');
  const [lat, setLat] = useState(10.03);
  const [lng, setLng] = useState(105.78);
  const [weight, setWeight] = useState('1');
  const [packageCount, setPackageCount] = useState('1');
  const [busy, setBusy] = useState(false);
  const [resolvingAddress, setResolvingAddress] = useState(false);
  const [locationSelected, setLocationSelected] = useState(false);

  useEffect(() => {
    pickupLocationRepository.getPickupLocations('ACTIVE')
      .then((data) => {
        setLocations(data);
        const chosen = data.find((location) => location.is_default) || data[0];
        if (chosen) setPickupId(chosen.id);
      })
      .catch(() => toast.error('Không tải được điểm lấy hàng'));
  }, []);

  async function submit(event: FormEvent) {
    event.preventDefault();
    const pickup = locations.find((location) => location.id === pickupId);
    if (!pickup) return toast.error('Hãy chọn điểm lấy hàng.');
    if (!locationSelected) return toast.error('Hãy tìm địa chỉ hoặc đặt ghim tại điểm giao.');
    if (!address.trim()) return toast.error('Chưa xác định được địa chỉ giao hàng.');

    setBusy(true);
    const start = new Date(Date.now() + 60 * 60 * 1000);
    const end = new Date(Date.now() + 5 * 60 * 60 * 1000);
    try {
      await orderRepository.createOrder({
        recipientName: recipient,
        recipientPhone: phone,
        pickupLocationId: pickup.id,
        pickupLat: pickup.lat,
        pickupLng: pickup.lng,
        pickupAddress: pickup.address,
        deliveryLat: lat,
        deliveryLng: lng,
        deliveryAddress: address.trim(),
        timeWindowStart: start.toISOString(),
        timeWindowEnd: end.toISOString(),
        weightKg: Number(weight),
        packageCount: Number(packageCount),
        packageCategory: 'PARCEL',
        priority: 'NORMAL',
      });
      toast.success('Đã tạo đơn hàng');
      onCreated();
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Không thể tạo đơn');
    } finally {
      setBusy(false);
    }
  }

  return <form onSubmit={submit} className="grid gap-5 border-y border-slate-200 bg-slate-50 p-5 lg:grid-cols-[0.8fr_1.2fr]">
    <div className="space-y-4">
      <div>
        <h2 className="text-lg font-bold text-slate-950">Tạo đơn hàng</h2>
        <p className="mt-1 text-sm text-slate-600">Nhập người nhận, sau đó tìm hoặc đặt ghim tại điểm giao.</p>
      </div>
      <label className="block text-xs font-semibold text-slate-700">Điểm lấy hàng
        <select required value={pickupId} onChange={(event) => setPickupId(event.target.value)} className="mt-1 w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm">
          <option value="">Chọn kho</option>
          {locations.map((location) => <option key={location.id} value={location.id}>{location.name}</option>)}
        </select>
      </label>
      <div className="grid gap-3 sm:grid-cols-2">
        <label className="text-xs font-semibold text-slate-700">Người nhận<input required value={recipient} onChange={(event) => setRecipient(event.target.value)} className="mt-1 w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm" /></label>
        <label className="text-xs font-semibold text-slate-700">Số điện thoại<input required value={phone} onChange={(event) => setPhone(event.target.value)} className="mt-1 w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm" /></label>
      </div>
      <label className="block text-xs font-semibold text-slate-700">Địa chỉ giao
        <textarea required rows={3} value={address} onChange={(event) => setAddress(event.target.value)} placeholder="Địa chỉ sẽ tự điền sau khi chọn vị trí trên bản đồ" className="mt-1 w-full resize-none rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm" />
      </label>
      <div className="grid gap-3 sm:grid-cols-2">
        <label className="block text-xs font-semibold text-slate-700">Số kiện<input required min="1" max="100" step="1" type="number" value={packageCount} onChange={(event) => setPackageCount(event.target.value)} className="mt-1 w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm" /></label>
        <label className="block text-xs font-semibold text-slate-700">Tổng khối lượng (kg)<input required min="0.1" step="0.1" type="number" value={weight} onChange={(event) => setWeight(event.target.value)} className="mt-1 w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm" /></label>
      </div>
      <div className={`flex items-center gap-2 text-xs ${locationSelected ? 'text-emerald-700' : 'text-amber-700'}`}>
        {locationSelected ? <CheckCircle2 className="h-4 w-4" /> : <MapPin className="h-4 w-4" />}
        {locationSelected ? `Đã xác định tọa độ ${lat.toFixed(6)}, ${lng.toFixed(6)}` : 'Chưa chọn điểm giao trên bản đồ'}
      </div>
      <div className="flex gap-2">
        <Button type="submit" disabled={busy || resolvingAddress || !locationSelected}>{busy ? 'Đang tạo…' : resolvingAddress ? 'Đang lấy địa chỉ…' : 'Tạo đơn'}</Button>
        <Button type="button" variant="outline" onClick={onClose}>Đóng</Button>
      </div>
    </div>
    <div className="overflow-hidden rounded-xl border border-slate-200 bg-white">
      <LocationMapPicker
        lat={lat}
        lng={lng}
        address={address}
        onResolvingChange={setResolvingAddress}
        onChange={(nextLat, nextLng, resolvedAddress) => {
          setLat(nextLat); setLng(nextLng); setLocationSelected(true);
          setAddress(resolvedAddress ?? '');
        }}
      />
    </div>
  </form>;
}
