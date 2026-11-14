'use client';

import { FormEvent, Fragment, useMemo, useState } from 'react';
import { ChevronDown, ChevronRight } from 'lucide-react';
import { toast } from 'sonner';
import { useLocale } from '@/i18n/provider';
import { DriverEarning, DriverSettlement, SettlementStatus } from '@/domain/entities/earning.entity';
import { earningRepository } from '@/infrastructure/repositories/earning.repository';
import { Button } from '@/components/ui/button';
import {
  formatPeriodDate,
  formatPeriodDateTime,
  formatVnd,
  getCurrentSettlementMonthRange,
} from './driver-formatters';

interface DriverSettlementsPanelProps {
  earnings: DriverEarning[];
  settlements: DriverSettlement[];
  loading: boolean;
  error: string | null;
  onReload: () => Promise<void>;
}

type StatusFilter = 'ALL' | SettlementStatus;

function StatusBadge({ status }: { status: SettlementStatus }) {
  const { t } = useLocale();
  const meta: Record<SettlementStatus, { label: string; className: string }> = {
    DRAFT: { label: t('Chờ duyệt'), className: 'bg-amber-50 text-amber-800' },
    APPROVED: { label: t('Đã duyệt'), className: 'bg-blue-50 text-blue-800' },
    PAID: { label: t('Đã trả'), className: 'bg-emerald-50 text-emerald-800' },
  };
  const current = meta[status];
  return (
    <span className={`inline-flex items-center rounded-md px-2 py-0.5 text-xs font-medium ${current.className}`}>
      {current.label}
    </span>
  );
}

/** Driver earnings queue, settlement history table, and inline payout confirmation workflow. */
export function DriverSettlementsPanel({
  earnings,
  settlements,
  loading,
  error,
  onReload,
}: DriverSettlementsPanelProps) {
  const { t, intlLocale } = useLocale();
  const [busyId, setBusyId] = useState<string | null>(null);
  const [payingId, setPayingId] = useState<string | null>(null);
  const [paymentReference, setPaymentReference] = useState('');
  const [paymentError, setPaymentError] = useState('');
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL');
  const [expandedKey, setExpandedKey] = useState<string | null>(null);

  const monthRange = useMemo(() => getCurrentSettlementMonthRange(), []);
  const monthRangeLabel = `${formatPeriodDate(monthRange.start, intlLocale)} – ${formatPeriodDate(monthRange.end, intlLocale)}`;

  const pendingByDriver = useMemo(() => {
    const grouped = new Map<
      string,
      { driverId: string; driverName: string; amount: number; count: number; entries: DriverEarning[] }
    >();
    earnings
      .filter((item) => item.status === 'PENDING' && !item.settlement_id)
      .forEach((item) => {
        const current = grouped.get(item.driver_id) || {
          driverId: item.driver_id,
          driverName: item.driver_name,
          amount: 0,
          count: 0,
          entries: [],
        };
        current.amount += item.net_amount;
        current.count += 1;
        current.entries.push(item);
        grouped.set(item.driver_id, current);
      });
    return [...grouped.values()].sort((a, b) => b.amount - a.amount);
  }, [earnings]);

  const earningsBySettlement = useMemo(() => {
    const grouped = new Map<string, DriverEarning[]>();
    earnings.forEach((entry) => {
      if (!entry.settlement_id) return;
      const list = grouped.get(entry.settlement_id) || [];
      list.push(entry);
      grouped.set(entry.settlement_id, list);
    });
    return grouped;
  }, [earnings]);

  const totals = useMemo(() => {
    const pendingEntries = earnings.filter((item) => item.status === 'PENDING');
    const approvedEntries = earnings.filter((item) => item.status === 'APPROVED');
    const paidEntries = earnings.filter((item) => item.status === 'PAID');
    return {
      pendingAmount: pendingEntries.reduce((sum, item) => sum + item.net_amount, 0),
      pendingCount: pendingEntries.length,
      approvedAmount: approvedEntries.reduce((sum, item) => sum + item.net_amount, 0),
      approvedCount: approvedEntries.length,
      paidAmount: paidEntries.reduce((sum, item) => sum + item.net_amount, 0),
      paidCount: paidEntries.length,
    };
  }, [earnings]);

  const settlementCounts = useMemo(
    () => ({
      ALL: settlements.length,
      DRAFT: settlements.filter((item) => item.status === 'DRAFT').length,
      APPROVED: settlements.filter((item) => item.status === 'APPROVED').length,
      PAID: settlements.filter((item) => item.status === 'PAID').length,
    }),
    [settlements]
  );

  const visibleSettlements = useMemo(
    () => (statusFilter === 'ALL' ? settlements : settlements.filter((item) => item.status === statusFilter)),
    [settlements, statusFilter]
  );

  async function createSettlement(driverId: string) {
    setBusyId(driverId);
    try {
      const range = getCurrentSettlementMonthRange();
      await earningRepository.createSettlement(driverId, range.start, range.end);
      toast.success(t('Đã lập kỳ đối soát'));
      await onReload();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : t('Không thể lập đối soát'));
    } finally {
      setBusyId(null);
    }
  }

  async function approve(id: string) {
    setBusyId(id);
    try {
      await earningRepository.approve(id);
      toast.success(t('Đã duyệt kỳ đối soát'));
      await onReload();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : t('Không thể duyệt'));
    } finally {
      setBusyId(null);
    }
  }

  function startPay(id: string) {
    setPayingId(id);
    setPaymentReference('');
    setPaymentError('');
  }

  function cancelPay() {
    setPayingId(null);
    setPaymentReference('');
    setPaymentError('');
  }

  async function submitPay(event: FormEvent<HTMLFormElement>, id: string) {
    event.preventDefault();
    const reference = paymentReference.trim();
    if (!reference) {
      setPaymentError(t('Vui lòng nhập mã tham chiếu thanh toán.'));
      return;
    }
    setBusyId(id);
    setPaymentError('');
    try {
      await earningRepository.markPaid(id, reference);
      toast.success(t('Đã xác nhận thanh toán'));
      cancelPay();
      await onReload();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : t('Không thể xác nhận thanh toán'));
    } finally {
      setBusyId(null);
    }
  }

  function toggleExpand(key: string) {
    setExpandedKey((current) => (current === key ? null : key));
  }

  const filterOptions: Array<{ key: StatusFilter; label: string; count: number }> = [
    { key: 'ALL', label: t('Tất cả'), count: settlementCounts.ALL },
    { key: 'DRAFT', label: t('Chờ duyệt'), count: settlementCounts.DRAFT },
    { key: 'APPROVED', label: t('Đã duyệt'), count: settlementCounts.APPROVED },
    { key: 'PAID', label: t('Đã trả'), count: settlementCounts.PAID },
  ];

  return (
    <div className="space-y-6">
      {error && (
        <div
          role="alert"
          className="flex flex-col gap-3 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800 sm:flex-row sm:items-center sm:justify-between"
        >
          <span>{error}</span>
          <Button size="sm" variant="outline" onClick={() => void onReload()}>
            {t('Thử lại')}
          </Button>
        </div>
      )}

      <section className="grid divide-y divide-slate-200 rounded-xl border border-slate-200 bg-white sm:grid-cols-3 sm:divide-x sm:divide-y-0">
        <div className="px-5 py-4">
          <p className="text-xs text-slate-500">{t('Chờ đối soát')}</p>
          <p className="mt-1 text-2xl font-semibold tabular-nums text-slate-950">
            {loading ? '—' : formatVnd(totals.pendingAmount, intlLocale)}
          </p>
          <p className="mt-1 text-xs tabular-nums text-slate-500">
            {totals.pendingCount} {t('khoản')} · {pendingByDriver.length} {t('tài xế sẵn sàng lập kỳ')}
          </p>
        </div>
        <div className="px-5 py-4">
          <p className="text-xs text-slate-500">{t('Đã duyệt')}</p>
          <p className="mt-1 text-2xl font-semibold tabular-nums text-slate-950">
            {loading ? '—' : formatVnd(totals.approvedAmount, intlLocale)}
          </p>
          <p className="mt-1 text-xs tabular-nums text-slate-500">
            {totals.approvedCount} {t('khoản')} · {settlementCounts.APPROVED} {t('kỳ chờ chi trả')}
          </p>
        </div>
        <div className="px-5 py-4">
          <p className="text-xs text-slate-500">{t('Đã thanh toán')}</p>
          <p className="mt-1 text-2xl font-semibold tabular-nums text-slate-950">
            {loading ? '—' : formatVnd(totals.paidAmount, intlLocale)}
          </p>
          <p className="mt-1 text-xs tabular-nums text-slate-500">
            {totals.paidCount} {t('khoản')} · {settlementCounts.PAID} {t('kỳ đã hoàn tất')}
          </p>
        </div>
      </section>

      <p className="text-xs text-slate-500">
        {t('Duyệt các khoản thu nhập đã hoàn tất và ghi nhận thanh toán theo kỳ. COD không được tính vào số tiền này.')}
      </p>

      <section>
        <div className="mb-3 flex flex-wrap items-baseline justify-between gap-2">
          <h2 className="text-base font-semibold text-slate-950">{t('Sẵn sàng lập kỳ')}</h2>
          <span className="text-xs tabular-nums text-slate-500">
            {t('Tháng hiện tại')} ({monthRangeLabel})
          </span>
        </div>

        <div className="overflow-hidden rounded-xl border border-slate-200 bg-white">
          {loading ? (
            <div className="divide-y divide-slate-100">
              {[1, 2].map((item) => (
                <div key={item} className="h-16 animate-pulse bg-slate-50/70" />
              ))}
            </div>
          ) : pendingByDriver.length === 0 ? (
            <div className="px-5 py-4 text-sm text-slate-500">
              {t('Không có khoản thu nhập nào đang chờ lập kỳ.')}
            </div>
          ) : (
            <div className="divide-y divide-slate-100">
              {pendingByDriver.map((row) => {
                const key = `pending:${row.driverId}`;
                const expanded = expandedKey === key;
                return (
                  <div key={row.driverId}>
                    <div className="flex flex-col gap-3 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
                      <div className="min-w-0">
                        <div className="flex items-center gap-2">
                          <button
                            type="button"
                            onClick={() => toggleExpand(key)}
                            aria-expanded={expanded}
                            className="inline-flex items-center gap-1.5 text-left font-medium text-slate-950 hover:text-slate-700"
                          >
                            {expanded ? <ChevronDown size={15} className="text-slate-400" /> : <ChevronRight size={15} className="text-slate-400" />}
                            <span>{row.driverName}</span>
                          </button>
                        </div>
                        <p className="mt-0.5 pl-5 text-xs text-slate-500 tabular-nums">
                          {row.count} {t('nhiệm vụ đủ điều kiện')}
                        </p>
                      </div>
                      <div className="flex items-center justify-between gap-5 sm:justify-end">
                        <p className="font-semibold tabular-nums text-slate-950">
                          {formatVnd(row.amount, intlLocale)}
                        </p>
                        <Button
                          size="sm"
                          isLoading={busyId === row.driverId}
                          onClick={() => void createSettlement(row.driverId)}
                        >
                          {t('Lập đối soát')}
                        </Button>
                      </div>
                    </div>
                    {expanded && <EarningBreakdown rows={row.entries} intlLocale={intlLocale} />}
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </section>

      <section>
        <div className="mb-3 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <h2 className="text-base font-semibold text-slate-950">{t('Các kỳ đối soát')}</h2>
          {settlements.length > 0 && (
            <div className="flex flex-wrap gap-1.5" role="group" aria-label={t('Lọc kỳ đối soát theo trạng thái')}>
              {filterOptions.map((option) => (
                <button
                  key={option.key}
                  type="button"
                  onClick={() => setStatusFilter(option.key)}
                  className={`rounded-lg border px-2.5 py-1 text-xs font-medium transition-colors ${
                    statusFilter === option.key
                      ? 'border-slate-900 bg-slate-900 text-white'
                      : 'border-slate-200 bg-white text-slate-600 hover:bg-slate-50 hover:text-slate-900'
                  }`}
                >
                  {option.label} <span className="tabular-nums opacity-75">({option.count})</span>
                </button>
              ))}
            </div>
          )}
        </div>

        <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white">
          <table className="min-w-full text-sm">
            <thead className="border-b border-slate-200 bg-slate-50/70 text-left text-xs font-medium text-slate-500">
              <tr>
                <th className="px-5 py-3 font-medium">{t('Tài xế')}</th>
                <th className="px-5 py-3 font-medium">{t('Kỳ')}</th>
                <th className="px-5 py-3 font-medium">{t('Trạng thái')}</th>
                <th className="px-5 py-3 text-right font-medium">{t('Tổng')}</th>
                <th className="px-5 py-3 text-right font-medium">{t('Thao tác')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {loading ? (
                <tr>
                  <td colSpan={5} className="px-5 py-10 text-center text-sm text-slate-500">
                    {t('Đang tải dữ liệu đối soát…')}
                  </td>
                </tr>
              ) : visibleSettlements.length === 0 ? (
                <tr>
                  <td colSpan={5} className="px-5 py-10 text-center text-sm text-slate-500">
                    {settlements.length === 0
                      ? t('Chưa có kỳ đối soát nào.')
                      : t('Không có kỳ đối soát nào ở trạng thái đã chọn.')}
                  </td>
                </tr>
              ) : (
                visibleSettlements.map((item) => {
                  const entries = earningsBySettlement.get(item.id) || [];
                  const expandKey = `settlement:${item.id}`;
                  const isExpanded = expandedKey === expandKey;
                  const isPaying = payingId === item.id;
                  const exactPeriodTooltip = `${formatPeriodDateTime(item.period_start, intlLocale)} – ${formatPeriodDateTime(item.period_end, intlLocale)}`;

                  return (
                    <Fragment key={item.id}>
                      <tr className="text-slate-700 hover:bg-slate-50/60">
                        <td className="px-5 py-3.5">
                          <div className="flex items-start gap-1.5">
                            {entries.length > 0 ? (
                              <button
                                type="button"
                                onClick={() => toggleExpand(expandKey)}
                                aria-expanded={isExpanded}
                                aria-label={t('Xem chi tiết khoản thu nhập')}
                                className="mt-0.5 rounded p-0.5 text-slate-400 hover:bg-slate-100 hover:text-slate-700"
                              >
                                {isExpanded ? <ChevronDown size={15} /> : <ChevronRight size={15} />}
                              </button>
                            ) : null}
                            <div>
                              <p className="font-medium text-slate-950">{item.driver_name}</p>
                              <p className="mt-0.5 text-xs tabular-nums text-slate-500">
                                {item.earning_count} {t('khoản')}
                              </p>
                            </div>
                          </div>
                        </td>
                        <td
                          className="whitespace-nowrap px-5 py-3.5 text-xs tabular-nums text-slate-600"
                          title={exactPeriodTooltip}
                        >
                          {formatPeriodDate(item.period_start, intlLocale)} –{' '}
                          {formatPeriodDate(item.period_end, intlLocale)}
                        </td>
                        <td className="whitespace-nowrap px-5 py-3.5">
                          <StatusBadge status={item.status} />
                        </td>
                        <td className="whitespace-nowrap px-5 py-3.5 text-right font-semibold tabular-nums text-slate-950">
                          {formatVnd(item.total_amount, intlLocale)}
                        </td>
                        <td className="whitespace-nowrap px-5 py-3.5 text-right">
                          {item.status === 'DRAFT' && (
                            <Button
                              size="sm"
                              variant="outline"
                              isLoading={busyId === item.id}
                              onClick={() => void approve(item.id)}
                            >
                              {t('Duyệt')}
                            </Button>
                          )}
                          {item.status === 'APPROVED' && (
                            <Button
                              size="sm"
                              variant={isPaying ? 'outline' : 'default'}
                              disabled={busyId === item.id}
                              onClick={() => (isPaying ? cancelPay() : startPay(item.id))}
                            >
                              {isPaying ? t('Đóng') : t('Xác nhận đã trả')}
                            </Button>
                          )}
                          {item.status === 'PAID' && (
                            <div className="text-right">
                              <span className="font-mono text-xs text-slate-700">
                                {item.payment_reference ? `${t('Mã GD')}: ${item.payment_reference}` : '—'}
                              </span>
                              {item.paid_at && (
                                <p className="mt-0.5 text-xs tabular-nums text-slate-400">
                                  {formatPeriodDate(item.paid_at, intlLocale)}
                                </p>
                              )}
                            </div>
                          )}
                        </td>
                      </tr>

                      {isPaying && (
                        <tr className="bg-slate-50/90">
                          <td colSpan={5} className="px-5 py-3.5">
                            <form
                              onSubmit={(event) => void submitPay(event, item.id)}
                              onKeyDown={(event) => {
                                if (event.key === 'Escape') cancelPay();
                              }}
                              className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between"
                            >
                              <div className="min-w-0 flex-1 sm:max-w-md">
                                <label
                                  htmlFor={`payment-ref-${item.id}`}
                                  className="block text-xs font-medium text-slate-700"
                                >
                                  {t('Mã tham chiếu thanh toán')} ({item.driver_name} ·{' '}
                                  <span className="tabular-nums">{formatVnd(item.total_amount, intlLocale)}</span>)
                                </label>
                                <input
                                  id={`payment-ref-${item.id}`}
                                  type="text"
                                  autoFocus
                                  maxLength={120}
                                  value={paymentReference}
                                  onChange={(event) => {
                                    setPaymentReference(event.target.value);
                                    if (paymentError) setPaymentError('');
                                  }}
                                  placeholder={t('VD: VCB-FT2631800123')}
                                  className="mt-1.5 w-full rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-sm text-slate-900 placeholder:text-slate-400 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500/20"
                                />
                                {paymentError && <p className="mt-1 text-xs text-red-600">{paymentError}</p>}
                              </div>
                              <div className="flex shrink-0 gap-2">
                                <Button type="button" size="sm" variant="outline" onClick={cancelPay} disabled={busyId === item.id}>
                                  {t('Hủy')}
                                </Button>
                                <Button type="submit" size="sm" isLoading={busyId === item.id}>
                                  {t('Xác nhận thanh toán')}
                                </Button>
                              </div>
                            </form>
                          </td>
                        </tr>
                      )}

                      {isExpanded && entries.length > 0 && (
                        <tr>
                          <td colSpan={5} className="p-0">
                            <EarningBreakdown rows={entries} intlLocale={intlLocale} />
                          </td>
                        </tr>
                      )}
                    </Fragment>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}

function EarningBreakdown({ rows, intlLocale }: { rows: DriverEarning[]; intlLocale: string }) {
  const { t } = useLocale();
  return (
    <div className="border-t border-slate-100 bg-slate-50/70 px-5 py-3">
      <div className="overflow-x-auto">
        <table className="min-w-full text-xs text-slate-600">
          <thead>
            <tr className="border-b border-slate-200/80 text-left font-medium text-slate-500">
              <th className="py-1.5 pr-4 font-medium">{t('Hoàn tất lúc')}</th>
              <th className="py-1.5 pr-4 text-right font-medium">{t('Quãng đường')}</th>
              <th className="py-1.5 pr-4 text-right font-medium">{t('Phí cơ bản')}</th>
              <th className="py-1.5 pr-4 text-right font-medium">{t('Phí khoảng cách')}</th>
              <th className="py-1.5 pr-4 text-right font-medium">{t('Phụ phí')}</th>
              <th className="py-1.5 text-right font-medium">{t('Thực nhận')}</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-200/60 tabular-nums">
            {rows.map((entry) => (
              <tr key={entry.id}>
                <td className="whitespace-nowrap py-2 pr-4 text-slate-700">
                  {formatPeriodDateTime(entry.completed_at, intlLocale)}
                </td>
                <td className="whitespace-nowrap py-2 pr-4 text-right">
                  {entry.payable_distance_km.toFixed(1)} km
                </td>
                <td className="whitespace-nowrap py-2 pr-4 text-right">
                  {formatVnd(entry.base_amount, intlLocale)}
                </td>
                <td className="whitespace-nowrap py-2 pr-4 text-right">
                  {formatVnd(entry.distance_amount, intlLocale)}
                </td>
                <td className="whitespace-nowrap py-2 pr-4 text-right">
                  {formatVnd(entry.bonus_amount + entry.adjustment_amount, intlLocale)}
                </td>
                <td className="whitespace-nowrap py-2 text-right font-medium text-slate-900">
                  {formatVnd(entry.net_amount, intlLocale)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
