'use client';

import { SlidersHorizontal } from 'lucide-react';
import { useEffect, useId, useRef, useState } from 'react';
import {
  DISPATCH_MAP_STATUSES, EMPTY_DISPATCH_MAP_FILTERS, type DispatchMapFilters,
} from '@/application/dispatch-map-filters';
import { PLAN_STATUS_META } from '@/components/dispatch/plan-status-badge';
import { Button } from '@/components/ui/button';
import { useLocale } from '@/i18n/provider';

interface RouteMapFiltersProps {
  filters: DispatchMapFilters;
  drivers: ReadonlyMap<string, string>;
  warehouses: ReadonlyMap<string, string>;
  showDeliveryStops: boolean;
  visibleCount: number;
  totalCount: number;
  onChange: (filters: DispatchMapFilters) => void;
  onShowDeliveryStopsChange: (value: boolean) => void;
}

const selectStyle = 'mt-1 h-10 w-full rounded-md border border-slate-300 bg-white px-2 text-sm text-slate-900 focus-visible:outline-2 focus-visible:outline-blue-700';

export function RouteMapFilters({
  filters, drivers, warehouses, showDeliveryStops, visibleCount, totalCount, onChange, onShowDeliveryStopsChange,
}: RouteMapFiltersProps) {
  const { t, intlLocale } = useLocale();
  const [open, setOpen] = useState(false);
  const id = useId();
  const trigger = useRef<HTMLButtonElement>(null);
  const panel = useRef<HTMLFieldSetElement>(null);
  const activeCount = Object.values(filters).filter(Boolean).length;
  const number = new Intl.NumberFormat(intlLocale);
  const byName = ([, left]: [string, string], [, right]: [string, string]) => left.localeCompare(right, intlLocale);

  useEffect(() => {
    if (!open) return;
    const dismiss = (event: PointerEvent) => {
      const target = event.target;
      if (target instanceof Node && !panel.current?.contains(target) && !trigger.current?.contains(target)) setOpen(false);
    };
    document.addEventListener('pointerdown', dismiss);
    return () => document.removeEventListener('pointerdown', dismiss);
  }, [open]);

  return (
    <div onKeyDown={(event) => {
      if (open && event.key === 'Escape') {
        event.preventDefault();
        setOpen(false);
        trigger.current?.focus();
      }
    }}>
      <Button ref={trigger} type="button" size="sm" variant="outline" aria-expanded={open} aria-controls={open ? id : undefined}
        onClick={() => setOpen((value) => !value)}
        className={`min-h-11 gap-1.5 rounded-xl bg-white px-3 shadow-sm ${activeCount ? 'border-blue-300 text-blue-800' : 'border-slate-200 text-slate-900'}`}>
        <SlidersHorizontal size={16} aria-hidden="true" />
        {t('Lọc bản đồ')}
        {activeCount > 0 && <span className="tabular-nums">({number.format(activeCount)})</span>}
      </Button>
      {open && (
        <fieldset ref={panel} id={id}
          className="absolute start-0 top-full mt-2 max-h-[min(22rem,50dvh)] w-[min(18rem,calc(100vw-2rem))] min-w-0 space-y-2 overflow-y-auto rounded-xl border border-slate-200 bg-white p-3 text-slate-950 shadow-md">
          <legend className="sr-only">{t('Lọc bản đồ')}</legend>
          <div className="flex items-center justify-between gap-2">
            <p className="text-xs font-semibold text-slate-700" role="status">
              {number.format(visibleCount)}/{number.format(totalCount)} {t('tuyến hiển thị')}
            </p>
            <button type="button" disabled={!activeCount} onClick={() => onChange(EMPTY_DISPATCH_MAP_FILTERS)}
              className="min-h-8 rounded px-1 text-xs font-semibold text-blue-700 hover:underline focus-visible:outline-2 focus-visible:outline-blue-700 disabled:text-slate-500 disabled:no-underline">
              {t('Xóa bộ lọc')}
            </button>
          </div>
          <label className="block text-xs font-medium text-slate-700">
            {t('Tài xế')}
            <select value={filters.driver} onChange={(event) => onChange({ ...filters, driver: event.target.value })} className={selectStyle}>
              <option value="">{t('Tất cả tài xế')}</option>
              {[...drivers].sort(byName).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
            </select>
          </label>
          <label className="block text-xs font-medium text-slate-700">
            {t('Kho xuất phát')}
            <select value={filters.warehouse} onChange={(event) => onChange({ ...filters, warehouse: event.target.value })} className={selectStyle}>
              <option value="">{t('Tất cả kho')}</option>
              {[...warehouses].sort(byName).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
            </select>
          </label>
          <label className="block text-xs font-medium text-slate-700">
            {t('Trạng thái tuyến')}
            <select value={filters.status}
              onChange={(event) => onChange({ ...filters, status: event.target.value as DispatchMapFilters['status'] })}
              className={selectStyle}>
              <option value="">{t('Tất cả trạng thái')}</option>
              {DISPATCH_MAP_STATUSES.map((status) => <option key={status} value={status}>{t(PLAN_STATUS_META[status].label)}</option>)}
            </select>
          </label>
          <label className="flex min-h-10 cursor-pointer items-center justify-between gap-3 border-t border-slate-100 pt-1 text-sm font-medium">
            {t('Hiện điểm giao')}
            <input type="checkbox" role="switch" checked={showDeliveryStops} onChange={(event) => onShowDeliveryStopsChange(event.target.checked)}
              className="size-4 accent-blue-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-700" />
          </label>
        </fieldset>
      )}
    </div>
  );
}
