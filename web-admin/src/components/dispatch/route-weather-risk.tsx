'use client';

import Link from 'next/link';
import { CloudRain } from 'lucide-react';
import { useRouteWeather } from '@/application/use-route-weather';
import { OrderDetailModal } from '@/components/orders/order-detail-modal';
import { useLocale } from '@/i18n/provider';
import { formatVNDate } from '@/lib/utils';
import type { RouteWeatherReport, RouteWeatherRisk } from '@/infrastructure/repositories/route-weather.repository';

export function RouteRainDetails({ risk, onSelect }: { risk: RouteWeatherRisk; onSelect?: () => void }) {
  const { t } = useLocale();
  return (
    <details className="mt-2 text-xs">
      <summary onClick={onSelect} className={`cursor-pointer rounded py-1 font-semibold focus-visible:outline-2 focus-visible:outline-blue-700 ${risk.urgent_stops ? 'text-amber-900' : 'text-blue-800'}`}>
        <CloudRain size={13} className="mr-1 inline" aria-hidden="true" />
        {risk.stops.length} {t('điểm có khả năng gặp mưa lúc đến')}
        {risk.urgent_stops > 0 && <span className="block">{risk.urgent_stops} {t('điểm có mưa và sát giờ hẹn')}</span>}
      </summary>
      <ul className="mt-1 space-y-3 pb-2">
        {risk.stops.map((stop) => (
          <li key={stop.order_id} className="space-y-1 break-words text-slate-700">
            <OrderDetailModal orderId={stop.order_id} orderCode={stop.order_code} shortCode={stop.order_code} />
            <p>{t('Dự kiến đến:')} {formatVNDate(stop.eta)} · {stop.rain_probability}% {t('khả năng mưa')}</p>
            {stop.buffer_minutes !== null && (
              <p className={stop.urgent ? 'font-medium text-amber-900' : ''}>
                {stop.buffer_minutes < 0 ? t('Dự kiến quá giờ hẹn:') : t('Biên giờ hẹn còn:')} {Math.abs(stop.buffer_minutes)} {t('phút')}
              </p>
            )}
          </li>
        ))}
      </ul>
    </details>
  );
}

export function WeatherCoverage({ report }: { report: RouteWeatherReport }) {
  const { t } = useLocale();
  return (
    <p className="text-xs leading-5 text-slate-600">
      {t('Dự báo 24 giờ tới tại ETA đã lưu')} · {report.source} · {t('Cập nhật:')} {formatVNDate(report.as_of)}
      {' · '}{report.evaluated_stops} {t('điểm đã đánh giá')}
      {report.unknown_stops > 0 && <> · {report.unknown_stops} {t('điểm chưa đánh giá được do thiếu ETA, ETA đã qua hoặc thiếu dự báo')}</>}
      {' · '}{t('Mưa từ 40%; cần kiểm tra khi biên giờ hẹn còn ≤15 phút. Không tự điều chỉnh ETA.')}
    </p>
  );
}

export function OperationsWeatherRisks() {
  const { t } = useLocale();
  const weather = useRouteWeather();
  if (!weather.allowed) return null;
  const urgent = weather.data?.routes.filter((route) => route.urgent_stops > 0) ?? [];
  return (
    <section aria-labelledby="operations-weather-title" className="rounded-xl border border-slate-200 bg-white p-4">
      <h2 id="operations-weather-title" className="font-semibold text-slate-950">{t('Mưa và giờ hẹn cần kiểm tra')}</h2>
      {weather.loading ? <p role="status" className="mt-2 text-sm text-slate-600">{t('Đang kiểm tra dự báo theo tuyến…')}</p>
        : weather.error ? <p role="status" className="mt-2 text-sm text-slate-600">{t('Chưa kiểm tra được thời tiết. Không thể kết luận tuyến an toàn.')}</p>
          : weather.data && (
            <div className="mt-2 space-y-3">
              <WeatherCoverage report={weather.data} />
              {urgent.length === 0 ? <p className="text-sm text-slate-700">{t('Chưa ghi nhận tuyến vừa có mưa vừa sát giờ hẹn trong các điểm đã đánh giá.')}</p>
                : <ul className="divide-y divide-slate-100">
                  {urgent.map((risk) => <li key={risk.plan_id} className="py-3 first:pt-0">
                    <Link href={`/dispatch?plan=${encodeURIComponent(risk.plan_id)}`} className="text-sm font-semibold text-blue-800 underline underline-offset-4">
                      {t('Mở tuyến')} #{risk.plan_id.slice(0, 8)}
                    </Link>
                    <RouteRainDetails risk={risk} />
                  </li>)}
                </ul>}
            </div>
          )}
    </section>
  );
}
