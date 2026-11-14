'use client';

import { useLocale } from '@/i18n/provider';

import { FormEvent, useEffect, useState } from 'react';
import { Banknote, CheckCircle2, MapPin } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import LocationMapPicker from '@/components/locations/location-map-picker';
import { PickupLocation } from '@/domain/entities/pickup-location.entity';
import { pickupLocationRepository } from '@/infrastructure/repositories/pickup-location.repository';
import { orderRepository } from '@/infrastructure/repositories/order.repository';

function numberToVietnameseWords(num: number): string {
  if (!num || num <= 0) return '';
  const digits = ['không', 'một', 'hai', 'ba', 'bốn', 'năm', 'sáu', 'bảy', 'tám', 'chín'];
  const units = ['', 'nghìn', 'triệu', 'tỷ'];

  function readGroup(n: number, isHead: boolean): string {
    const h = Math.floor(n / 100);
    const t = Math.floor((n % 100) / 10);
    const u = n % 10;
    if (h === 0 && t === 0 && u === 0) return '';
    let res = '';
    if (h > 0 || !isHead) {
      res += `${digits[h]} trăm `;
    }
    if (t > 1) {
      res += `${digits[t]} mươi `;
      if (u === 1) res += 'mốt';
      else if (u === 5) res += 'lăm';
      else if (u > 0) res += digits[u];
    } else if (t === 1) {
      res += 'mười ';
      if (u === 5) res += 'lăm';
      else if (u > 0) res += digits[u];
    } else if (t === 0 && u > 0) {
      if (h > 0 || !isHead) res += `lẻ ${digits[u]}`;
      else res += digits[u];
    }
    return res.trim();
  }

  let str = '';
  let i = 0;
  let temp = num;
  while (temp > 0) {
    const block = temp % 1000;
    if (block > 0) {
      const bStr = readGroup(block, temp < 1000);
      str = `${bStr} ${units[i]} ${str}`.trim();
    }
    temp = Math.floor(temp / 1000);
    i++;
  }
  const full = `${str} đồng`.trim();
  return full.charAt(0).toUpperCase() + full.slice(1);
}

export function OrderCreatePanel({ onCreated, onClose }: { onCreated: () => void; onClose: () => void }) {
  const { t, intlLocale } = useLocale();
  const [locations, setLocations] = useState<PickupLocation[]>([]);
  const [pickupId, setPickupId] = useState('');
  const [recipient, setRecipient] = useState('');
  const [phone, setPhone] = useState('');
  const [address, setAddress] = useState('');
  const [lat, setLat] = useState(10.03);
  const [lng, setLng] = useState(105.78);
  const [weight, setWeight] = useState('1');
  const [hasCod, setHasCod] = useState(false);
  const [codThousands, setCodThousands] = useState('');
  const [busy, setBusy] = useState(false);
  const [resolvingAddress, setResolvingAddress] = useState(false);
  const [locationSelected, setLocationSelected] = useState(false);

  const parsedCodAmount = hasCod && codThousands ? Number(codThousands) * 1000 : 0;

  useEffect(() => {
    pickupLocationRepository.getPickupLocations('ACTIVE')
      .then((data) => {
        setLocations(data);
        const chosen = data.find((location) => location.is_default) || data[0];
        if (chosen) setPickupId(chosen.id);
      })
      .catch(() => toast.error(t("Không tải được điểm lấy hàng")));
  }, [t]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    const pickup = locations.find((location) => location.id === pickupId);
    if (!pickup) return toast.error(t("Hãy chọn điểm lấy hàng."));
    if (!locationSelected) return toast.error(t("Hãy tìm địa chỉ hoặc đặt ghim tại điểm giao."));
    if (!address.trim()) return toast.error(t("Chưa xác định được địa chỉ giao hàng."));
    const finalCodAmount = hasCod && codThousands ? Number(codThousands) * 1000 : 0;
    if (hasCod && (!Number.isInteger(finalCodAmount) || finalCodAmount <= 0)) {
      return toast.error(t("Tiền thu hộ COD phải là số nguyên lớn hơn 0."));
    }

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
        packageCategory: 'PARCEL',
        priority: 'NORMAL',
        codAmount: finalCodAmount,
      });
      toast.success(t("Đã tạo đơn hàng"));
      onCreated();
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t("Không thể tạo đơn"));
    } finally {
      setBusy(false);
    }
  }

  return <form onSubmit={submit} className="grid gap-5 border-y border-slate-200 bg-slate-50 p-5 lg:grid-cols-[0.8fr_1.2fr]">
    <div className="space-y-4">
      <div>
        <h2 className="text-lg font-bold text-slate-950">{t("Tạo đơn hàng")}</h2>
        <p className="mt-1 text-sm text-slate-600">{t("Nhập người nhận, sau đó tìm hoặc đặt ghim tại điểm giao.")}</p>
      </div>
      <label className="block text-xs font-semibold text-slate-700">{t("Điểm lấy hàng")}
        <select required value={pickupId} onChange={(event) => setPickupId(event.target.value)} className="mt-1 w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm">
          <option value="">{t("Chọn kho")}</option>
          {locations.map((location) => <option key={location.id} value={location.id}>{location.name}</option>)}
        </select>
      </label>
      <div className="grid gap-3 sm:grid-cols-2">
        <label className="text-xs font-semibold text-slate-700">{t("Người nhận")}<input required value={recipient} onChange={(event) => setRecipient(event.target.value)} className="mt-1 w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm" /></label>
        <label className="text-xs font-semibold text-slate-700">{t("Số điện thoại")}<input required value={phone} onChange={(event) => setPhone(event.target.value)} className="mt-1 w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm" /></label>
      </div>
      <fieldset className="rounded-xl border border-slate-200 bg-white p-4">
        <label className="flex min-h-11 cursor-pointer items-center gap-3">
          <input
            type="checkbox"
            checked={hasCod}
            onChange={(event) => {
              setHasCod(event.target.checked);
              if (!event.target.checked) setCodThousands('');
            }}
            className="h-5 w-5 rounded border-slate-300 text-emerald-700 focus:ring-emerald-600"
          />
          <Banknote className="h-5 w-5 text-emerald-700" aria-hidden="true" />
          <span>
            <span className="block text-sm font-semibold text-slate-900">{t("Thu hộ COD khi giao hàng")}</span>
            <span className="block text-xs font-normal text-slate-500">{t("Tài xế phải ghi nhận số tiền thực thu trước khi bàn giao cho quầy.")}</span>
          </span>
        </label>
        {hasCod && (
          <div className="mt-3 space-y-2">
            <label className="block text-xs font-semibold text-slate-700">
              {t("Số tiền cần thu từ người nhận (VND)")}
              <div className="mt-1 flex min-h-11 items-center overflow-hidden rounded-lg border border-slate-300 bg-white focus-within:border-emerald-600 focus-within:ring-2 focus-within:ring-emerald-100">
                <input
                  required
                  autoFocus
                  inputMode="numeric"
                  type="text"
                  value={codThousands}
                  onChange={(event) => {
                    let val = event.target.value.replace(/[^\d]/g, '');
                    // Nếu nhập hoặc dán đầy đủ đuôi 000 (ví dụ: 150000), tự động quy đổi về hàng nghìn (150)
                    if (val.length > 3 && val.endsWith('000') && Number(val) >= 1000) {
                      val = String(Math.floor(Number(val) / 1000));
                    }
                    setCodThousands(val);
                  }}
                  placeholder={t("Ví dụ: 1 (= 1.000đ), 150 (= 150.000đ)")}
                  className="min-w-0 flex-1 px-3 py-2 text-base font-semibold tabular-nums text-slate-950 outline-none"
                />
                <span className="select-none px-2 text-base font-bold text-emerald-700">
                  .000
                </span>
                <span className="border-l border-slate-200 bg-slate-50 px-3 py-2.5 text-sm font-medium text-slate-600">
                  VND
                </span>
              </div>
            </label>

            {parsedCodAmount > 0 ? (
              <div className="flex flex-wrap items-center justify-between gap-1 rounded-md bg-emerald-50 px-3 py-2 text-xs text-emerald-800">
                <span className="font-semibold">
                  {t("Thực thu COD:")}{" "}{new Intl.NumberFormat(intlLocale).format(parsedCodAmount)} ₫
                </span>
                <span className="text-emerald-700">
                  ({numberToVietnameseWords(parsedCodAmount)})
                </span>
              </div>
            ) : (
              <p className="text-[11px] text-slate-500">
                {t("* Tự động thêm 3 số 0 (nhập")}{" "}<strong>1</strong>{" "}{t("thành")}{" "}<strong>1.000 ₫</strong>{t(", nhập")}{" "}<strong>150</strong>{" "}{t("thành")}{" "}<strong>150.000 ₫</strong>).
              </p>
            )}

            <div className="flex flex-wrap items-center gap-1.5 pt-0.5">
              <span className="text-[11px] text-slate-500">{t("Chọn nhanh:")}</span>
              {[
                { label: '30k', val: '30' },
                { label: '50k', val: '50' },
                { label: '100k', val: '100' },
                { label: '150k', val: '150' },
                { label: '200k', val: '200' },
                { label: '500k', val: '500' },
              ].map((item) => (
                <button
                  key={item.val}
                  type="button"
                  onClick={() => setCodThousands(item.val)}
                  className={`rounded border px-2 py-0.5 text-xs font-medium transition ${
                    codThousands === item.val
                      ? 'border-emerald-600 bg-emerald-600 text-white'
                      : 'border-slate-200 bg-slate-50 text-slate-700 hover:bg-slate-100'
                  }`}
                >
                  {item.label}
                </button>
              ))}
            </div>
          </div>
        )}
      </fieldset>
      <label className="block text-xs font-semibold text-slate-700">{t("Địa chỉ giao")}
        <textarea required rows={3} value={address} onChange={(event) => setAddress(event.target.value)} placeholder={t("Địa chỉ sẽ tự điền sau khi chọn vị trí trên bản đồ")} className="mt-1 w-full resize-none rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm" />
      </label>
      <div className="grid gap-3 sm:grid-cols-2">
        <label className="block text-xs font-semibold text-slate-700">{t("Tổng khối lượng (kg)")}<input required min="0.1" step="0.1" type="number" value={weight} onChange={(event) => setWeight(event.target.value)} className="mt-1 w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm" /></label>
      </div>
      <div className={`flex items-center gap-2 text-xs ${locationSelected ? 'text-emerald-700' : 'text-amber-700'}`}>
        {locationSelected ? <CheckCircle2 className="h-4 w-4" /> : <MapPin className="h-4 w-4" />}
        {locationSelected ? `Đã xác định tọa độ ${lat.toFixed(6)}, ${lng.toFixed(6)}` : t("Chưa chọn điểm giao trên bản đồ")}
      </div>
      <div className="flex gap-2">
        <Button type="submit" disabled={busy || resolvingAddress || !locationSelected}>{busy ? t("Đang tạo…") : resolvingAddress ? t("Đang lấy địa chỉ…") : t("Tạo đơn")}</Button>
        <Button type="button" variant="outline" onClick={onClose}>{t("Đóng")}</Button>
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
