'use client';

import { formatIncidentType } from '@/application/operations-display';
import { Button } from '@/components/ui/button';
import { useLocale } from '@/i18n/provider';
import {
  deliveryReportRepository,
  type DeliveryReport,
  type DeliveryReportDay,
  type DeliveryReportFilters,
} from '@/infrastructure/repositories/delivery-report.repository';
import { ArrowUpRight } from 'lucide-react';
import { useEffect, useState, type FormEvent } from 'react';
import { DeliveryReportDetails } from './delivery-report-details';
import { DeliveryReportCharts } from './delivery-report-charts';

const zone = 'Asia/Ho_Chi_Minh';
const dayMs = 86_400_000;
const fieldClass = 'min-h-11 w-full min-w-0 rounded-sm border border-slate-300 bg-white px-3 text-sm text-slate-900 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-700';
const linkClass = 'inline-flex min-h-11 items-center gap-1 rounded-sm text-sm font-medium text-blue-800 underline decoration-blue-300 underline-offset-4 hover:text-blue-950 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-700';
const mobileCell = "max-sm:px-0 max-sm:py-0 max-sm:text-left max-sm:before:block max-sm:before:text-xs max-sm:before:text-slate-600 max-sm:before:content-[attr(data-label)]";
const zeroDay = { delivered: 0, with_deadline: 0, on_time: 0, first_attempts: 0, first_successes: 0, failures: 0 };

function localDate() {
  return new Intl.DateTimeFormat('en-CA', { timeZone: zone, year: 'numeric', month: '2-digit', day: '2-digit' }).format();
}

function dateOffset(date: string, days: number) {
  return new Date(Date.parse(date) + days * dayMs).toISOString().slice(0, 10);
}

function reportDays(report: DeliveryReport, filters: DeliveryReportFilters) {
  const byDate = new Map(report.days.map((day) => [day.date, day]));
  const days: DeliveryReportDay[] = [];
  for (let date = filters.to; date >= filters.from; date = dateOffset(date, -1)) {
    days.push(byDate.get(date) ?? { date, ...zeroDay });
    if (date === filters.from) break;
  }
  return days;
}

export function DeliveryReportView() {
  const { t } = useLocale();
  const today = localDate();
  const [filters, setFilters] = useState<DeliveryReportFilters>(() => ({ from: dateOffset(today, -6), to: today }));
  const [revision, setRevision] = useState(0);
  const [validation, setValidation] = useState('');
  const [warehouses, setWarehouses] = useState<DeliveryReport['warehouses']>([]);
  const [result, setResult] = useState<{ revision: number; data?: DeliveryReport }>();
  const [from, setFrom] = useState(filters.from);
  const [to, setTo] = useState(filters.to);
  const [warehouse, setWarehouse] = useState('');

  useEffect(() => {
    const controller = new AbortController();
    deliveryReportRepository.overview(filters, controller.signal).then(
      (data) => {
        if (!controller.signal.aborted) {
          setWarehouses(data.warehouses);
          setResult({ revision, data });
        }
      },
      () => { if (!controller.signal.aborted) setResult({ revision }); },
    );
    return () => controller.abort();
  }, [filters, revision]);

  function apply(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!from || !to || from > to || to > today || from < '0001-01-01' || Date.parse(to) - Date.parse(from) >= 90 * dayMs) {
      setValidation('Chọn khoảng thời gian hợp lệ, tối đa 90 ngày và không vượt quá hôm nay.');
      return;
    }
    setValidation('');
    setFilters({ from, to, warehouse: warehouse || undefined });
    setRevision((value) => value + 1);
  }

  const current = result?.revision === revision ? result : undefined;

  return (
    <div className="mx-auto max-w-7xl space-y-5" data-ui>
      <header>
        <h1 className="text-2xl font-semibold tracking-tight text-slate-950">{t('Báo cáo giao hàng')}</h1>
      </header>
      <form noValidate onSubmit={apply} className="space-y-2 rounded-xl border border-slate-200 p-3 sm:p-4">
        <div className="grid grid-cols-2 items-end gap-3 xl:grid-cols-[minmax(8rem,11rem)_minmax(8rem,11rem)_minmax(10rem,16rem)_auto] xl:justify-start">
          <label className="grid min-w-0 gap-1.5 text-sm font-medium text-slate-700">
            {t('Từ ngày')}
            <input type="date" required data-sr-input="true" max={today} value={from} aria-invalid={Boolean(validation)}
              aria-describedby={validation ? 'report-date-error' : undefined} onChange={(event) => setFrom(event.target.value)} className={fieldClass} />
          </label>
          <label className="grid min-w-0 gap-1.5 text-sm font-medium text-slate-700">
            {t('Đến ngày')}
            <input type="date" required data-sr-input="true" max={today} value={to} aria-invalid={Boolean(validation)}
              aria-describedby={validation ? 'report-date-error' : undefined} onChange={(event) => setTo(event.target.value)} className={fieldClass} />
          </label>
          <label className="grid min-w-0 gap-1.5 text-sm font-medium text-slate-700">
            {t('Kho lấy hàng')}
            <select value={warehouse} onChange={(event) => setWarehouse(event.target.value)} className={fieldClass}>
              <option value="">{t('Tất cả kho')}</option>
              {warehouses.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
            </select>
          </label>
          <Button type="submit" className="min-h-11 bg-slate-900 hover:bg-slate-800">{t('Áp dụng')}</Button>
        </div>
        {validation && <p id="report-date-error" role="alert" className="text-sm text-red-700">{t(validation)}</p>}
      </form>
      {!current ? (
        <div role="status" className="min-h-28 rounded-xl border border-slate-200 px-4 py-5 text-sm text-slate-600">{t('Đang tải báo cáo giao hàng…')}</div>
      ) : !current.data ? (
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-slate-200 px-4 py-3">
          <p role="alert" className="text-sm text-slate-700">{t('Không tải được báo cáo giao hàng.')}</p>
          <Button variant="outline" onClick={() => setRevision((value) => value + 1)}>{t('Thử lại')}</Button>
        </div>
      ) : <ReportResults report={current.data} filters={filters} warehouses={warehouses} />}
    </div>
  );
}

function ReportResults({ report, filters, warehouses }: {
  report: DeliveryReport;
  filters: DeliveryReportFilters;
  warehouses: DeliveryReport['warehouses'];
}) {
  const { t, intlLocale } = useLocale();
  const number = new Intl.NumberFormat(intlLocale);
  const dateLabel = new Intl.DateTimeFormat(intlLocale, { day: 'numeric', month: '2-digit', year: 'numeric', timeZone: 'UTC' });
  const percent = new Intl.NumberFormat(intlLocale, { style: 'percent', maximumFractionDigits: 1 });
  const date = (value: string) => dateLabel.format(new Date(value));
  const rate = (value: number, total: number) => total ? percent.format(value / total) : '—';
  const fraction = (value: number, total: number) => `${number.format(value)} / ${number.format(total)}`;
  const days = reportDays(report, filters);
  const totals = report.days.reduce((sum, day) => ({
    delivered: sum.delivered + day.delivered,
    with_deadline: sum.with_deadline + day.with_deadline,
    on_time: sum.on_time + day.on_time,
    first_attempts: sum.first_attempts + day.first_attempts,
    first_successes: sum.first_successes + day.first_successes,
    failures: sum.failures + day.failures,
  }), zeroDay);
  const late = totals.with_deadline - totals.on_time;
  const scope = warehouses.find((item) => item.id === filters.warehouse)?.name ?? t('Tất cả kho');
  const metrics = [
    { label: 'Đơn giao thành công', value: number.format(totals.delivered), note: t('Vận đơn đã giao trong kỳ') },
    { label: 'Tỷ lệ giao đúng hẹn', value: rate(totals.on_time, totals.with_deadline), note: `${fraction(totals.on_time, totals.with_deadline)} ${t('đơn có hạn giao')}` },
    { label: 'Thành công lần đầu', value: rate(totals.first_successes, totals.first_attempts), note: `${fraction(totals.first_successes, totals.first_attempts)} ${t('lần giao đầu tiên')}` },
    { label: 'Lượt giao thất bại', value: number.format(totals.failures), note: t('Theo từng lần giao') },
  ];
  const failureName = (reason: string) => reason === 'UNSPECIFIED' ? t('Chưa ghi lý do') : formatIncidentType(reason, t);

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-1 text-sm text-slate-600">
        <p role="status">{scope} · {date(filters.from)} – {date(filters.to)} · {t('Giờ Việt Nam')}</p>
        <p><strong className={report.overdue ? 'text-amber-800' : 'text-slate-800'}>{number.format(report.overdue)}</strong> {t('đơn quá hạn còn chờ giao')}</p>
      </div>
      <dl className="grid grid-cols-2 gap-px overflow-hidden rounded-xl border border-slate-200 bg-slate-200 lg:grid-cols-4">
        {metrics.map((metric) => (
          <div key={metric.label} className="min-w-0 bg-white px-3 py-3 sm:px-4">
            <dt className="text-sm font-medium text-slate-700">{t(metric.label)}</dt>
            <dd className="mt-1 text-2xl font-semibold tabular-nums text-slate-950">{metric.value}</dd>
            <dd className="mt-1 text-xs text-slate-600">{metric.note}</dd>
          </div>
        ))}
      </dl>

      {report.days.length > 0 && <DeliveryReportCharts days={days} />}
      <div className="grid items-start gap-4 xl:grid-cols-[minmax(0,2fr)_minmax(16.5rem,1fr)]">
        <section aria-labelledby="report-by-day" className="min-w-0 rounded-xl border border-slate-200">
          <div className="flex flex-wrap items-center justify-between gap-x-4 px-4 py-2.5">
            <h2 id="report-by-day" className="text-base font-semibold text-slate-950">{t('Chi tiết theo ngày')}</h2>
            {late > 0 && (
              <DeliveryReportDetails title={t('Đơn giao trễ')} filters={filters} detail={{ kind: 'LATE' }} trigger={
                <button type="button" className={linkClass}>
                  {number.format(late)} {t('đơn giao trễ')} <ArrowUpRight size={14} aria-hidden="true" />
                </button>
              } />
            )}
          </div>
          {report.days.length === 0 && <p className="px-4 pb-3 text-sm text-slate-600">{t('Chưa ghi nhận lần giao nào trong kỳ này.')}</p>}
          {report.days.length > 0 && <details>
          <summary className="cursor-pointer px-4 pb-3 text-sm font-medium text-blue-800">{t('Xem bảng số liệu')}</summary>
          <div role="region" aria-labelledby="report-by-day" tabIndex={0} className="overflow-x-auto focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-blue-700">
            <table role="table" className="w-full text-sm tabular-nums max-sm:block sm:min-w-[34rem]">
              <thead role="rowgroup" className="border-y border-slate-200 text-slate-600 max-sm:sr-only">
                <tr role="row">
                  <th role="columnheader" scope="col" className="px-4 py-2.5 text-left font-medium">{t('Ngày')}</th>
                  <th role="columnheader" scope="col" className="px-3 py-2.5 text-right font-medium">{t('Đã giao')}</th>
                  <th role="columnheader" scope="col" className="px-3 py-2.5 text-right font-medium">{t('Đúng hẹn')}</th>
                  <th role="columnheader" scope="col" className="px-3 py-2.5 text-right font-medium">{t('Giao trễ')}</th>
                  <th role="columnheader" scope="col" className="px-3 py-2.5 text-right font-medium">{t('Lần đầu')}</th>
                  <th role="columnheader" scope="col" className="px-4 py-2.5 text-right font-medium">{t('Thất bại')}</th>
                </tr>
              </thead>
              <tbody role="rowgroup" className="divide-y divide-slate-100 text-slate-900 max-sm:block">
                {days.map((day) => {
                  const dailyLate = day.with_deadline - day.on_time;
                  return (
                    <tr key={day.date} role="row" className="max-sm:grid max-sm:grid-cols-3 max-sm:items-start max-sm:gap-x-3 max-sm:gap-y-2 max-sm:px-3 max-sm:py-3">
                      <th role="rowheader" scope="row" className="whitespace-nowrap px-4 py-2.5 text-left font-medium max-sm:col-span-3 max-sm:p-0">{date(day.date)}</th>
                      <td role="cell" data-label={t('Đã giao')} className={'px-3 py-2.5 text-right ' + mobileCell}>{number.format(day.delivered)}</td>
                      <td role="cell" data-label={t('Đúng hẹn')} className={'px-3 py-2.5 text-right ' + mobileCell}>
                        {day.with_deadline ? (
                          <div className="flex items-center justify-end gap-2 max-sm:justify-start" title={fraction(day.on_time, day.with_deadline)}>
                            <span className="hidden h-1.5 w-10 overflow-hidden rounded-full bg-slate-200 sm:block" aria-hidden="true">
                              <span className="block h-full bg-blue-700" style={{ width: (day.on_time / day.with_deadline) * 100 + "%" }} />
                            </span>
                            <span className="sm:w-14">{rate(day.on_time, day.with_deadline)}</span>
                          </div>
                        ) : '—'}
                      </td>
                      <td role="cell" data-label={t('Giao trễ')} className={'px-3 text-right ' + mobileCell}>
                        {dailyLate > 0 ? (
                          <DeliveryReportDetails title={`${t('Đơn giao trễ')} · ${date(day.date)}`}
                            filters={{ ...filters, from: day.date, to: day.date }} detail={{ kind: 'LATE' }} trigger={
                              <button type="button" className={linkClass + " max-sm:mr-0 max-sm:min-w-8 max-sm:justify-start"} aria-label={`${number.format(dailyLate)} ${t('đơn giao trễ')} · ${date(day.date)}`}>
                                {number.format(dailyLate)}
                              </button>
                            } />
                        ) : '0'}
                      </td>
                      <td role="cell" data-label={t('Lần đầu')} className={'whitespace-nowrap px-3 py-2.5 text-right ' + mobileCell}>{day.first_attempts ? fraction(day.first_successes, day.first_attempts) : '—'}</td>
                      <td role="cell" data-label={t('Thất bại')} className={'px-4 py-2.5 text-right ' + mobileCell}>{number.format(day.failures)}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          </details>}
        </section>

        <section aria-labelledby="report-failure-reasons" className="min-w-0 rounded-xl border border-slate-200">
          <div className="border-b border-slate-200 px-4 py-3">
            <h2 id="report-failure-reasons" className="text-base font-semibold text-slate-950">{t('Lý do giao thất bại')}</h2>
          </div>
          {report.failures.length ? (
            <ul className="divide-y divide-slate-100">
              {report.failures.map(({ reason, count }) => (
                <li key={reason}>
                  <DeliveryReportDetails title={failureName(reason)} filters={filters} detail={{ kind: 'FAILURE', reason }} trigger={
                    <button type="button" className="flex min-h-11 w-full items-center justify-between gap-3 px-4 py-2 text-left text-sm text-slate-800 hover:bg-slate-50 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-blue-700">
                      <span className="min-w-0 break-words">{failureName(reason)}</span>
                      <span className="flex shrink-0 items-center gap-2 font-medium tabular-nums text-blue-800">
                        {number.format(count)} <ArrowUpRight size={14} aria-hidden="true" />
                      </span>
                    </button>
                  } />
                </li>
              ))}
            </ul>
          ) : <p className="px-4 py-3 text-sm text-slate-600">{t('Không có lượt giao thất bại trong kỳ này.')}</p>}
        </section>
      </div>
    </div>
  );
}
