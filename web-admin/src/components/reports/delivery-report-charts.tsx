'use client';

import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { useLocale } from '@/i18n/provider';
import type { DeliveryReportDay } from '@/infrastructure/repositories/delivery-report.repository';

export function DeliveryReportCharts({ days }: { days: DeliveryReportDay[] }) {
  const { t, intlLocale } = useLocale();
  const date = new Intl.DateTimeFormat(intlLocale, { day: 'numeric', month: 'numeric', timeZone: 'UTC' });
  const fullDate = new Intl.DateTimeFormat(intlLocale, { dateStyle: 'medium', timeZone: 'UTC' });
  const number = new Intl.NumberFormat(intlLocale);
  const percent = new Intl.NumberFormat(intlLocale, { style: 'percent', maximumFractionDigits: 1 });
  const data = [...days].reverse().map((day) => ({
    ...day,
    punctuality: day.with_deadline ? day.on_time / day.with_deadline : null,
    firstSuccess: day.first_attempts ? day.first_successes / day.first_attempts : null,
  }));
  const charts = [
    { title: t('Kết quả theo ngày'), rates: false, series: [
      { key: 'delivered', name: t('Đã giao'), color: '#1d4ed8' },
      { key: 'failures', name: t('Lượt giao thất bại'), color: '#b45309', dash: '5 3' },
    ] },
    { title: t('Tỷ lệ giao hàng theo ngày'), rates: true, series: [
      { key: 'punctuality', name: t('Tỷ lệ giao đúng hẹn'), color: '#047857' },
      { key: 'firstSuccess', name: t('Thành công lần đầu'), color: '#1d4ed8', dash: '5 3' },
    ] },
  ];

  return (
    <div className="grid min-w-0 gap-4 lg:grid-cols-2">
      {charts.map(({ title, rates, series }) => (
        <figure key={title} className="min-w-0 rounded-xl border border-slate-200 px-3 pb-3 pt-4 sm:px-4">
          <figcaption className="mb-4 text-base font-semibold text-slate-950">{title}</figcaption>
          {rates && !data.some((day) => day.punctuality !== null || day.firstSuccess !== null) ? (
            <p className="text-sm text-slate-600">{t('Chưa có dữ liệu để tính tỷ lệ giao hàng.')}</p>
          ) : (
            <div className="h-64 min-w-0 text-xs">
              <ResponsiveContainer width="100%" height="100%" minWidth={0}>
                <LineChart data={data} title={title} margin={{ top: 8, right: 12, left: 0, bottom: 0 }}>
                  <CartesianGrid vertical={false} stroke="#e2e8f0" />
                  <XAxis dataKey="date" tickFormatter={(value) => date.format(new Date(value))}
                    minTickGap={24} tickLine={false} axisLine={false} tick={{ fill: '#475569' }} />
                  <YAxis domain={rates ? [0, 1] : [0, 'auto']} allowDecimals={rates} width={rates ? 52 : 44}
                    tickFormatter={(value) => (rates ? percent : number).format(value)}
                    tickLine={false} axisLine={false} tick={{ fill: '#475569' }} />
                  <Tooltip labelFormatter={(value) => fullDate.format(new Date(String(value)))}
                    formatter={(value) => value == null ? '—' : (rates ? percent : number).format(Number(value))} />
                  <Legend iconType="plainline" />
                  {series.map(({ key, name, color, dash }) => (
                    <Line key={key} dataKey={key} name={name} type="linear" stroke={color} strokeDasharray={dash}
                      connectNulls={false} strokeWidth={2.25} isAnimationActive={false}
                      dot={rates || data.length <= 14 ? { r: data.length > 30 ? 2 : 3, fill: color, strokeWidth: 0 } : false}
                      activeDot={{ r: 5 }} />
                  ))}
                </LineChart>
              </ResponsiveContainer>
            </div>
          )}
        </figure>
      ))}
    </div>
  );
}
