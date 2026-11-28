'use client';

import { stripVietnameseDiacritics } from '@/application/vietqr';
import { driverVehicleLabel, formatDriverShift } from '@/components/drivers/driver-formatters';
import { Button } from '@/components/ui/button';
import type { Driver } from '@/domain/entities/driver.entity';
import { useLocale } from '@/i18n/provider';
import { ChevronRight, RefreshCw, Search, UserPlus } from 'lucide-react';
import { useState } from 'react';

const onboardingCopy: Record<Driver['onboarding_status'], { label: string; className: string }> = {
  INACTIVE: { label: 'Cần hoàn thiện', className: 'bg-amber-50 text-amber-800' },
  PENDING_APPROVAL: { label: 'Chờ duyệt', className: 'bg-blue-50 text-blue-800' },
  ACTIVE: { label: 'Đã duyệt', className: 'bg-emerald-50 text-emerald-800' },
  SUSPENDED: { label: 'Tạm ngưng', className: 'bg-red-50 text-red-800' },
};

export function DriverProfileStatus({ driver }: { driver: Driver }) {
  const { t } = useLocale();
  const state = onboardingCopy[driver.onboarding_status] ?? onboardingCopy.INACTIVE;
  return <span className={`inline-flex rounded-md px-1.5 py-0.5 text-xs font-medium ${state.className}`}>{t(state.label)}</span>;
}

interface DriverProfilesTableProps {
  drivers: Driver[];
  isLoading: boolean;
  error: string | null;
  canReview: boolean;
  onAdd?: () => void;
  onRefresh: () => void;
  onOpen: (driver: Driver, review?: boolean) => void;
}

export function DriverProfilesTable({
  drivers, isLoading, error, canReview, onAdd, onRefresh, onOpen,
}: DriverProfilesTableProps) {
  const { t, intlLocale } = useLocale();
  const [query, setQuery] = useState('');
  const search = stripVietnameseDiacritics(query).replaceAll(' ', '');
  const visible = drivers.filter((driver) => !search || [driver.name, driver.phone, driver.vehicle_plate]
    .some((value) => stripVietnameseDiacritics(value).replaceAll(' ', '').includes(search)));
  const reviewCount = drivers.filter((driver) => driver.onboarding_status === 'PENDING_APPROVAL').length;
  const readyCount = drivers.filter((driver) => driver.onboarding_status === 'ACTIVE' && driver.is_available).length;
  const number = new Intl.NumberFormat(intlLocale);

  return (
    <section aria-labelledby="driver-table-title" className="min-w-0 overflow-hidden rounded-xl border border-slate-200 bg-white">
      <div className="space-y-3 border-b border-slate-200 px-3 py-3 sm:px-4">
        <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
          <h2 id="driver-table-title" className="text-sm font-semibold text-slate-950">{t('Danh sách tài xế')}</h2>
          {!isLoading && !error && <p className="flex flex-wrap gap-x-3 text-xs tabular-nums text-slate-600" role="status">
            <span>{number.format(visible.length)}/{number.format(drivers.length)} {t('hồ sơ')}</span>
            <span>{number.format(readyCount)} {t('sẵn sàng nhận đơn')}</span>
            <span>{number.format(reviewCount)} {t('đang chờ duyệt')}</span>
          </p>}
        </div>
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div className="flex flex-wrap gap-2">
            {onAdd && <Button size="sm" type="button" onClick={onAdd} className="min-h-9">
              <UserPlus size={15} aria-hidden="true" />{t('Thêm tài xế')}
            </Button>}
            <Button size="sm" type="button" variant="outline" onClick={onRefresh} disabled={isLoading} className="min-h-9">
              <RefreshCw size={14} aria-hidden="true" />{t('Làm mới')}
            </Button>
          </div>
          <label className="relative w-full sm:w-72">
            <Search size={15} aria-hidden="true" className="pointer-events-none absolute start-2.5 top-1/2 -translate-y-1/2 text-slate-500" />
            <input type="search" aria-label={t('Tìm tài xế')} placeholder={t('Tên, số điện thoại hoặc biển số')}
              value={query} onChange={(event) => setQuery(event.target.value)}
              className="h-9 w-full rounded-sm border border-slate-300 bg-white pe-2 ps-8 text-sm text-slate-900 placeholder:text-slate-500 focus-visible:outline-2 focus-visible:outline-blue-600" />
          </label>
        </div>
      </div>
      {isLoading ? (
        <p role="status" className="px-4 py-10 text-center text-sm text-slate-600">{t('Đang tải hồ sơ…')}</p>
      ) : error ? (
        <p role="alert" className="bg-red-50 px-4 py-4 text-sm text-red-800">{error}</p>
      ) : visible.length === 0 ? (
        <p className="px-4 py-10 text-center text-sm text-slate-600">{drivers.length ? t('Không tìm thấy tài xế phù hợp.') : t('Chưa có tài xế')}</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-start text-sm">
            <thead className="border-b border-slate-200 text-xs text-slate-600">
              <tr>
                <th scope="col" className="px-3 py-2.5 text-start font-semibold sm:px-4">{t('Tài xế')}</th>
                <th scope="col" className="hidden px-3 py-2.5 text-start font-semibold md:table-cell">{t('Số điện thoại')}</th>
                <th scope="col" className="hidden px-3 py-2.5 text-start font-semibold sm:table-cell">{t('Phương tiện')}</th>
                <th scope="col" className="hidden px-3 py-2.5 text-start font-semibold lg:table-cell">{t('Ca làm')}</th>
                <th scope="col" className="px-2 py-2.5 text-start font-semibold sm:px-3">{t('Trạng thái')}</th>
                <th scope="col" className="px-3 py-2.5 text-end font-semibold sm:px-4"><span className="sr-only sm:not-sr-only">{t('Thao tác')}</span></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {visible.map((driver) => {
                const review = canReview && driver.onboarding_status === 'PENDING_APPROVAL';
                const type = driverVehicleLabel(driver.vehicle_type);
                return <tr key={driver.id} className="hover:bg-slate-50/70 focus-within:bg-slate-50">
                  <th scope="row" className="min-w-0 px-3 py-2.5 text-start sm:px-4">
                    <button type="button" onClick={() => onOpen(driver)}
                      className="rounded-sm text-start font-semibold break-words text-slate-950 underline-offset-4 hover:underline focus-visible:outline-2 focus-visible:outline-blue-600">
                      {driver.name}
                    </button>
                    {!driver.user_id && <span className="mt-0.5 block text-xs font-normal text-slate-600">{t('Chưa liên kết tài khoản')}</span>}
                  </th>
                  <td className="hidden whitespace-nowrap px-3 py-2.5 tabular-nums text-slate-700 md:table-cell">{driver.phone || '—'}</td>
                  <td className="hidden px-3 py-2.5 text-slate-700 sm:table-cell">
                    {type ? t(type) : '—'}
                    {driver.vehicle_plate && <span className="mt-0.5 block text-xs tabular-nums text-slate-600">{driver.vehicle_plate}</span>}
                  </td>
                  <td className="hidden whitespace-nowrap px-3 py-2.5 tabular-nums text-slate-700 lg:table-cell">{formatDriverShift(driver.shift_start, driver.shift_end)}</td>
                  <td className="px-2 py-2.5 sm:px-3">
                    <DriverProfileStatus driver={driver} />
                    {driver.onboarding_status === 'ACTIVE' && <p className="mt-0.5 text-xs text-slate-600">{driver.is_available ? t('Sẵn sàng nhận đơn') : t('Không sẵn sàng')}</p>}
                  </td>
                  <td className="px-2 py-2 text-end sm:px-4">
                    <Button type="button" size="sm" variant="ghost" onClick={() => onOpen(driver, review)}
                      aria-label={`${review ? t('Xét duyệt') : t('Xem hồ sơ')}: ${driver.name}`}
                      className="min-h-9 gap-1 px-2 text-blue-800">
                      <span className="hidden sm:inline">{review ? t('Xét duyệt') : t('Xem hồ sơ')}</span>
                      <ChevronRight aria-hidden="true" size={15} />
                    </Button>
                  </td>
                </tr>;
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
