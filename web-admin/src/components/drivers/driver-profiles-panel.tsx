'use client';

import { FormEvent, useMemo, useState } from 'react';
import { UserPlus, Mail, Phone, Truck, ShieldCheck, X, RefreshCw, Link2, Clock3 } from 'lucide-react';
import { useLocale } from '@/i18n/provider';
import { CreateDriverPayload, Driver } from '@/domain/entities/driver.entity';
import { TenantInvitation } from '@/domain/entities/member.entity';
import { getCurrentTenantId, memberRepository } from '@/infrastructure/repositories/member.repository';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';

export type AddDriverMode = 'choose' | 'manual' | 'invite';

interface DriverProfilesPanelProps {
  drivers: Driver[];
  isLoading: boolean;
  error: string | null;
  invitations: TenantInvitation[];
  mode: AddDriverMode | null;
  onModeChange: (mode: AddDriverMode | null) => void;
  onCreateDriver: (payload: CreateDriverPayload) => Promise<Driver>;
  onReviewDriver: (driverId: string, approved: boolean, note?: string) => Promise<unknown>;
  onReloadInvitations: () => Promise<void>;
}

const onboardingCopy: Record<Driver['onboarding_status'], { label: string; className: string }> = {
  INACTIVE: { label: 'Cần hoàn thiện', className: 'bg-amber-50 text-amber-800' },
  PENDING_APPROVAL: { label: 'Chờ duyệt', className: 'bg-blue-50 text-blue-800' },
  ACTIVE: { label: 'Đã duyệt', className: 'bg-emerald-50 text-emerald-800' },
  SUSPENDED: { label: 'Tạm ngưng', className: 'bg-red-50 text-red-800' },
};

function invitationState(invitation: TenantInvitation) {
  if (invitation.status === 'PENDING' && new Date(invitation.expires_at) <= new Date()) {
    return { label: 'Đã hết hạn', className: 'bg-amber-50 text-amber-800', canResend: true, canRevoke: false };
  }
  const states = {
    PENDING: { label: 'Đang chờ', className: 'bg-blue-50 text-blue-800', canResend: true, canRevoke: true },
    ACCEPTED: { label: 'Đã chấp nhận', className: 'bg-emerald-50 text-emerald-800', canResend: false, canRevoke: false },
    EXPIRED: { label: 'Đã hết hạn', className: 'bg-amber-50 text-amber-800', canResend: true, canRevoke: false },
    REVOKED: { label: 'Đã thu hồi', className: 'bg-slate-100 text-slate-700', canResend: true, canRevoke: false },
  };
  return states[invitation.status];
}

/** Profiles, invitations, onboarding composer, and approval workflows for tenant drivers. */
export function DriverProfilesPanel({
  drivers,
  isLoading,
  error,
  invitations,
  mode,
  onModeChange,
  onCreateDriver,
  onReviewDriver,
  onReloadInvitations,
}: DriverProfilesPanelProps) {
  const { t, intlLocale } = useLocale();
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [actionError, setActionError] = useState('');
  const [createdDriver, setCreatedDriver] = useState<Driver | null>(null);
  const [reviewNotes, setReviewNotes] = useState<Record<string, string>>({});

  const pendingInvitations = useMemo(
    () => invitations.filter((item) => item.status === 'PENDING' && new Date(item.expires_at) > new Date()),
    [invitations]
  );
  const readyCount = drivers.filter((driver) => driver.onboarding_status === 'ACTIVE' && driver.is_available).length;
  const reviewCount = drivers.filter((driver) => driver.onboarding_status === 'PENDING_APPROVAL').length;

  const resetComposer = () => {
    onModeChange(null);
    setCreatedDriver(null);
    setActionError('');
    setMessage('');
  };

  async function createManual(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setActionError('');
    setMessage('');
    const data = new FormData(event.currentTarget);
    try {
      const payload: CreateDriverPayload = {
        tenantId: getCurrentTenantId(),
        fullName: String(data.get('fullName')).trim(),
        phone: String(data.get('phone')).trim(),
        vehicleType: data.get('vehicleType') as CreateDriverPayload['vehicleType'],
        licensePlate: String(data.get('licensePlate')).trim().toUpperCase() || undefined,
        capacity: Number(data.get('capacity')),
        driverType: 'DEDICATED',
      };
      const created = await onCreateDriver(payload);
      setCreatedDriver(created);
      setMessage(`Đã tạo hồ sơ cho ${created.name}. Hồ sơ chưa thể nhận đơn cho đến khi tài khoản được liên kết và duyệt.`);
    } catch (err) {
      setActionError(err instanceof Error ? err.message : t('Không thể tạo hồ sơ tài xế.'));
    } finally {
      setBusy(false);
    }
  }

  async function invite(event: FormEvent<HTMLFormElement>, driverId?: string) {
    event.preventDefault();
    setBusy(true);
    setActionError('');
    setMessage('');
    const data = new FormData(event.currentTarget);
    try {
      const email = String(data.get('email')).trim().toLowerCase();
      await memberRepository.inviteMember({ email, role: 'DRIVER', driverId });
      setMessage(`Đã gửi lời mời đến ${email}. Lời mời có hiệu lực 7 ngày.`);
      await onReloadInvitations();
      if (!driverId) onModeChange(null);
    } catch (err) {
      setActionError(err instanceof Error ? err.message : t('Không thể gửi lời mời.'));
    } finally {
      setBusy(false);
    }
  }

  async function revoke(id: string) {
    setBusy(true);
    setActionError('');
    try {
      await memberRepository.revokeInvitation(id);
      await onReloadInvitations();
      setMessage(t('Đã thu hồi lời mời.'));
    } catch (err) {
      setActionError(err instanceof Error ? err.message : t('Không thể thu hồi lời mời.'));
    } finally {
      setBusy(false);
    }
  }

  async function resend(invitation: TenantInvitation) {
    setBusy(true);
    setActionError('');
    setMessage('');
    try {
      await memberRepository.inviteMember({
        email: invitation.email,
        role: 'DRIVER',
        driverId: invitation.driver_id,
      });
      await onReloadInvitations();
      setMessage(`Đã gửi lời mời mới đến ${invitation.email}. Liên kết cũ không còn hiệu lực.`);
    } catch (err) {
      setActionError(err instanceof Error ? err.message : t('Không thể gửi lại lời mời.'));
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
      setMessage(approved ? `Đã duyệt hồ sơ ${driver.name}.` : `Đã gửi yêu cầu bổ sung cho ${driver.name}.`);
    } catch (err) {
      setActionError(err instanceof Error ? err.message : t('Không thể xử lý hồ sơ.'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="space-y-6">
      <section className="grid divide-y divide-slate-200 rounded-xl border border-slate-200 bg-white sm:grid-cols-3 sm:divide-x sm:divide-y-0">
        {[
          [t('Tổng hồ sơ'), drivers.length],
          [t('Sẵn sàng nhận đơn'), readyCount],
          [t('Đang chờ duyệt'), reviewCount],
        ].map(([label, value]) => (
          <div key={String(label)} className="px-5 py-4">
            <p className="text-xs text-slate-500">{label}</p>
            <p className="mt-1 text-2xl font-semibold tabular-nums text-slate-950">{isLoading ? '—' : value}</p>
          </div>
        ))}
      </section>

      {(actionError || message) && (
        <div
          role={actionError ? 'alert' : 'status'}
          className={`rounded-lg px-4 py-3 text-sm ${actionError ? 'bg-red-50 text-red-800' : 'bg-emerald-50 text-emerald-800'}`}
        >
          {actionError || message}
        </div>
      )}

      {mode && (
        <section className="rounded-xl border border-slate-300 bg-white p-5 sm:p-6">
          <div className="flex items-start justify-between gap-4">
            <div>
              <h2 className="text-lg font-semibold text-slate-950">{t('Thêm tài xế')}</h2>
              <p className="mt-1 text-sm text-slate-600">{t('Chọn cách phù hợp với tình trạng nhân sự hiện tại.')}</p>
            </div>
            <button
              type="button"
              onClick={resetComposer}
              aria-label={t('Đóng')}
              className="rounded-md p-2 text-slate-500 hover:bg-slate-100"
            >
              <X size={18} />
            </button>
          </div>

          {mode === 'choose' && (
            <div className="mt-5 grid gap-3 md:grid-cols-2">
              <button
                type="button"
                onClick={() => onModeChange('manual')}
                className="rounded-xl border border-slate-200 p-5 text-left transition-colors hover:border-slate-400 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
              >
                <UserPlus size={20} />
                <h3 className="mt-4 font-semibold text-slate-950">{t('Tạo hồ sơ thủ công')}</h3>
                <p className="mt-1 text-sm leading-6 text-slate-600">
                  {t('Dành cho tài xế đang làm việc với doanh nghiệp. Có thể gửi lời mời kích hoạt sau.')}
                </p>
              </button>
              <button
                type="button"
                onClick={() => onModeChange('invite')}
                className="rounded-xl border border-slate-200 p-5 text-left transition-colors hover:border-slate-400 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
              >
                <Mail size={20} />
                <h3 className="mt-4 font-semibold text-slate-950">{t('Mời bằng email')}</h3>
                <p className="mt-1 text-sm leading-6 text-slate-600">
                  {t('Tài xế tạo tài khoản và tự hoàn thiện hồ sơ từ lời mời.')}
                </p>
              </button>
            </div>
          )}

          {mode === 'manual' && !createdDriver && (
            <form onSubmit={createManual} className="mt-5 grid gap-4 sm:grid-cols-2">
              <Input name="fullName" label={t('Họ và tên')} required minLength={2} />
              <Input name="phone" label={t('Số điện thoại')} required pattern="\+?[0-9]{9,15}" />
              <label className="flex flex-col gap-1.5 text-sm font-medium text-gray-700">
                {t('Loại phương tiện')}
                <select name="vehicleType" className="rounded-lg border border-gray-300 bg-white px-3 py-2">
                  <option value="MOTORCYCLE">{t('Xe máy')}</option>
                  <option value="CAR">{t('Ô tô')}</option>
                  <option value="VAN">Xe van</option>
                </select>
              </label>
              <Input name="licensePlate" label={t('Biển số (có thể bổ sung sau)')} />
              <Input name="capacity" label={t('Sức chứa (đơn)')} type="number" min={1} required defaultValue={20} />
              <div className="flex items-end gap-2">
                <Button type="button" variant="outline" onClick={() => onModeChange('choose')}>
                  {t('Quay lại')}
                </Button>
                <Button type="submit" isLoading={busy}>
                  {t('Tạo hồ sơ')}
                </Button>
              </div>
            </form>
          )}

          {mode === 'manual' && createdDriver && (
            <div className="mt-5">
              <p className="text-sm leading-6 text-slate-700">
                {t('Hồ sơ đã được lưu. Gửi lời mời để tài xế liên kết tài khoản, hoặc đóng và thực hiện sau từ danh sách.')}
              </p>
              <form onSubmit={(event) => invite(event, createdDriver.id)} className="mt-4 flex flex-col gap-3 sm:flex-row">
                <Input name="email" label={t('Email tài xế')} type="email" required className="min-w-0 sm:w-72" />
                <div className="flex items-end gap-2">
                  <Button type="submit" isLoading={busy}>
                    {t('Gửi lời mời')}
                  </Button>
                  <Button type="button" variant="outline" onClick={resetComposer}>
                    {t('Để sau')}
                  </Button>
                </div>
              </form>
            </div>
          )}

          {mode === 'invite' && (
            <form onSubmit={(event) => invite(event)} className="mt-5 flex flex-col gap-3 sm:flex-row">
              <Input name="email" label={t('Email tài xế')} type="email" required className="min-w-0 sm:w-80" />
              <div className="flex items-end gap-2">
                <Button type="button" variant="outline" onClick={() => onModeChange('choose')}>
                  {t('Quay lại')}
                </Button>
                <Button type="submit" isLoading={busy}>
                  {t('Gửi lời mời')}
                </Button>
              </div>
            </form>
          )}
        </section>
      )}

      {invitations.length > 0 && (
        <section>
          <div className="mb-3 flex items-center gap-2">
            <Clock3 size={17} />
            <h2 className="font-semibold text-slate-950">{t('Lời mời tài xế')}</h2>
            {pendingInvitations.length > 0 && (
              <span className="text-sm text-slate-500">
                {pendingInvitations.length} {t('đang chờ')}
              </span>
            )}
          </div>
          <div className="divide-y rounded-xl border border-slate-200 bg-white">
            {invitations.map((invitation) => {
              const state = invitationState(invitation);
              return (
                <div key={invitation.id} className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center sm:justify-between">
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="truncate font-medium text-slate-900">{invitation.email}</p>
                      <span className={`rounded-md px-2 py-1 text-xs font-medium ${state.className}`}>{t(state.label)}</span>
                    </div>
                    <p className="mt-1 text-xs text-slate-500">
                      {t('Hết hạn')} {new Date(invitation.expires_at).toLocaleString(intlLocale)}
                      {invitation.driver_id ? ` ${t('· Đã gắn hồ sơ')}` : ` ${t('· Chưa tạo hồ sơ')}`}
                    </p>
                  </div>
                  <div className="flex gap-2">
                    {state.canResend && (
                      <Button size="sm" variant="outline" disabled={busy} onClick={() => resend(invitation)}>
                        <RefreshCw size={14} />
                        {t('Gửi lại')}
                      </Button>
                    )}
                    {state.canRevoke && (
                      <Button size="sm" variant="outline" disabled={busy} onClick={() => revoke(invitation.id)}>
                        {t('Thu hồi')}
                      </Button>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        </section>
      )}

      <section>
        <h2 className="mb-3 font-semibold text-slate-950">{t('Danh sách tài xế')}</h2>
        {isLoading ? (
          <div className="py-16 text-center text-sm text-slate-500">{t('Đang tải hồ sơ…')}</div>
        ) : error ? (
          <div className="rounded-lg bg-red-50 p-4 text-sm text-red-800">{error}</div>
        ) : drivers.length === 0 ? (
          <div className="rounded-xl border border-dashed border-slate-300 py-16 text-center">
            <p className="font-medium text-slate-900">{t('Chưa có tài xế')}</p>
            <p className="mt-1 text-sm text-slate-500">{t('Dùng “Thêm tài xế” để bắt đầu.')}</p>
          </div>
        ) : (
          <div className="divide-y rounded-xl border border-slate-200 bg-white">
            {drivers.map((driver) => {
              const state = onboardingCopy[driver.onboarding_status] || onboardingCopy.INACTIVE;
              const linked = Boolean(driver.user_id);
              return (
                <article key={driver.id} className="p-4 sm:p-5">
                  <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
                    <div className="min-w-0">
                      <div className="flex flex-wrap items-center gap-2">
                        <h3 className="font-semibold text-slate-950">{driver.name}</h3>
                        <span className={`rounded-md px-2 py-1 text-xs font-medium ${state.className}`}>{t(state.label)}</span>
                        {!linked && (
                          <span className="rounded-md bg-slate-100 px-2 py-1 text-xs text-slate-700">
                            {t('Chưa liên kết tài khoản')}
                          </span>
                        )}
                      </div>
                      <div className="mt-2 flex flex-wrap gap-x-5 gap-y-1 text-sm text-slate-600">
                        <span className="flex items-center gap-1.5">
                          <Phone size={14} />
                          {driver.phone}
                        </span>
                        <span className="flex items-center gap-1.5">
                          <Truck size={14} />
                          {driver.vehicle_plate
                            ? `${driver.vehicle_type} · ${driver.vehicle_plate}`
                            : `${driver.vehicle_type} · ${t('Chưa có biển số')}`}
                        </span>
                      </div>
                      {driver.review_note && (
                        <p className="mt-2 text-sm text-amber-800">
                          {t('Yêu cầu bổ sung:')} {driver.review_note}
                        </p>
                      )}
                    </div>
                    {!linked && (
                      <Button
                        size="sm"
                        variant="outline"
                        onClick={() => {
                          setCreatedDriver(driver);
                          onModeChange('manual');
                        }}
                      >
                        <Link2 size={14} />
                        {t('Mời kích hoạt')}
                      </Button>
                    )}
                  </div>

                  {driver.onboarding_status === 'PENDING_APPROVAL' && (
                    <div className="mt-4 border-t border-slate-100 pt-4">
                      <label className="text-sm font-medium text-slate-700" htmlFor={`note-${driver.id}`}>
                        {t('Ghi chú xét duyệt')}
                      </label>
                      <textarea
                        id={`note-${driver.id}`}
                        value={reviewNotes[driver.id] || ''}
                        onChange={(event) =>
                          setReviewNotes((current) => ({ ...current, [driver.id]: event.target.value }))
                        }
                        maxLength={1000}
                        className="mt-1.5 min-h-20 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500/20"
                        placeholder={t('Bắt buộc khi yêu cầu bổ sung')}
                      />
                      <div className="mt-3 flex justify-end gap-2">
                        <Button variant="outline" disabled={busy} onClick={() => review(driver, false)}>
                          {t('Yêu cầu bổ sung')}
                        </Button>
                        <Button disabled={busy} onClick={() => review(driver, true)}>
                          <ShieldCheck size={15} />
                          {t('Duyệt hồ sơ')}
                        </Button>
                      </div>
                    </div>
                  )}
                </article>
              );
            })}
          </div>
        )}
      </section>
    </div>
  );
}
