'use client';

import { useAuth } from '@/application/contexts/auth-context';
import { formatBankDisplay, stripVietnameseDiacritics, VIETQR_BANKS } from '@/application/vietqr';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Modal } from '@/components/ui/modal';
import { CustomSelect } from '@/components/ui/select';
import {
  CreateDriverPayload,
  Driver,
  DriverActivationKeyResult,
  UpdateDriverPayload,
} from '@/domain/entities/driver.entity';
import { useLocale } from '@/i18n/provider';
import { driverRepository } from '@/infrastructure/repositories/driver.repository';
import { getCurrentTenantId } from '@/infrastructure/repositories/member.repository';
import { KeyRound, MessageSquare, ShieldCheck } from 'lucide-react';
import Link from 'next/link';
import { FormEvent, useMemo, useState } from 'react';
import { toast } from 'sonner';
import { DriverActivationCodeModal } from './driver-activation-code-modal';
import { driverVehicleLabel, formatDriverShift } from './driver-formatters';
import { DriverProfileDialog } from './driver-profile-dialog';
import { DriverProfilesTable, DriverProfileStatus } from './driver-profiles-table';

interface DriverProfilesPanelProps {
  drivers: Driver[];
  isLoading: boolean;
  error: string | null;
  isCreateOpen: boolean;
  onCreateOpenChange: (open: boolean) => void;
  onCreateDriver: (payload: CreateDriverPayload) => Promise<Driver>;
  onUpdateDriver?: (driverId: string, payload: UpdateDriverPayload) => Promise<Driver>;
  onReviewDriver: (driverId: string, approved: boolean, note?: string) => Promise<unknown>;
  onRefresh: () => void;
  onBankAccountUpdated?: () => Promise<void>;
}

const fieldRow =
  'grid gap-1.5 text-sm font-medium text-slate-700 sm:grid-cols-[9rem_minmax(0,1fr)] sm:items-center sm:gap-3';

/** Driver profiles, manual creation, approval review, and VietQR payout configuration. */
export function DriverProfilesPanel({
  drivers,
  isLoading,
  error,
  isCreateOpen,
  onCreateOpenChange,
  onCreateDriver,
  onUpdateDriver,
  onReviewDriver,
  onRefresh,
  onBankAccountUpdated,
}: DriverProfilesPanelProps) {
  const { t } = useLocale();
  const { user, hasPermission } = useAuth();
  const isManager = hasPermission('driver:manage');
  const canReview =
    isManager && ['OWNER', 'TENANT_ADMIN', 'SYSTEM_ADMIN'].includes(user?.tenantRole ?? user?.role ?? '');
  const canChat = ['OWNER', 'TENANT_ADMIN', 'OPERATOR'].includes(user?.tenantRole ?? '');
  const canManageBank =
    ['OWNER', 'TENANT_ADMIN', 'OPERATOR', 'SYSTEM_ADMIN'].includes(user?.tenantRole ?? '') ||
    user?.role === 'SYSTEM_ADMIN';
  const canEditProfile = isManager || canManageBank;

  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [actionError, setActionError] = useState('');
  const [reviewNotes, setReviewNotes] = useState<Record<string, string>>({});
  const [selection, setSelection] = useState<{ id: string; review: boolean }>();
  const profileDriver = drivers.find((driver) => driver.id === selection?.id);
  const [issuedKeyResult, setIssuedKeyResult] = useState<DriverActivationKeyResult | null>(null);

  const [bankCode, setBankCode] = useState('VCB');
  const [bankAccountNumber, setBankAccountNumber] = useState('');
  const [bankAccountName, setBankAccountName] = useState('');
  const [bankError, setBankError] = useState('');
  const [savingBank, setSavingBank] = useState(false);

  const [editFullName, setEditFullName] = useState('');
  const [editPhone, setEditPhone] = useState('');
  const [editEmail, setEditEmail] = useState('');
  const [editVehicleType, setEditVehicleType] = useState<'MOTORCYCLE' | 'CAR' | 'VAN'>('MOTORCYCLE');
  const [editLicensePlate, setEditLicensePlate] = useState('');
  const [editCapacity, setEditCapacity] = useState('20');
  const [editShiftStart, setEditShiftStart] = useState('');
  const [editShiftEnd, setEditShiftEnd] = useState('');
  const [profileError, setProfileError] = useState('');
  const [savingProfile, setSavingProfile] = useState(false);

  const bankOptions = useMemo(
    () =>
      VIETQR_BANKS.map((bank) => ({
        value: bank.code,
        label: `${bank.shortName} (${bank.code})`,
      })),
    []
  );

  function openProfile(driver: Driver, review = false) {
    setActionError('');
    setMessage('');
    setSelection({ id: driver.id, review });
    setBankCode(driver.bank_code || 'VCB');
    setBankAccountNumber(driver.bank_account_number || '');
    setBankAccountName(driver.bank_account_name || stripVietnameseDiacritics(driver.name));
    setBankError('');
    setEditFullName(driver.name || '');
    setEditPhone(driver.phone || '');
    setEditEmail(driver.email || '');
    setEditVehicleType(
      driver.vehicle_type === 'CAR' || driver.vehicle_type === 'VAN' ? driver.vehicle_type : 'MOTORCYCLE'
    );
    setEditLicensePlate(driver.vehicle_plate || '');
    setEditCapacity(driver.capacity != null && driver.capacity > 0 ? String(driver.capacity) : '20');
    setEditShiftStart(driver.shift_start ? driver.shift_start.slice(0, 5) : '');
    setEditShiftEnd(driver.shift_end ? driver.shift_end.slice(0, 5) : '');
    setProfileError('');
  }

  async function saveDriverProfile() {
    if (!profileDriver) return;
    const trimmedName = editFullName.trim();
    const trimmedPhone = editPhone.trim().replace(/\s+/g, '');
    const trimmedEmail = editEmail.trim().toLowerCase();
    const parsedCapacity = Number(editCapacity);

    if (trimmedName.length < 2) {
      setProfileError(t('Họ và tên cần ít nhất 2 ký tự.'));
      return;
    }
    if (!/^\+?[0-9]{9,15}$/.test(trimmedPhone)) {
      setProfileError(t('Số điện thoại không hợp lệ (9–15 chữ số).'));
      return;
    }
    if (trimmedEmail && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmedEmail)) {
      setProfileError(t('Địa chỉ email liên hệ không hợp lệ.'));
      return;
    }
    if (!Number.isInteger(parsedCapacity) || parsedCapacity < 1) {
      setProfileError(t('Sức chứa phải là số nguyên lớn hơn hoặc bằng 1.'));
      return;
    }

    setSavingProfile(true);
    setProfileError('');
    try {
      const payload: UpdateDriverPayload = {
        full_name: trimmedName,
        phone: trimmedPhone,
        email: trimmedEmail,
        vehicle_type: editVehicleType,
        license_plate: editLicensePlate.trim().toUpperCase(),
        capacity: parsedCapacity,
        driver_type: 'DEDICATED',
        shift_start: editShiftStart ? editShiftStart : undefined,
        shift_end: editShiftEnd ? editShiftEnd : undefined,
      };
      if (onUpdateDriver) {
        await onUpdateDriver(profileDriver.id, payload);
      } else {
        await driverRepository.updateDriver(profileDriver.id, payload);
        onRefresh();
      }
      toast.success(t('Đã cập nhật hồ sơ tài xế'));
    } catch (err) {
      setProfileError(err instanceof Error ? err.message : t('Không thể cập nhật hồ sơ tài xế.'));
    } finally {
      setSavingProfile(false);
    }
  }

  async function saveDriverBankAccount(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!profileDriver) return;
    const cleanNumber = bankAccountNumber.trim().replace(/\s+/g, '');
    if (!/^[0-9A-Za-z]{4,40}$/.test(cleanNumber)) {
      setBankError(t('Số tài khoản cần từ 4 đến 40 ký tự chữ hoặc số.'));
      return;
    }
    setSavingBank(true);
    setBankError('');
    try {
      await driverRepository.updateBankAccount(profileDriver.id, {
        bank_code: bankCode,
        bank_account_number: cleanNumber,
        bank_account_name: bankAccountName.trim() || profileDriver.name,
      });
      toast.success(t('Đã lưu tài khoản ngân hàng tài xế'));
      setSelection(undefined);
      if (onBankAccountUpdated) {
        await onBankAccountUpdated();
      }
    } catch (err) {
      setBankError(err instanceof Error ? err.message : t('Không thể lưu tài khoản ngân hàng'));
    } finally {
      setSavingBank(false);
    }
  }

  const resetComposer = () => {
    onCreateOpenChange(false);
    setActionError('');
  };

  async function createManual(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setActionError('');
    setMessage('');
    const data = new FormData(event.currentTarget);
    try {
      const emailRaw = String(data.get('email') || '').trim().toLowerCase();
      const payload: CreateDriverPayload = {
        tenantId: getCurrentTenantId(),
        fullName: String(data.get('fullName')).trim(),
        phone: String(data.get('phone')).trim(),
        email: emailRaw || undefined,
        vehicleType: data.get('vehicleType') as CreateDriverPayload['vehicleType'],
        licensePlate: String(data.get('licensePlate') || '').trim().toUpperCase() || undefined,
        capacity: Number(data.get('capacity')),
        driverType: 'DEDICATED',
      };
      const created = await onCreateDriver(payload);
      onCreateOpenChange(false);
      toast.success(`${t('Đã tạo hồ sơ cho')} ${created.name}`);
      setMessage(
        `${t('Đã tạo hồ sơ cho')} ${created.name}. ${t(
          'Bạn có thể cấp mã kích hoạt ứng dụng 12 chữ số ngay ở tab "Kích hoạt ứng dụng".'
        )}`
      );
    } catch (err) {
      setActionError(err instanceof Error ? err.message : t('Không thể tạo hồ sơ tài xế.'));
    } finally {
      setBusy(false);
    }
  }

  async function issueActivationFromProfile(driver: Driver) {
    setBusy(true);
    setProfileError('');
    try {
      const issued = await driverRepository.generateActivationCode(driver.id);
      setSelection(undefined);
      setIssuedKeyResult(issued);
      onRefresh();
    } catch (err) {
      setProfileError(err instanceof Error ? err.message : t('Không thể tạo mã kích hoạt.'));
    } finally {
      setBusy(false);
    }
  }

  async function review(driver: Driver, approved: boolean) {
    const note = reviewNotes[driver.id]?.trim();
    if (!approved && !note) {
      setActionError(t('Hãy ghi rõ nội dung tài xế cần bổ sung.'));
      return;
    }
    setBusy(true);
    setActionError('');
    try {
      await onReviewDriver(driver.id, approved, note);
      setReviewNotes((current) => ({ ...current, [driver.id]: '' }));
      setMessage(
        approved ? `${t('Đã duyệt hồ sơ')} ${driver.name}.` : `${t('Đã gửi yêu cầu bổ sung cho')} ${driver.name}.`
      );
    } catch (err) {
      setActionError(err instanceof Error ? err.message : t('Không thể xử lý hồ sơ.'));
    } finally {
      setBusy(false);
    }
  }

  const actionFeedback = (actionError || message) && (
    <div
      role={actionError ? 'alert' : 'status'}
      className={`rounded-sm px-3 py-2 text-sm ${actionError ? 'bg-red-50 text-red-800' : 'bg-emerald-50 text-emerald-800'
        }`}
    >
      {actionError || message}
    </div>
  );

  return (
    <div className="space-y-4">
      {!isCreateOpen && !profileDriver && actionFeedback}
      <DriverProfilesTable
        drivers={drivers}
        isLoading={isLoading}
        error={error}
        canReview={canReview}
        onRefresh={onRefresh}
        onOpen={openProfile}
        onAdd={
          isManager
            ? () => {
              setActionError('');
              onCreateOpenChange(true);
            }
            : undefined
        }
      />

      {isCreateOpen && (
        <Modal
          open
          onOpenChange={(open) => {
            if (!open && !busy) resetComposer();
          }}
          title={t('Thêm tài xế')}
          description={t('Tạo hồ sơ tài xế nội bộ. Sau khi lưu, cấp mã kích hoạt 12 chữ số để tài xế đăng nhập ứng dụng.')}
          className="max-h-[calc(100dvh-1.5rem)] w-[calc(100%-1.5rem)] max-w-xl overflow-y-auto"
        >
          {actionError && (
            <div role="alert" className="mb-3 rounded-sm bg-red-50 px-3 py-2 text-sm text-red-800">
              {actionError}
            </div>
          )}

          <form onSubmit={(event) => void createManual(event)} className="space-y-4">
            <div className="space-y-2.5">
              <label className={fieldRow}>
                <span>{t('Họ và tên')}</span>
                <Input name="fullName" required minLength={2} placeholder={t('VD: Nguyễn Văn Tài')} />
              </label>
              <label className={fieldRow}>
                <span>{t('Số điện thoại')}</span>
                <Input name="phone" type="tel" required pattern="\+?[0-9]{9,15}" placeholder={t('VD: 0901234567')} />
              </label>
              <label className={fieldRow}>
                <span>{t('Email liên hệ')}</span>
                <Input name="email" type="email" placeholder={t('Tùy chọn — dùng gửi mã kích hoạt')} />
              </label>
            </div>
            <div className="space-y-2.5 border-t border-slate-200 pt-4">
              <label className={fieldRow}>
                <span>{t('Loại phương tiện')}</span>
                <CustomSelect
                  name="vehicleType"
                  aria-label={t('Loại phương tiện')}
                  defaultValue="MOTORCYCLE"
                  options={[
                    { value: 'MOTORCYCLE', label: t('Xe máy') },
                    { value: 'CAR', label: t('Ô tô') },
                    { value: 'VAN', label: t('Xe van') },
                  ]}
                />
              </label>
              <label className={fieldRow}>
                <span>{t('Biển số xe')}</span>
                <Input name="licensePlate" required placeholder={t('VD: 65-B1 123.45')} />
              </label>
              <label className={fieldRow}>
                <span>{t('Sức chứa (đơn)')}</span>
                <Input name="capacity" type="number" min={1} required defaultValue={20} />
              </label>
            </div>
            <div className="flex justify-end gap-2 border-t border-slate-200 pt-3">
              <Button type="button" variant="outline" className="cursor-pointer" disabled={busy} onClick={resetComposer}>
                {t('Đóng')}
              </Button>
              <Button type="submit" className="cursor-pointer" isLoading={busy}>
                {t('Tạo hồ sơ')}
              </Button>
            </div>
          </form>
        </Modal>
      )}

      {profileDriver && (
        <DriverProfileDialog
          key={profileDriver.id}
          name={profileDriver.name}
          initialSection={selection?.review ? 'review' : 'profile'}
          busy={savingBank || savingProfile || busy}
          onClose={() => setSelection(undefined)}
          profileAction={
            canEditProfile && (
              <Button
                type="button"
                size="sm"
                className="cursor-pointer"
                onClick={() => void saveDriverProfile()}
                isLoading={savingProfile}
              >
                {t('Lưu hồ sơ')}
              </Button>
            )
          }
          profile={
            <>
              {canEditProfile ? (
                <div className="space-y-3">
                  <label className={fieldRow}>
                    <span>{t('Họ và tên')}</span>
                    <input
                      type="text"
                      required
                      minLength={2}
                      maxLength={255}
                      disabled={savingProfile}
                      value={editFullName}
                      onChange={(event) => {
                        setEditFullName(event.target.value);
                        if (profileError) setProfileError('');
                      }}
                      className="h-10 rounded-sm border border-slate-300 bg-white px-3 text-sm text-slate-950 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500/20"
                    />
                  </label>
                  <label className={fieldRow}>
                    <span>{t('Số điện thoại')}</span>
                    <input
                      type="tel"
                      required
                      pattern="^\+?[0-9]{9,15}$"
                      maxLength={15}
                      disabled={savingProfile}
                      value={editPhone}
                      onChange={(event) => {
                        setEditPhone(event.target.value);
                        if (profileError) setProfileError('');
                      }}
                      className="h-10 rounded-sm border border-slate-300 bg-white px-3 text-sm tabular-nums text-slate-950 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500/20"
                    />
                  </label>
                  <label className={fieldRow}>
                    <span>{t('Email liên hệ')}</span>
                    <input
                      type="email"
                      maxLength={255}
                      disabled={savingProfile}
                      value={editEmail}
                      onChange={(event) => {
                        setEditEmail(event.target.value);
                        if (profileError) setProfileError('');
                      }}
                      placeholder={t('Dùng để nhận mã kích hoạt ứng dụng')}
                      className="h-10 rounded-sm border border-slate-300 bg-white px-3 text-sm text-slate-950 placeholder:text-slate-400 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500/20"
                    />
                  </label>
                  <DriverDetailRows
                    rows={[
                      [t('Trạng thái'), <DriverProfileStatus key="status" driver={profileDriver} />],
                      [
                        t('Tài khoản ứng dụng'),
                        profileDriver.user_id ? t('Đã liên kết tài khoản') : t('Chưa kích hoạt ứng dụng'),
                      ],
                    ]}
                  />
                  {profileError && (
                    <p role="alert" className="text-sm text-red-700">
                      {profileError}
                    </p>
                  )}
                </div>
              ) : (
                <DriverDetailRows
                  rows={[
                    [t('Họ và tên'), profileDriver.name || '—'],
                    [t('Số điện thoại'), profileDriver.phone || '—'],
                    [t('Email liên hệ'), profileDriver.email || '—'],
                    [t('Trạng thái'), <DriverProfileStatus key="status" driver={profileDriver} />],
                    [
                      t('Tài khoản ứng dụng'),
                      profileDriver.user_id ? t('Đã liên kết tài khoản') : t('Chưa kích hoạt ứng dụng'),
                    ],
                  ]}
                />
              )}
              <div className="mt-4 flex flex-wrap gap-2">
                {profileDriver.user_id && canChat && (
                  <Link
                    href={`/messages?peer=${encodeURIComponent(profileDriver.user_id)}`}
                    className="inline-flex min-h-9 items-center gap-2 rounded-md border border-slate-300 px-3 text-sm font-medium text-slate-700 hover:bg-slate-50 focus-visible:outline-2 focus-visible:outline-blue-600"
                  >
                    <MessageSquare size={14} aria-hidden="true" />
                    {t('Nhắn tin')}
                  </Link>
                )}
                {isManager && profileDriver.onboarding_status !== 'SUSPENDED' && (
                  <Button
                    type="button"
                    size="sm"
                    variant="outline"
                    className="cursor-pointer"
                    disabled={busy || savingProfile}
                    onClick={() => void issueActivationFromProfile(profileDriver)}
                  >
                    <KeyRound size={14} aria-hidden="true" />
                    {t('Cấp mã kích hoạt ứng dụng')}
                  </Button>
                )}
              </div>
            </>
          }
          vehicle={
            canEditProfile ? (
              <div className="space-y-3">
                <label className={fieldRow}>
                  <span>{t('Loại phương tiện')}</span>
                  <CustomSelect
                    aria-label={t('Loại phương tiện')}
                    value={editVehicleType}
                    onValueChange={(val) => setEditVehicleType(val as 'MOTORCYCLE' | 'CAR' | 'VAN')}
                    disabled={savingProfile}
                    options={[
                      { value: 'MOTORCYCLE', label: t('Xe máy') },
                      { value: 'CAR', label: t('Ô tô') },
                      { value: 'VAN', label: t('Xe van') },
                    ]}
                  />
                </label>
                <label className={fieldRow}>
                  <span>{t('Biển số')}</span>
                  <input
                    type="text"
                    maxLength={20}
                    disabled={savingProfile}
                    value={editLicensePlate}
                    onChange={(event) => {
                      setEditLicensePlate(event.target.value.toUpperCase());
                      if (profileError) setProfileError('');
                    }}
                    placeholder={t('VD: 65-B1 123.45')}
                    className="h-10 rounded-sm border border-slate-300 bg-white px-3 text-sm uppercase tabular-nums text-slate-950 placeholder:normal-case placeholder:text-slate-400 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500/20"
                  />
                </label>
                <label className={fieldRow}>
                  <span>{t('Sức chứa (đơn)')}</span>
                  <input
                    type="number"
                    min={1}
                    step={1}
                    required
                    disabled={savingProfile}
                    value={editCapacity}
                    onChange={(event) => {
                      setEditCapacity(event.target.value);
                      if (profileError) setProfileError('');
                    }}
                    className="h-10 rounded-sm border border-slate-300 bg-white px-3 text-sm tabular-nums text-slate-950 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500/20"
                  />
                </label>
                <div className={fieldRow}>
                  <span>{t('Ca làm')}</span>
                  <div className="grid grid-cols-2 gap-2">
                    <input
                      type="time"
                      aria-label={t('Giờ bắt đầu ca')}
                      disabled={savingProfile}
                      value={editShiftStart}
                      onChange={(event) => setEditShiftStart(event.target.value)}
                      className="h-10 rounded-sm border border-slate-300 bg-white px-3 text-sm tabular-nums text-slate-950 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500/20"
                    />
                    <input
                      type="time"
                      aria-label={t('Giờ kết thúc ca')}
                      disabled={savingProfile}
                      value={editShiftEnd}
                      onChange={(event) => setEditShiftEnd(event.target.value)}
                      className="h-10 rounded-sm border border-slate-300 bg-white px-3 text-sm tabular-nums text-slate-950 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500/20"
                    />
                  </div>
                </div>
                {profileError && (
                  <p role="alert" className="text-sm text-red-700">
                    {profileError}
                  </p>
                )}
              </div>
            ) : (
              <DriverDetailRows
                rows={[
                  [t('Loại phương tiện'), profileDriver.vehicle_type ? t(driverVehicleLabel(profileDriver.vehicle_type)) : '—'],
                  [t('Biển số'), profileDriver.vehicle_plate || '—'],
                  [t('Sức chứa (đơn)'), profileDriver.capacity ?? '—'],
                  [t('Ca làm'), formatDriverShift(profileDriver.shift_start, profileDriver.shift_end)],
                ]}
              />
            )
          }
          review={
            profileDriver.onboarding_status === 'PENDING_APPROVAL' ||
              profileDriver.review_note ||
              selection?.review ||
              message ? (
              <div className="space-y-3">
                <DriverProfileStatus driver={profileDriver} />
                {profileDriver.review_note && (
                  <p className="break-words text-sm text-slate-700">
                    {t('Yêu cầu bổ sung:')} {profileDriver.review_note}
                  </p>
                )}
                {canReview && profileDriver.onboarding_status === 'PENDING_APPROVAL' && (
                  <>
                    <label className="block text-sm font-medium text-slate-700" htmlFor="driver-review-note">
                      {t('Ghi chú xét duyệt')}
                    </label>
                    <textarea
                      id="driver-review-note"
                      value={reviewNotes[profileDriver.id] || ''}
                      onChange={(event) =>
                        setReviewNotes((current) => ({ ...current, [profileDriver.id]: event.target.value }))
                      }
                      maxLength={1000}
                      disabled={busy}
                      className="min-h-20 w-full rounded-sm border border-slate-300 px-3 py-2 text-sm focus-visible:outline-2 focus-visible:outline-blue-600"
                      placeholder={t('Bắt buộc khi yêu cầu bổ sung')}
                    />
                    <div className="flex flex-wrap justify-end gap-2">
                      <Button
                        type="button"
                        size="sm"
                        variant="outline"
                        disabled={busy}
                        onClick={() => void review(profileDriver, false)}
                      >
                        {t('Yêu cầu bổ sung')}
                      </Button>
                      <Button type="button" size="sm" disabled={busy} onClick={() => void review(profileDriver, true)}>
                        <ShieldCheck size={15} aria-hidden="true" />
                        {t('Duyệt hồ sơ')}
                      </Button>
                    </div>
                  </>
                )}
                {actionFeedback}
              </div>
            ) : undefined
          }
          bankAction={
            canManageBank && (
              <Button type="submit" size="sm" form="driver-bank-account" isLoading={savingBank}>
                {t('Lưu tài khoản')}
              </Button>
            )
          }
          bank={
            canManageBank ? (
              <form
                id="driver-bank-account"
                onSubmit={(event) => void saveDriverBankAccount(event)}
                className="space-y-3"
              >
                <label className={fieldRow}>
                  {t('Ngân hàng')}
                  <CustomSelect
                    aria-label={t('Ngân hàng')}
                    value={bankCode}
                    onValueChange={setBankCode}
                    options={bankOptions}
                    disabled={savingBank}
                  />
                </label>
                <label className={fieldRow}>
                  {t('Số tài khoản')}
                  <input
                    type="text"
                    required
                    pattern="^[0-9A-Za-z]{4,40}$"
                    maxLength={40}
                    disabled={savingBank}
                    value={bankAccountNumber}
                    onChange={(event) => {
                      setBankAccountNumber(event.target.value);
                      if (bankError) setBankError('');
                    }}
                    placeholder={t('VD: 0123456789')}
                    className={`h-10 rounded-sm border bg-white px-3 text-sm tabular-nums text-slate-950 placeholder:text-slate-400 focus:outline-none focus:ring-2 ${bankError
                        ? 'border-red-500 focus:border-red-500 focus:ring-red-500/20'
                        : 'border-slate-300 focus:border-blue-500 focus:ring-blue-500/20'
                      }`}
                  />
                </label>
                <label className={fieldRow}>
                  {t('Tên chủ tài khoản (không dấu)')}
                  <input
                    type="text"
                    maxLength={120}
                    disabled={savingBank}
                    value={bankAccountName}
                    onChange={(event) => setBankAccountName(event.target.value)}
                    placeholder={profileDriver ? stripVietnameseDiacritics(profileDriver.name) : ''}
                    className="h-10 rounded-sm border border-slate-300 bg-white px-3 text-sm uppercase text-slate-950 placeholder:text-slate-400 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500/20"
                  />
                </label>
                {bankError && (
                  <p role="alert" className="text-sm text-red-700">
                    {bankError}
                  </p>
                )}
              </form>
            ) : (
              <DriverDetailRows
                rows={[
                  [t('Ngân hàng'), profileDriver.bank_code ? formatBankDisplay(profileDriver.bank_code) : '—'],
                  [t('Số tài khoản'), profileDriver.bank_account_number || '—'],
                  [t('Tên chủ tài khoản'), profileDriver.bank_account_name || '—'],
                ]}
              />
            )
          }
        />
      )}

      {issuedKeyResult && (
        <DriverActivationCodeModal
          items={[issuedKeyResult]}
          onClose={() => setIssuedKeyResult(null)}
          onEmailDelivered={onRefresh}
        />
      )}
    </div>
  );
}

function DriverDetailRows({ rows }: { rows: [string, React.ReactNode][] }) {
  return (
    <dl className="divide-y divide-slate-100">
      {rows.map(([label, value]) => (
        <div key={label} className={`${fieldRow} py-2.5`}>
          <dt className="text-slate-600">{label}</dt>
          <dd className="min-w-0 break-words text-slate-950">{value}</dd>
        </div>
      ))}
    </dl>
  );
}
