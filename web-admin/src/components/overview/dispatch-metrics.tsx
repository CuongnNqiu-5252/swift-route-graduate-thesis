import Link from 'next/link';
import { ArrowRight } from 'lucide-react';
import { getAppointmentSlack, getRouteStages, getWarehouseBacklog } from '@/application/overview-metrics';
import type { DispatchPlanRecord } from '@/domain/entities/routing.entity';
import { useLocale } from '@/i18n/provider';
import type { OperationsQueue } from '@/infrastructure/repositories/order.repository';

const panel = 'min-w-0 rounded-xl border border-slate-200 p-4 sm:p-5';
const link = 'rounded-sm text-sm font-medium text-blue-700 hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-700';
const heading = 'text-base font-semibold text-slate-950';
const muted = 'text-sm text-slate-600';
const ageBins = [
  { label: 'Dưới 15 phút', color: 'bg-blue-200' },
  { label: '15–44 phút', color: 'bg-blue-600' },
  { label: 'Từ 45 phút', color: 'bg-amber-700' },
  { label: 'Thiếu giờ tạo', color: 'bg-slate-400' },
];
const stageLabels = ['Chưa gán tài xế', 'Chờ giao tuyến', 'Đã điều phối cho tài xế'];
const planHref = (planId: string) => `/dispatch?plan=${encodeURIComponent(planId)}`;

export function OverviewDispatchMetrics({ queue, queueState, plans, planState, now }: {
  queue: OperationsQueue;
  queueState: 'ready' | 'loading' | 'error';
  plans: DispatchPlanRecord[];
  planState: 'ready' | 'loading' | 'error' | 'restricted';
  now: number;
}) {
  const { t, intlLocale } = useLocale();
  const number = new Intl.NumberFormat(intlLocale);
  const signed = new Intl.NumberFormat(intlLocale, { signDisplay: 'exceptZero' });
  const backlog = getWarehouseBacklog(queue.ready_for_dispatch, now);
  const maxBacklog = Math.max(1, ...backlog.map((group) => group.total));
  const bins = ageBins.filter((_, index) => index < 3 || backlog.some((group) => group.counts[index] > 0));
  const slack = getAppointmentSlack([...queue.ready_for_dispatch, ...queue.active], plans, now);
  const stages = getRouteStages(plans, now);
  const planMessage = planState === 'restricted' ? 'Bạn không có quyền xem tuyến.'
    : planState === 'error' ? 'Không thể tải kế hoạch điều phối.' : 'Đang tải kế hoạch điều phối…';

  return (
    <>
      <section className={panel} aria-labelledby="backlog-heading">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 id="backlog-heading" className={heading}>{t('Đơn chờ theo kho')}</h2>
          <Link href="/orders?status=READY_FOR_DISPATCH" className={link}>{t('Xem đơn chờ')} <ArrowRight className="inline" size={14} aria-hidden="true" /></Link>
        </div>
        <p className={`mt-1 ${muted}`}>{t('Tuổi đơn tính từ lúc tạo.')}</p>
        {queueState !== 'ready' ? (
          <p className={`mt-4 ${muted}`} role={queueState === 'error' ? 'alert' : 'status'}>
            {t(queueState === 'error' ? 'Không thể tải hàng đợi vận hành' : 'Đang tải hàng đợi vận hành…')}
          </p>
        ) : backlog.length === 0 ? (
          <p className={`mt-4 ${muted}`}>{t('Không có đơn chờ điều phối.')}</p>
        ) : (
          <>
            <ul className="mt-4 flex flex-wrap gap-x-3 gap-y-1 text-xs text-slate-600">
              {bins.map(({ label, color }) => (
                <li key={label} className="flex items-center gap-1.5"><span className={`size-2 rounded-sm ${color}`} aria-hidden="true" />{t(label)}</li>
              ))}
            </ul>
            <ul className="mt-2 divide-y divide-slate-100">
              {backlog.slice(0, 5).map(({ key, name, counts, oldest, total }) => (
                <li key={key} className="py-2.5">
                  <div className="flex items-baseline justify-between gap-3 text-sm">
                    <span className="min-w-0 break-words font-medium text-slate-900">{name || t('Chưa xác định kho')}</span>
                    <span className="shrink-0 tabular-nums text-slate-900">{number.format(total)} {t('đơn')}</span>
                  </div>
                  <div className="mt-1.5 flex h-2 overflow-hidden rounded-full bg-slate-100" aria-label={bins.map(({ label }, index) => `${t(label)}: ${number.format(counts[index])}`).join(' · ')}>
                    {counts.map((count, index) => count > 0 && (
                      <span key={ageBins[index].label} title={`${t(ageBins[index].label)}: ${number.format(count)}`}
                        className={ageBins[index].color} style={{ width: `${count / maxBacklog * 100}%` }} />
                    ))}
                  </div>
                  {oldest !== undefined && <p className="mt-1 text-xs tabular-nums text-slate-600">{t('Đơn cũ nhất')} · {number.format(oldest)} {t('phút')}</p>}
                </li>
              ))}
            </ul>
            {backlog.length > 5 && <p className="mt-1 text-xs text-slate-600">{t('Kho khác')} · {number.format(backlog.length - 5)}</p>}
          </>
        )}
      </section>

      <section className={panel} aria-labelledby="slack-heading">
        <h2 id="slack-heading" className={heading}>{t('Đệm giờ hẹn giao')}</h2>
        <p className={`mt-1 ${muted}`}>{t('So hạn giao với ETA kế hoạch hoặc giờ hiện tại nếu ETA đã qua.')}</p>
        {planState !== 'ready' || queueState !== 'ready' ? (
          <p className={`mt-4 ${muted}`} role={planState === 'error' || queueState === 'error' ? 'alert' : 'status'}>
            {t(planState !== 'ready' ? planMessage : queueState === 'error' ? 'Không thể tải hàng đợi vận hành' : 'Đang tải hàng đợi vận hành…')}
          </p>
        ) : (
          <>
            {slack.points.length > 0 && (
              <>
                <p className="mt-3 text-sm font-medium text-slate-800">
                  {t('Dự kiến trễ')} <span className={slack.late ? 'text-amber-800' : ''}>{number.format(slack.late)}</span>
                  {' / '}{number.format(slack.points.length)} {t('đơn có ETA')}
                </p>
                <div className="mt-2 grid grid-cols-[minmax(0,1fr)_minmax(6rem,1.5fr)_4rem] items-center gap-2 text-xs text-slate-600" aria-hidden="true">
                  <span />
                  <div className="flex justify-between tabular-nums"><span>≤−60</span><span>0</span><span>≥+60</span></div>
                  <span className="text-right">{t('phút')}</span>
                </div>
                <ul className="divide-y divide-slate-100">
                  {slack.points.slice(0, 6).map((point) => (
                    <li key={point.id}>
                      <Link href={planHref(point.planId)} className="grid min-h-9 grid-cols-[minmax(0,1fr)_minmax(6rem,1.5fr)_4rem] items-center gap-2 rounded-sm text-sm hover:bg-slate-50 focus-visible:outline-2 focus-visible:outline-blue-700">
                        <span className="truncate font-medium text-blue-700" title={point.label}>{point.label}</span>
                        <span className="relative h-2.5 rounded-full bg-slate-100" aria-hidden="true">
                          <span className="absolute inset-y-0 left-1/2 w-px bg-slate-400" />
                          <span className={`absolute top-1/2 size-2.5 -translate-x-1/2 -translate-y-1/2 rounded-full ${point.minutes < 0 ? 'bg-amber-700' : 'bg-blue-700'}`}
                            style={{ left: `${Math.min(100, Math.max(0, 50 + point.minutes / 1.2))}%` }} />
                        </span>
                        <span aria-label={`${signed.format(point.minutes)} ${t('phút')}`} className={`text-right tabular-nums ${point.minutes < 0 ? 'font-semibold text-amber-800' : 'text-slate-700'}`}>{signed.format(point.minutes)}</span>
                      </Link>
                    </li>
                  ))}
                </ul>
                {slack.points.length > 6 && <p className="mt-1 text-xs text-slate-600">{t('Đang hiển thị 6 đơn còn ít đệm nhất.')}</p>}
              </>
            )}
            {slack.withoutEta > 0 && <p className={`mt-3 ${muted}`}>{t('Đơn có hẹn chưa có ETA')} · {number.format(slack.withoutEta)}</p>}
            {slack.points.length === 0 && slack.withoutEta === 0 && <p className={`mt-4 ${muted}`}>{t('Không có đơn đang xử lý có hẹn giờ.')}</p>}
          </>
        )}
      </section>

      <section className={panel} aria-labelledby="stages-heading">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 id="stages-heading" className={heading}>{t('Tiến độ bàn giao tuyến')}</h2>
          {planState !== 'restricted' && <Link href="/dispatch" className={link}>{t('Mở điều phối')} <ArrowRight className="inline" size={14} aria-hidden="true" /></Link>}
        </div>
        {planState !== 'ready' ? (
          <p className={`mt-4 ${muted}`} role={planState === 'error' ? 'alert' : 'status'}>{t(planMessage)}</p>
        ) : (
          <dl className="mt-3 grid grid-cols-1 gap-px overflow-hidden rounded-lg border border-slate-200 bg-slate-200 sm:grid-cols-3">
            {stages.map(({ count, oldest, planId }, index) => (
              <div key={stageLabels[index]} className="min-w-0 bg-white p-3">
                <dt className="text-xs font-medium text-slate-600">{t(stageLabels[index])}</dt>
                <dd className="mt-1 text-2xl font-semibold tabular-nums text-slate-950">{number.format(count)}</dd>
                {planId && oldest !== undefined && (
                  <dd className="mt-1"><Link href={planHref(planId)} className="rounded-sm text-xs font-medium tabular-nums text-blue-700 hover:underline focus-visible:outline-2 focus-visible:outline-blue-700">
                    {t('Chờ lâu nhất')} · {number.format(oldest)} {t('phút')}
                  </Link></dd>
                )}
              </div>
            ))}
          </dl>
        )}
      </section>
    </>
  );
}
