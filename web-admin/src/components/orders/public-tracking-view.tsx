'use client';

import { useEffect, useState, type FormEvent } from 'react';
import { useRouter } from 'next/navigation';
import {
  AlertTriangle,
  Banknote,
  Check,
  Clock,
  ExternalLink,
  MapPin,
  Package,
  Search,
  ShieldCheck,
  Truck,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { OrderStatusBadge } from '@/components/orders/order-status-badge';
import type { PublicOrderTracking } from '@/domain/entities/order.entity';
import { useLocale } from '@/i18n/provider';
import { orderRepository } from '@/infrastructure/repositories/order.repository';
import { cn } from '@/lib/utils';

const STEP_ITEMS = [
  { step: 1, label: 'Tiếp nhận đơn' },
  { step: 2, label: 'Đã điều phối' },
  { step: 3, label: 'Đang giao hàng' },
  { step: 4, label: 'Giao thành công' },
];

const PACKAGE_CATEGORY_LABEL: Record<string, string> = {
  DOCUMENT: 'Tài liệu',
  PARCEL: 'Bưu kiện',
  FRAGILE: 'Hàng dễ vỡ',
  PERISHABLE: 'Hàng dễ hỏng / Thực phẩm',
};

function exceptionBannerKey(status: PublicOrderTracking['status']): {
  tone: string;
  messageKey: string;
} | null {
  switch (status) {
    case 'FAILED':
      return {
        tone: 'border-rose-200 bg-rose-50 text-rose-900',
        messageKey: 'Chuyến giao gần nhất đang gặp gián đoạn. Đơn vị vận chuyển đang kiểm tra và xử lý tiếp.',
      };
    case 'RETURNING':
      return {
        tone: 'border-amber-200 bg-amber-50 text-amber-900',
        messageKey: 'Đơn hàng đang trên đường hoàn trả về kho người gửi.',
      };
    case 'RETURNED':
      return {
        tone: 'border-amber-200 bg-amber-50 text-amber-900',
        messageKey: 'Đơn hàng đã được hoàn trả về người gửi.',
      };
    case 'CANCELLED':
      return {
        tone: 'border-slate-200 bg-slate-100 text-slate-800',
        messageKey: 'Đơn hàng đã được huỷ trước khi giao hoàn tất.',
      };
    default:
      return null;
  }
}

export function PublicTrackingView({ token }: { token: string }) {
  const { t, intlLocale } = useLocale();
  const router = useRouter();
  const [searchInput, setSearchInput] = useState(token);
  const [attempt, setAttempt] = useState(0);
  const [state, setState] = useState<{
    loading: boolean;
    tracking?: PublicOrderTracking;
    error: boolean;
  }>({ loading: true, error: false });

  useEffect(() => {
    const controller = new AbortController();
    orderRepository.getPublicTracking(token, controller.signal).then(
      (tracking) => {
        if (!controller.signal.aborted) {
          setState({ loading: false, tracking, error: false });
        }
      },
      () => {
        if (!controller.signal.aborted) {
          setState({ loading: false, error: true });
        }
      },
    );
    return () => controller.abort();
  }, [token, attempt]);

  function handleLookup(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const cleaned = searchInput.trim();
    if (!cleaned) return;
    router.push(`/track/${encodeURIComponent(cleaned)}`);
  }

  const money = new Intl.NumberFormat(intlLocale, { style: 'currency', currency: 'VND' });
  const dateTime = new Intl.DateTimeFormat(intlLocale, {
    dateStyle: 'short',
    timeStyle: 'short',
    timeZone: 'Asia/Ho_Chi_Minh',
  });

  const tracking = state.tracking;
  const banner = tracking ? exceptionBannerKey(tracking.status) : null;
  const windowLabel = tracking?.time_window_start && tracking?.time_window_end
    ? `${dateTime.format(new Date(tracking.time_window_start))} — ${dateTime.format(new Date(tracking.time_window_end))}`
    : tracking?.time_window_start
      ? dateTime.format(new Date(tracking.time_window_start))
      : tracking?.time_window_end
        ? dateTime.format(new Date(tracking.time_window_end))
        : t('Giao trong ngày theo tuyến');

  const progressTrackPercent =
    tracking && tracking.progress_step > 1
      ? Math.min(100, ((tracking.progress_step - 1) / (STEP_ITEMS.length - 1)) * 100)
      : 0;

  return (
    <div className="min-h-dvh bg-[#f7f9f8] text-slate-900">
      <header className="sticky top-0 z-20 border-b border-slate-200/90 bg-white/95 backdrop-blur-sm">
        <div className="mx-auto flex max-w-3xl flex-col gap-3 px-4 py-3.5 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-center gap-2.5">
            <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-[#17766c] text-white">
              <Truck className="h-5 w-5" aria-hidden="true" />
            </span>
            <div>
              <p className="text-sm font-bold tracking-tight text-slate-950">SwiftRoute</p>
              <p className="text-xs text-slate-500">{t('Tra cứu vận đơn')}</p>
            </div>
          </div>

          <form onSubmit={handleLookup} className="flex w-full gap-2 sm:max-w-xs">
            <Input
              value={searchInput}
              onChange={(event) => setSearchInput(event.target.value)}
              placeholder={t('Nhập mã tra cứu vận đơn…')}
              aria-label={t('Mã tra cứu')}
              className="h-9 bg-slate-50 font-mono text-xs"
            />
            <Button type="submit" size="sm" className="h-9 shrink-0 cursor-pointer bg-[#17766c] text-white hover:bg-[#136058]">
              <Search className="mr-1.5 h-3.5 w-3.5" aria-hidden="true" />
              {t('Tra cứu')}
            </Button>
          </form>
        </div>
      </header>

      <main className="mx-auto max-w-3xl space-y-5 px-4 py-6 sm:py-8">
        {state.loading && (
          <div className="space-y-4 rounded-2xl border border-slate-200 bg-white p-6" role="status">
            <div className="h-7 w-48 animate-pulse rounded-lg bg-slate-200" />
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
              <div className="h-14 animate-pulse rounded-xl bg-slate-100" />
              <div className="h-14 animate-pulse rounded-xl bg-slate-100" />
              <div className="h-14 animate-pulse rounded-xl bg-slate-100" />
              <div className="h-14 animate-pulse rounded-xl bg-slate-100" />
            </div>
            <p className="text-center text-xs text-slate-500">
              {t('Đang tải thông tin vận đơn…')}
            </p>
          </div>
        )}

        {!state.loading && (state.error || !tracking) && (
          <section className="rounded-2xl border border-slate-200 bg-white p-6 text-center sm:p-10">
            <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-rose-50 text-rose-600">
              <AlertTriangle className="h-6 w-6" aria-hidden="true" />
            </div>
            <h1 className="mt-4 text-lg font-semibold text-slate-950">
              {t('Không tìm thấy thông tin vận đơn')}
            </h1>
            <p className="mx-auto mt-2 max-w-md text-sm text-slate-600">
              {t('Liên kết theo dõi không hợp lệ hoặc đơn hàng đã quá thời hạn tra cứu công khai.')}
            </p>
            <p className="mt-1 text-xs text-slate-500">
              {t('Vui lòng kiểm tra lại mã tra cứu từ người gửi hoặc thử tải lại trang.')}
            </p>
            <div className="mt-5">
              <Button
                variant="outline"
                size="sm"
                className="cursor-pointer"
                onClick={() => {
                  setState({ loading: true, error: false });
                  setAttempt((value) => value + 1);
                }}
              >
                {t('Thử lại')}
              </Button>
            </div>
          </section>
        )}

        {!state.loading && tracking && (
          <>
            <section className="rounded-2xl border border-slate-200 bg-white p-5 sm:p-6">
              <div className="flex flex-wrap items-start justify-between gap-3 border-b border-slate-100 pb-4">
                <div className="space-y-1">
                  <p className="text-xs font-medium text-slate-500">
                    {t('Đơn vị vận chuyển')}: <span className="font-semibold text-slate-800">{tracking.carrier_name}</span>
                  </p>
                  <h1 className="text-xl font-bold tracking-tight text-slate-950 sm:text-2xl">
                    {t('Mã đơn')}: <span className="tabular-nums">{tracking.order_code}</span>
                  </h1>
                  <p className="font-mono text-xs text-slate-500">
                    {t('Mã tra cứu')}: {tracking.tracking_token}
                  </p>
                </div>
                <OrderStatusBadge status={tracking.status} />
              </div>

              {banner && (
                <div
                  role="status"
                  className={cn('mt-4 flex items-start gap-2.5 rounded-xl border px-3.5 py-3 text-sm', banner.tone)}
                >
                  <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
                  <p>{t(banner.messageKey)}</p>
                </div>
              )}

              <div className="relative mt-6" aria-label={t('Tiến trình giao hàng')}>
                <div
                  aria-hidden="true"
                  className="pointer-events-none absolute left-[12.5%] right-[12.5%] top-[15px] hidden h-0.5 bg-slate-200 sm:block"
                >
                  <div
                    className="h-full bg-[#17766c] transition-[width] duration-300 ease-out"
                    style={{ width: `${progressTrackPercent}%` }}
                  />
                </div>
                <ol className="grid grid-cols-2 gap-3 sm:grid-cols-4">
                  {STEP_ITEMS.map((item) => {
                    const reached = tracking.progress_step >= item.step;
                    const current = tracking.progress_step === item.step;
                    return (
                      <li
                        key={item.step}
                        className={cn(
                          'relative flex flex-row items-center gap-2.5 rounded-xl border p-3 transition-colors duration-200 sm:flex-col sm:border-0 sm:bg-transparent sm:p-0 sm:text-center',
                          reached
                            ? 'border-[#17766c]/30 bg-[#17766c]/5 text-slate-950'
                            : 'border-slate-200 bg-slate-50/50 text-slate-500',
                        )}
                      >
                        <span
                          aria-hidden="true"
                          className={cn(
                            'relative z-10 flex h-8 w-8 shrink-0 items-center justify-center rounded-full border-2 text-xs font-semibold transition-colors duration-200',
                            reached
                              ? 'border-[#17766c] bg-[#17766c] text-white'
                              : 'border-slate-300 bg-white text-slate-500',
                            current && 'ring-4 ring-[#17766c]/20',
                          )}
                        >
                          {reached ? <Check className="h-4 w-4" /> : item.step}
                        </span>
                        <span className={cn('text-xs leading-tight sm:mt-1', reached ? 'font-semibold text-slate-900' : 'font-medium text-slate-500')}>
                          {t(item.label)}
                        </span>
                      </li>
                    );
                  })}
                </ol>
              </div>

              <div className="mt-5 flex items-center gap-2 rounded-xl border border-slate-200/80 bg-slate-50/80 px-3.5 py-2.5 text-sm text-slate-700">
                <Clock className="h-4 w-4 shrink-0 text-[#17766c]" aria-hidden="true" />
                <span className="font-medium">{t('Khung giờ giao dự kiến')}:</span>
                <span className="tabular-nums font-semibold text-slate-900">{windowLabel}</span>
              </div>
            </section>

            <div className="grid items-stretch gap-5 sm:grid-cols-2">
              <section className="flex flex-col justify-between rounded-2xl border border-slate-200 bg-white p-5">
                <div>
                  <div className="flex items-center gap-2 border-b border-slate-100 pb-3">
                    <MapPin className="h-4 w-4 text-[#17766c]" aria-hidden="true" />
                    <h2 className="text-sm font-semibold text-slate-950">{t('Thông tin giao nhận')}</h2>
                  </div>
                  <dl className="mt-3.5 space-y-3 text-sm">
                    <div>
                      <dt className="text-xs text-slate-500">{t('Người gửi')}</dt>
                      <dd className="mt-0.5 font-medium text-slate-900">{tracking.masked_sender_name}</dd>
                      <dd className="text-xs text-slate-600">{t('Khu vực lấy hàng')}: {tracking.masked_pickup_area}</dd>
                    </div>
                    <div className="border-t border-slate-100 pt-3">
                      <dt className="text-xs text-slate-500">{t('Người nhận')}</dt>
                      <dd className="mt-0.5 font-medium text-slate-900">
                        {tracking.masked_recipient_name} · <span className="tabular-nums">{tracking.masked_recipient_phone}</span>
                      </dd>
                      <dd className="text-xs text-slate-600">{t('Khu vực giao hàng')}: {tracking.masked_delivery_area}</dd>
                    </div>
                  </dl>
                </div>
                <p className="mt-4 flex items-center gap-1.5 border-t border-slate-100 pt-3 text-[11px] text-slate-500">
                  <ShieldCheck className="h-3.5 w-3.5 shrink-0 text-[#17766c]" aria-hidden="true" />
                  <span>{t('Thông tin cá nhân đã được ẩn một phần để bảo mật')}</span>
                </p>
              </section>

              <section className="flex flex-col justify-between rounded-2xl border border-slate-200 bg-white p-5">
                <div>
                  <div className="flex items-center gap-2 border-b border-slate-100 pb-3">
                    <Package className="h-4 w-4 text-[#17766c]" aria-hidden="true" />
                    <h2 className="text-sm font-semibold text-slate-950">{t('Kiện hàng & Thu hộ')}</h2>
                  </div>
                  <dl className="mt-3.5 space-y-3 text-sm">
                    <div className="grid grid-cols-2 gap-3">
                      <div>
                        <dt className="text-xs text-slate-500">{t('Phân loại')}</dt>
                        <dd className="mt-0.5 font-medium text-slate-900">
                          {t(PACKAGE_CATEGORY_LABEL[tracking.package_category] ?? 'Bưu kiện')}
                        </dd>
                      </div>
                      <div>
                        <dt className="text-xs text-slate-500">{t('Khối lượng')}</dt>
                        <dd className="mt-0.5 font-medium tabular-nums text-slate-900">
                          {tracking.weight_kg} kg
                        </dd>
                      </div>
                    </div>
                    {tracking.package_description && (
                      <div>
                        <dt className="text-xs text-slate-500">{t('Mô tả kiện hàng')}</dt>
                        <dd className="mt-0.5 break-words font-medium text-slate-900">
                          {tracking.package_description}
                        </dd>
                      </div>
                    )}
                  </dl>
                </div>
                <div className="mt-4 border-t border-slate-100 pt-3">
                  <p className="flex items-center gap-1.5 text-xs text-slate-500">
                    <Banknote className="h-3.5 w-3.5" aria-hidden="true" />
                    <span>{t('Tiền thu hộ cần chuẩn bị')}</span>
                  </p>
                  <p className="mt-1 text-lg font-bold tabular-nums text-slate-950">
                    {tracking.cod_amount > 0 ? money.format(tracking.cod_amount) : t('Không thu COD')}
                  </p>
                </div>
              </section>
            </div>

            {tracking.latest_proof_url && (
              <section className="rounded-2xl border border-slate-200 bg-white p-5">
                <h2 className="text-sm font-semibold text-slate-950">{t('Ảnh bằng chứng giao hàng (POD)')}</h2>
                <a
                  href={tracking.latest_proof_url}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="group mt-3 inline-block cursor-pointer overflow-hidden rounded-xl border border-slate-200 bg-slate-50 transition-colors hover:border-slate-300"
                >
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img
                    src={tracking.latest_proof_url}
                    alt={t('Ảnh bằng chứng giao hàng (POD)')}
                    className="max-h-60 w-auto object-cover transition-transform duration-200 group-hover:scale-[1.02]"
                  />
                  <span className="inline-flex w-full items-center justify-center gap-1.5 border-t border-slate-200 bg-white px-3 py-2 text-xs font-medium text-[#17766c]">
                    {t('Mở ảnh gốc')}
                    <ExternalLink className="h-3 w-3" aria-hidden="true" />
                  </span>
                </a>
              </section>
            )}

            <section className="rounded-2xl border border-slate-200 bg-white p-5 sm:p-6">
              <h2 className="border-b border-slate-100 pb-3 text-sm font-semibold text-slate-950">
                {t('Hành trình đơn hàng')}
              </h2>
              {tracking.milestones.length === 0 ? (
                <p className="py-4 text-sm text-slate-500">{t('Chưa có cập nhật hành trình nào.')}</p>
              ) : (
                <ol className="mt-4 space-y-4">
                  {tracking.milestones.map((milestone, index) => (
                    <li key={milestone.id} className="relative flex gap-3.5">
                      {index < tracking.milestones.length - 1 && (
                        <span
                          aria-hidden="true"
                          className="absolute left-[9px] top-5 h-[calc(100%+6px)] w-px bg-slate-200"
                        />
                      )}
                      <span
                        aria-hidden="true"
                        className={cn(
                          'relative mt-1 h-[19px] w-[19px] shrink-0 rounded-full border-2',
                          index === 0
                            ? 'border-[#17766c] bg-[#17766c]/20 ring-4 ring-[#17766c]/10'
                            : 'border-slate-300 bg-white',
                        )}
                      />
                      <div className="min-w-0 flex-1 pb-1">
                        <div className="flex flex-wrap items-center justify-between gap-2">
                          <p className={cn('text-sm font-semibold', index === 0 ? 'text-slate-950' : 'text-slate-800')}>
                            {milestone.title}
                          </p>
                          <time
                            dateTime={milestone.occurred_at}
                            className="text-xs tabular-nums text-slate-500"
                          >
                            {dateTime.format(new Date(milestone.occurred_at))}
                          </time>
                        </div>
                        {milestone.description && (
                          <p className="mt-1 text-xs leading-5 text-slate-600">{milestone.description}</p>
                        )}
                        {milestone.proof_url && (
                          <a
                            href={milestone.proof_url}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="mt-2 inline-flex cursor-pointer items-center gap-1 text-xs font-medium text-[#17766c] hover:underline"
                          >
                            {t('Xem ảnh xác nhận')}
                            <ExternalLink className="h-3 w-3" aria-hidden="true" />
                          </a>
                        )}
                      </div>
                    </li>
                  ))}
                </ol>
              )}
            </section>
          </>
        )}
      </main>
    </div>
  );
}
