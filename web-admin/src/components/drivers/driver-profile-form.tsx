'use client';

import { FormEvent, useEffect, useMemo, useState } from 'react';
import { AlertCircle, CheckCircle2, UserRoundPlus } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { CreateDriverPayload, Driver } from '@/domain/entities/driver.entity';
import { TenantMember } from '@/domain/entities/member.entity';
import { getCurrentTenantId, memberRepository } from '@/infrastructure/repositories/member.repository';

interface DriverProfileFormProps {
  drivers: Driver[];
  onCreate: (payload: CreateDriverPayload) => Promise<Driver>;
}

const initialFields = {
  fullName: '',
  phone: '',
  licensePlate: '',
  vehicleType: 'MOTORCYCLE' as CreateDriverPayload['vehicleType'],
  capacity: 20,
  driverType: 'DEDICATED' as CreateDriverPayload['driverType'],
};

export function DriverProfileForm({ drivers, onCreate }: DriverProfileFormProps) {
  const [members, setMembers] = useState<TenantMember[]>([]);
  const [selectedUserId, setSelectedUserId] = useState('');
  const [fields, setFields] = useState(initialFields);
  const [isLoadingMembers, setIsLoadingMembers] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  useEffect(() => {
    memberRepository
      .getMembers()
      .then(setMembers)
      .catch((err) => setError(err instanceof Error ? err.message : 'Không thể tải thành viên'))
      .finally(() => setIsLoadingMembers(false));
  }, []);

  const eligibleMembers = useMemo(() => {
    const profiledUserIds = new Set(drivers.map((driver) => driver.user_id));
    return members.filter(
      (member) =>
        member.role === 'DRIVER' &&
        member.status === 'ACTIVE' &&
        !profiledUserIds.has(member.user_id)
    );
  }, [drivers, members]);

  const selectMember = (userId: string) => {
    setSelectedUserId(userId);
    const member = members.find((item) => item.user_id === userId);
    if (member) {
      setFields((current) => ({ ...current, fullName: member.full_name || '' }));
    }
    setError(null);
    setSuccess(null);
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const tenantId = getCurrentTenantId();
    if (!tenantId || !selectedUserId) {
      setError('Hãy chọn một thành viên DRIVER đã kích hoạt.');
      return;
    }
    if (!/^\+?[0-9]{9,15}$/.test(fields.phone.trim())) {
      setError('Số điện thoại cần có 9–15 chữ số.');
      return;
    }

    setIsSubmitting(true);
    setError(null);
    setSuccess(null);
    try {
      const created = await onCreate({
        userId: selectedUserId,
        tenantId,
        fullName: fields.fullName.trim(),
        phone: fields.phone.trim(),
        licensePlate: fields.licensePlate.trim().toUpperCase(),
        vehicleType: fields.vehicleType,
        capacity: Number(fields.capacity),
        driverType: fields.driverType,
      });
      setSuccess(`Đã tạo hồ sơ cho ${created.name}. Có thể điều phối đơn ngay.`);
      setSelectedUserId('');
      setFields(initialFields);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không thể tạo hồ sơ tài xế');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <section className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
      <div className="flex items-start gap-3">
        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-blue-100 text-blue-700">
          <UserRoundPlus size={20} />
        </div>
        <div>
          <h2 className="text-base font-semibold text-gray-900">Tạo hồ sơ vận hành cho tài xế</h2>
          <p className="mt-1 max-w-2xl text-sm text-gray-600">
            Chọn thành viên DRIVER đã kích hoạt bằng token, sau đó khai báo phương tiện để đưa vào danh sách điều phối.
          </p>
        </div>
      </div>

      <form onSubmit={submit} className="mt-5 grid gap-4 lg:grid-cols-4">
        <label className="flex flex-col gap-1.5 text-sm font-medium text-gray-700 lg:col-span-2">
          Tài khoản tài xế
          <select
            value={selectedUserId}
            onChange={(event) => selectMember(event.target.value)}
            disabled={isLoadingMembers || isSubmitting}
            className="h-[38px] rounded-lg border border-gray-300 bg-white px-3 text-sm text-gray-900 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 disabled:bg-gray-100"
          >
            <option value="">
              {isLoadingMembers ? 'Đang tải thành viên…' : 'Chọn thành viên DRIVER'}
            </option>
            {eligibleMembers.map((member) => (
              <option key={member.user_id} value={member.user_id}>
                {member.full_name || member.email} — {member.email}
              </option>
            ))}
          </select>
        </label>
        <Input
          id="driver-full-name"
          label="Họ tên hiển thị"
          value={fields.fullName}
          onChange={(event) => setFields({ ...fields, fullName: event.target.value })}
          required
          disabled={isSubmitting}
        />
        <Input
          id="driver-phone"
          label="Số điện thoại"
          value={fields.phone}
          onChange={(event) => setFields({ ...fields, phone: event.target.value })}
          placeholder="0901234567"
          required
          disabled={isSubmitting}
        />
        <Input
          id="driver-license-plate"
          label="Biển số xe"
          value={fields.licensePlate}
          onChange={(event) => setFields({ ...fields, licensePlate: event.target.value })}
          placeholder="65-B1 123.45"
          required
          disabled={isSubmitting}
        />
        <label className="flex flex-col gap-1.5 text-sm font-medium text-gray-700">
          Phương tiện
          <select
            value={fields.vehicleType}
            onChange={(event) => setFields({ ...fields, vehicleType: event.target.value as CreateDriverPayload['vehicleType'] })}
            className="h-[38px] rounded-lg border border-gray-300 bg-white px-3 text-sm text-gray-900 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
          >
            <option value="MOTORCYCLE">Xe máy</option>
            <option value="CAR">Ô tô</option>
            <option value="VAN">Xe tải van</option>
          </select>
        </label>
        <Input
          id="driver-capacity"
          label="Sức chứa (đơn)"
          type="number"
          min={1}
          value={fields.capacity}
          onChange={(event) => setFields({ ...fields, capacity: Number(event.target.value) })}
          required
          disabled={isSubmitting}
        />
        <label className="flex flex-col gap-1.5 text-sm font-medium text-gray-700">
          Hình thức làm việc
          <select
            value={fields.driverType}
            onChange={(event) => setFields({ ...fields, driverType: event.target.value as CreateDriverPayload['driverType'] })}
            className="h-[38px] rounded-lg border border-gray-300 bg-white px-3 text-sm text-gray-900 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
          >
            <option value="DEDICATED">Cố định</option>
            <option value="POOLED">Theo nhóm</option>
          </select>
        </label>

        <div className="flex flex-col justify-end lg:col-span-4 lg:flex-row lg:items-center lg:justify-between">
          <div aria-live="polite" className="min-h-6">
            {error && <p className="flex items-center gap-2 text-sm text-red-700"><AlertCircle size={16} />{error}</p>}
            {success && <p className="flex items-center gap-2 text-sm text-emerald-700"><CheckCircle2 size={16} />{success}</p>}
            {!isLoadingMembers && eligibleMembers.length === 0 && !success && (
              <p className="text-sm text-amber-700">Chưa có thành viên DRIVER đang hoạt động và chưa có hồ sơ.</p>
            )}
          </div>
          <Button type="submit" isLoading={isSubmitting} disabled={!selectedUserId || eligibleMembers.length === 0}>
            Tạo hồ sơ tài xế
          </Button>
        </div>
      </form>
    </section>
  );
}
