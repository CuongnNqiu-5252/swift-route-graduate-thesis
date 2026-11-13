'use client';

import { useState } from 'react';
import * as Dialog from '@radix-ui/react-dialog';
import {
  AlertTriangle,
  Building2,
  Check,
  CheckCircle2,
  ChevronRight,
  Clock3,
  FileCheck2,
  MapPin,
  PauseCircle,
  Phone,
  PlayCircle,
  ShieldCheck,
  Store,
  Truck,
  UserRoundCheck,
  X,
  XCircle,
} from 'lucide-react';
import {
  BUSINESS_TYPE_LABEL,
  DELIVERY_MODE_LABEL,
  formatAdminDateTime,
  parseTimelineDetails,
  parseVerificationSnapshot,
  REVIEW_DECISION_OPTIONS,
  ReviewDecisionValue,
  TENANT_LIFECYCLE_LABEL,
  VERIFICATION_ACTION_LABEL,
  VERIFICATION_STATUS_META,
} from '@/domain/entities/platform-verification.entity';
import { VerificationDetail } from '@/infrastructure/repositories/tenant-workflow.repository';
import { Button } from '@/components/ui/button';

interface VerificationDetailDialogProps {
  detail: VerificationDetail | null;
  submitting: boolean;
  error: string | null;
  onClose: () => void;
  onSubmitDecision: (decision: ReviewDecisionValue, reason: string) => Promise<boolean>;
  onUpdateLifecycle: (
    tenantId: string,
    nextStatus: 'SUSPENDED' | 'APPROVED',
    reason: string,
  ) => Promise<boolean>;
}

function SettingChecklistRow({
  enabled,
  label,
  detail,
}: {
  enabled: boolean;
  label: string;
  detail: string;
}) {
  return (
    <div className="flex gap-3 py-3 first:pt-0 last:pb-0">
      <span
        className={`mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full ${
          enabled ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-400'
        }`}
      >
        {enabled ? (
          <Check className="h-3.5 w-3.5" aria-hidden="true" />
        ) : (
          <X className="h-3.5 w-3.5" aria-hidden="true" />
        )}
      </span>
      <div>
        <p className="text-sm font-medium text-slate-900">{label}</p>
        <p className="mt-0.5 text-xs leading-5 text-slate-500">{detail}</p>
      </div>
    </div>
  );
}

function DecisionIcon({ value, active }: { value: ReviewDecisionValue; active: boolean }) {
  const colorClass = active
    ? 'text-white'
    : value === 'REJECTED'
      ? 'text-rose-600'
      : value === 'CHANGES_REQUIRED'
        ? 'text-amber-600'
        : value === 'APPROVED'
          ? 'text-emerald-600'
          : 'text-blue-600';

  if (value === 'APPROVED') return <CheckCircle2 className={`h-4 w-4 shrink-0 ${colorClass}`} />;
  if (value === 'CHANGES_REQUIRED') return <AlertTriangle className={`h-4 w-4 shrink-0 ${colorClass}`} />;
  if (value === 'REJECTED') return <XCircle className={`h-4 w-4 shrink-0 ${colorClass}`} />;
  return <Clock3 className={`h-4 w-4 shrink-0 ${colorClass}`} />;
}

function safeText(value: unknown, fallback = 'Chưa cung cấp'): string {
  return typeof value === 'string' && value.trim().length > 0 ? value : fallback;
}

export function VerificationDetailDialog({
  detail,
  submitting,
  error,
  onClose,
  onSubmitDecision,
  onUpdateLifecycle,
}: VerificationDetailDialogProps) {
  return (
    <Dialog.Root open={Boolean(detail)} onOpenChange={(open) => !open && !submitting && onClose()}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-50 bg-slate-950/45 backdrop-blur-[1px]" />
        <Dialog.Content className="fixed inset-y-4 left-1/2 z-50 flex w-[calc(100%-2rem)] max-w-5xl -translate-x-1/2 flex-col overflow-hidden rounded-xl border border-slate-200 bg-white focus:outline-none">
          {detail && (
            <VerificationDetailContent
              key={detail.request.request_id}
              detail={detail}
              submitting={submitting}
              error={error}
              onSubmitDecision={onSubmitDecision}
              onUpdateLifecycle={onUpdateLifecycle}
            />
          )}
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}

function VerificationDetailContent({
  detail,
  submitting,
  error,
  onSubmitDecision,
  onUpdateLifecycle,
}: Omit<VerificationDetailDialogProps, 'detail' | 'onClose'> & {
  detail: VerificationDetail;
}) {
  const [decision, setDecision] = useState<ReviewDecisionValue | null>(null);
  const [decisionReason, setDecisionReason] = useState('');
  const [lifecycleOpen, setLifecycleOpen] = useState(false);
  const [lifecycleReason, setLifecycleReason] = useState('');

  const snapshot = parseVerificationSnapshot(detail.request.snapshot);
  const deliverySettings = snapshot?.delivery_settings ?? {};
  const pickupPoints = Array.isArray(snapshot?.pickup_points) ? snapshot.pickup_points : [];

  const statusMeta =
    VERIFICATION_STATUS_META[detail.request.status] ?? {
      label: detail.request.status,
      dotClass: 'bg-slate-400',
      badgeClass: 'bg-slate-100 text-slate-700',
    };

  const tenantStatusMeta =
    TENANT_LIFECYCLE_LABEL[detail.tenant.status] ?? {
      label: detail.tenant.status,
      badgeClass: 'bg-slate-100 text-slate-700',
    };

  const canReview =
    detail.request.status === 'SUBMITTED' || detail.request.status === 'UNDER_REVIEW';
  const requiresReason = decision === 'REJECTED' || decision === 'CHANGES_REQUIRED';

  const canSuspendOrReactivate =
    detail.tenant.status === 'APPROVED' || detail.tenant.status === 'SUSPENDED';
  const targetLifecycleStatus: 'SUSPENDED' | 'APPROVED' =
    detail.tenant.status === 'APPROVED' ? 'SUSPENDED' : 'APPROVED';

  const decisionCtaLabel =
    decision === 'APPROVED'
      ? 'Phê duyệt doanh nghiệp'
      : decision === 'CHANGES_REQUIRED'
        ? 'Gửi yêu cầu bổ sung'
        : decision === 'REJECTED'
          ? 'Từ chối hồ sơ'
          : 'Nhận xử lý hồ sơ';

  async function handleConfirmDecision() {
    if (!decision) return;
    await onSubmitDecision(decision, decisionReason);
  }

  async function handleConfirmLifecycle() {
    if (!lifecycleReason.trim()) return;
    const ok = await onUpdateLifecycle(
      detail.tenant.tenant_id,
      targetLifecycleStatus,
      lifecycleReason,
    );
    if (ok) {
      setLifecycleOpen(false);
      setLifecycleReason('');
    }
  }

  return (
    <>
              <header className="flex shrink-0 items-start justify-between gap-4 border-b border-slate-200 bg-white px-5 py-4 sm:px-6">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <Dialog.Title className="truncate text-lg font-semibold text-slate-950 sm:text-xl">
                      {detail.tenant.business_name}
                    </Dialog.Title>
                    {statusMeta && (
                      <span
                        className={`inline-flex items-center gap-1.5 rounded-md px-2 py-0.5 text-xs font-medium ${statusMeta.badgeClass}`}
                      >
                        <span className={`h-1.5 w-1.5 rounded-full ${statusMeta.dotClass}`} />
                        {statusMeta.label}
                      </span>
                    )}
                  </div>
                  <Dialog.Description className="num mt-1 text-xs text-slate-500">
                    Gửi lúc {formatAdminDateTime(detail.request.submitted_at)} · Hồ sơ #
                    {detail.request.request_id.slice(0, 8).toUpperCase()} · v{detail.request.version}
                  </Dialog.Description>
                </div>

                <Dialog.Close
                  disabled={submitting}
                  aria-label="Đóng chi tiết hồ sơ"
                  className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-slate-200 text-slate-500 transition-colors hover:bg-slate-50 hover:text-slate-900 disabled:opacity-50"
                >
                  <X className="h-4 w-4" aria-hidden="true" />
                </Dialog.Close>
              </header>

              <div className="min-h-0 flex-1 overflow-y-auto">
                <div className="grid lg:grid-cols-[minmax(0,1fr)_350px]">
                  <div className="min-w-0 space-y-7 px-5 py-6 sm:px-6">
                    {error && (
                      <div
                        role="alert"
                        className="flex gap-3 rounded-lg border border-rose-200 bg-rose-50/70 p-4 text-sm text-rose-800"
                      >
                        <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
                        <div>
                          <p className="font-semibold">Chưa thể thực hiện thao tác</p>
                          <p className="mt-0.5 text-xs leading-5">{error}</p>
                        </div>
                      </div>
                    )}

                    {!snapshot ? (
                      <div className="rounded-lg border border-amber-200 bg-amber-50/70 p-5 text-sm text-amber-900">
                        <p className="font-semibold">Không đọc được snapshot hồ sơ</p>
                        <p className="mt-1 text-xs leading-5">
                          Dữ liệu đính kèm lúc gửi yêu cầu không hợp lệ. Vui lòng kiểm tra dữ liệu trước khi quyết định.
                        </p>
                      </div>
                    ) : (
                      <>
                        <section aria-labelledby="admin-business-info">
                          <div className="flex items-center gap-2">
                            <Building2 className="h-4 w-4 text-[#17766c]" aria-hidden="true" />
                            <h3 id="admin-business-info" className="text-sm font-semibold text-slate-950">
                              Thông tin doanh nghiệp
                            </h3>
                          </div>
                          <dl className="mt-4 grid gap-x-6 gap-y-4 sm:grid-cols-2">
                            <div>
                              <dt className="text-xs text-slate-500">Tên đăng ký</dt>
                              <dd className="mt-1 text-sm font-medium text-slate-950">
                                {safeText(snapshot.business_name)}
                              </dd>
                            </div>
                            <div>
                              <dt className="text-xs text-slate-500">Loại hình kinh doanh</dt>
                              <dd className="mt-1 text-sm font-medium text-slate-950">
                                {BUSINESS_TYPE_LABEL[safeText(snapshot.business_type, '')] ||
                                  safeText(snapshot.business_type)}
                              </dd>
                            </div>
                            <div>
                              <dt className="text-xs text-slate-500">Số điện thoại liên hệ</dt>
                              <dd className="num mt-1 flex items-center gap-1.5 text-sm font-medium text-slate-950">
                                <Phone className="h-3.5 w-3.5 text-slate-400" aria-hidden="true" />
                                {safeText(snapshot.contact_phone)}
                              </dd>
                            </div>
                            <div>
                              <dt className="text-xs text-slate-500">Quốc gia hoạt động</dt>
                              <dd className="mt-1 text-sm font-medium text-slate-950">
                                {snapshot.country_code === 'VN'
                                  ? 'Việt Nam'
                                  : safeText(snapshot.country_code)}
                              </dd>
                            </div>
                            <div className="sm:col-span-2">
                              <dt className="text-xs text-slate-500">Địa chỉ đăng ký</dt>
                              <dd className="mt-1 flex items-start gap-1.5 text-sm font-medium leading-6 text-slate-950">
                                <MapPin className="mt-1 h-3.5 w-3.5 shrink-0 text-slate-400" aria-hidden="true" />
                                {safeText(snapshot.address)}
                              </dd>
                            </div>
                          </dl>
                        </section>

                        <section aria-labelledby="admin-operations-info" className="border-t border-slate-200 pt-6">
                          <div className="flex items-center gap-2">
                            <Truck className="h-4 w-4 text-[#17766c]" aria-hidden="true" />
                            <h3 id="admin-operations-info" className="text-sm font-semibold text-slate-950">
                              Cấu hình vận hành
                            </h3>
                          </div>
                          <div className="mt-4 grid gap-5 md:grid-cols-[minmax(0,0.8fr)_minmax(0,1.2fr)]">
                            <div className="rounded-lg border border-slate-200 bg-slate-50/70 p-4">
                              <p className="text-xs text-slate-500">Phương thức giao hàng</p>
                              <p className="mt-1.5 text-sm font-semibold text-slate-950">
                                {DELIVERY_MODE_LABEL[safeText(snapshot.delivery_mode, '')] ||
                                  safeText(snapshot.delivery_mode)}
                              </p>
                              <p className="mt-2 text-xs text-slate-500">
                                Ưu tiên:{' '}
                                {deliverySettings.service_priority === 'BALANCED'
                                  ? 'Cân bằng thời gian và chi phí'
                                  : safeText(deliverySettings.service_priority)}
                              </p>
                            </div>

                            <div className="divide-y divide-slate-100 rounded-lg border border-slate-200 px-4 py-1">
                              <SettingChecklistRow
                                enabled={deliverySettings.preparation_enabled === true}
                                label="Chuẩn bị hàng trước khi lấy"
                                detail={
                                  deliverySettings.preparation_enabled === true
                                    ? `Dự kiến ${Number(deliverySettings.preparation_minutes) || 0} phút`
                                    : 'Không khai báo thời gian chuẩn bị riêng'
                                }
                              />
                              <SettingChecklistRow
                                enabled={deliverySettings.delivery_verification_required === true}
                                label="Xác nhận giao nhận"
                                detail="Yêu cầu tài xế cung cấp bằng chứng khi hoàn tất đơn"
                              />
                              <SettingChecklistRow
                                enabled={deliverySettings.sensitive_handling === true}
                                label="Hàng hóa nhạy cảm"
                                detail="Áp dụng quy trình xử lý đặc biệt khi vận chuyển"
                              />
                              <SettingChecklistRow
                                enabled={deliverySettings.routing_approval_required === true}
                                label="Duyệt tuyến đường thủ công"
                                detail="Điều phối viên xác nhận lộ trình trước khi phát chuyến"
                              />
                            </div>
                          </div>
                        </section>

                        <section aria-labelledby="admin-pickups-info" className="border-t border-slate-200 pt-6">
                          <div className="flex items-center justify-between gap-2">
                            <div className="flex items-center gap-2">
                              <Store className="h-4 w-4 text-[#17766c]" aria-hidden="true" />
                              <h3 id="admin-pickups-info" className="text-sm font-semibold text-slate-950">
                                Điểm lấy hàng đã khai báo
                              </h3>
                            </div>
                            <span className="num text-xs text-slate-500">{pickupPoints.length} địa điểm</span>
                          </div>

                          {pickupPoints.length === 0 ? (
                            <div className="mt-3 flex gap-2.5 rounded-lg border border-amber-200 bg-amber-50/70 p-4 text-xs text-amber-900">
                              <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
                              <span>
                                Doanh nghiệp chưa có điểm lấy hàng hoạt động trong bản chụp hồ sơ này.
                              </span>
                            </div>
                          ) : (
                            <div className="mt-3 divide-y divide-slate-100 rounded-lg border border-slate-200">
                              {pickupPoints.map((point, index) => (
                                <div key={safeText(point.id, String(index))} className="flex items-start gap-3 p-3.5">
                                  <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-md border border-slate-200 bg-slate-50 text-[#17766c]">
                                    <MapPin className="h-4 w-4" aria-hidden="true" />
                                  </span>
                                  <div className="min-w-0 flex-1">
                                    <div className="flex flex-wrap items-center gap-2">
                                      <p className="text-sm font-medium text-slate-950">
                                        {safeText(point.name, `Điểm lấy hàng ${index + 1}`)}
                                      </p>
                                      {point.is_default === true && (
                                        <span className="rounded bg-slate-100 px-2 py-0.5 text-[11px] font-medium text-slate-700">
                                          Mặc định
                                        </span>
                                      )}
                                    </div>
                                    <p className="mt-1 text-xs leading-5 text-slate-600">
                                      {safeText(point.address)}
                                    </p>
                                    {Boolean(point.contact_name || point.contact_phone) && (
                                      <p className="num mt-1 text-xs text-slate-500">
                                        Liên hệ: {[point.contact_name, point.contact_phone].filter(Boolean).join(' · ')}
                                      </p>
                                    )}
                                  </div>
                                </div>
                              ))}
                            </div>
                          )}
                        </section>

                        <p className="num border-t border-slate-200 pt-4 text-xs text-slate-400">
                          Dữ liệu bảo toàn theo bản chụp gửi duyệt · Phiên bản cấu trúc{' '}
                          {snapshot.schema_version ?? '—'}
                        </p>
                      </>
                    )}
                  </div>

                  <aside className="space-y-6 border-t border-slate-200 bg-slate-50/70 px-5 py-6 sm:px-6 lg:border-l lg:border-t-0">
                    <section aria-labelledby="tenant-lifecycle-heading" className="space-y-3">
                      <div className="flex items-center justify-between gap-2">
                        <h3 id="tenant-lifecycle-heading" className="text-xs font-semibold text-slate-700">
                          Trạng thái doanh nghiệp
                        </h3>
                        {tenantStatusMeta && (
                          <span
                            className={`rounded-md px-2 py-0.5 text-xs font-medium ${tenantStatusMeta.badgeClass}`}
                          >
                            {tenantStatusMeta.label}
                          </span>
                        )}
                      </div>

                      {canSuspendOrReactivate && (
                        <div className="rounded-lg border border-slate-200 bg-white p-3.5 space-y-3">
                          <div className="flex items-center justify-between gap-2">
                            <span className="text-xs text-slate-600">
                              {detail.tenant.status === 'APPROVED'
                                ? 'Có thể tạm ngưng nếu doanh nghiệp vi phạm.'
                                : 'Có thể mở khóa để doanh nghiệp hoạt động lại.'}
                            </span>
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => setLifecycleOpen((prev) => !prev)}
                              disabled={submitting}
                            >
                              {detail.tenant.status === 'APPROVED' ? (
                                <>
                                  <PauseCircle className="h-3.5 w-3.5 text-rose-600" />
                                  Tạm ngưng
                                </>
                              ) : (
                                <>
                                  <PlayCircle className="h-3.5 w-3.5 text-emerald-600" />
                                  Khôi phục
                                </>
                              )}
                            </Button>
                          </div>

                          {lifecycleOpen && (
                            <div className="space-y-2.5 border-t border-slate-100 pt-3">
                              <textarea
                                value={lifecycleReason}
                                onChange={(e) => setLifecycleReason(e.target.value)}
                                maxLength={500}
                                rows={3}
                                placeholder={
                                  targetLifecycleStatus === 'SUSPENDED'
                                    ? 'Nhập lý do tạm ngưng doanh nghiệp…'
                                    : 'Nhập lý do khôi phục hoạt động…'
                                }
                                className="w-full resize-none rounded-lg border border-slate-300 bg-white p-2.5 text-xs text-slate-900 outline-none focus:border-slate-500"
                              />
                              <div className="flex justify-end gap-2">
                                <Button
                                  variant="ghost"
                                  size="sm"
                                  onClick={() => setLifecycleOpen(false)}
                                  disabled={submitting}
                                >
                                  Hủy
                                </Button>
                                <Button
                                  variant={targetLifecycleStatus === 'SUSPENDED' ? 'destructive' : 'default'}
                                  size="sm"
                                  disabled={!lifecycleReason.trim()}
                                  isLoading={submitting}
                                  onClick={() => void handleConfirmLifecycle()}
                                >
                                  Xác nhận
                                </Button>
                              </div>
                            </div>
                          )}
                        </div>
                      )}
                    </section>

                    {canReview ? (
                      <section
                        aria-labelledby="admin-decision-heading"
                        className="border-t border-slate-200 pt-5 space-y-3"
                      >
                        <div className="flex items-center gap-2">
                          <UserRoundCheck className="h-4 w-4 text-slate-600" aria-hidden="true" />
                          <h3 id="admin-decision-heading" className="text-sm font-semibold text-slate-950">
                            Quyết định xét duyệt
                          </h3>
                        </div>

                        <div className="space-y-2">
                          {REVIEW_DECISION_OPTIONS.map((item) => {
                            const selected = decision === item.value;
                            return (
                              <button
                                key={item.value}
                                type="button"
                                aria-pressed={selected}
                                onClick={() => setDecision(item.value)}
                                className={`flex w-full items-center gap-3 rounded-lg border p-3 text-left transition-colors ${
                                  selected
                                    ? 'border-slate-900 bg-slate-900 text-white'
                                    : 'border-slate-200 bg-white text-slate-900 hover:bg-slate-50'
                                }`}
                              >
                                <DecisionIcon value={item.value} active={selected} />
                                <span className="min-w-0 flex-1">
                                  <span className="block text-sm font-medium">{item.label}</span>
                                  <span
                                    className={`mt-0.5 block text-xs leading-4 ${
                                      selected ? 'text-slate-300' : 'text-slate-500'
                                    }`}
                                  >
                                    {item.description}
                                  </span>
                                </span>
                                {selected ? (
                                  <Check className="h-4 w-4 shrink-0" />
                                ) : (
                                  <ChevronRight className="h-4 w-4 shrink-0 text-slate-400" />
                                )}
                              </button>
                            );
                          })}
                        </div>

                        {decision && (
                          <div className="space-y-2 pt-1">
                            <label className="block text-xs font-medium text-slate-700">
                              {requiresReason ? 'Lý do / Nội dung yêu cầu bổ sung *' : 'Ghi chú xét duyệt'}
                              <textarea
                                value={decisionReason}
                                onChange={(e) => setDecisionReason(e.target.value)}
                                maxLength={2000}
                                rows={3}
                                placeholder={
                                  requiresReason
                                    ? 'Nêu rõ lý do hoặc nội dung cần doanh nghiệp cập nhật…'
                                    : 'Ghi chú nội bộ hoặc lời nhắn phản hồi…'
                                }
                                className="mt-1.5 w-full resize-none rounded-lg border border-slate-300 bg-white p-2.5 text-sm text-slate-900 outline-none focus:border-slate-500"
                              />
                            </label>
                            <p className="num text-right text-[11px] text-slate-400">
                              {decisionReason.length}/2000
                            </p>
                            <Button
                              onClick={() => void handleConfirmDecision()}
                              isLoading={submitting}
                              disabled={requiresReason && !decisionReason.trim()}
                              variant={decision === 'REJECTED' ? 'destructive' : 'default'}
                              className="w-full"
                            >
                              {decisionCtaLabel}
                            </Button>
                          </div>
                        )}
                      </section>
                    ) : (
                      <section className="border-t border-slate-200 pt-5">
                        <div className="flex gap-2.5 rounded-lg border border-slate-200 bg-white p-3.5">
                          <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-[#17766c]" />
                          <div>
                            <p className="text-xs font-semibold text-slate-900">Hồ sơ đã có kết quả</p>
                            <p className="mt-0.5 text-xs leading-5 text-slate-500">
                              Trạng thái xét duyệt đã chốt để bảo toàn lịch sử kiểm duyệt.
                            </p>
                          </div>
                        </div>
                      </section>
                    )}

                    <section
                      aria-labelledby="admin-timeline-heading"
                      className="border-t border-slate-200 pt-5 space-y-3"
                    >
                      <div className="flex items-center gap-2">
                        <FileCheck2 className="h-4 w-4 text-slate-600" aria-hidden="true" />
                        <h3 id="admin-timeline-heading" className="text-sm font-semibold text-slate-950">
                          Lịch sử xử lý
                        </h3>
                      </div>

                      {detail.timeline.length === 0 ? (
                        <p className="text-xs text-slate-500">Chưa có bản ghi xét duyệt nào.</p>
                      ) : (
                        <div className="space-y-4">
                          {detail.timeline.map((event, index) => {
                            const details = parseTimelineDetails(event.details);
                            const eventReason =
                              typeof details.reason === 'string' && details.reason.trim()
                                ? details.reason
                                : null;
                            return (
                              <div
                                key={`${event.action}-${event.created_at}-${index}`}
                                className="relative flex gap-3"
                              >
                                {index < detail.timeline.length - 1 && (
                                  <span
                                    aria-hidden="true"
                                    className="absolute left-[7px] top-4 h-[calc(100%+4px)] w-px bg-slate-200"
                                  />
                                )}
                                <span
                                  aria-hidden="true"
                                  className={`relative mt-1 h-3.5 w-3.5 shrink-0 rounded-full border-2 ${
                                    index === 0
                                      ? 'border-[#17766c] bg-[#17766c]/15'
                                      : 'border-slate-300 bg-white'
                                  }`}
                                />
                                <div className="min-w-0">
                                  <p className="text-xs font-medium text-slate-900">
                                    {VERIFICATION_ACTION_LABEL[event.action] ||
                                      event.action.replaceAll('_', ' ')}
                                  </p>
                                  <p className="num mt-0.5 text-[11px] text-slate-500">
                                    {formatAdminDateTime(event.created_at)}
                                  </p>
                                  {eventReason && (
                                    <p className="mt-1.5 rounded-md border border-slate-200 bg-white px-2.5 py-1.5 text-xs leading-5 text-slate-600">
                                      {eventReason}
                                    </p>
                                  )}
                                </div>
                              </div>
                            );
                          })}
                        </div>
                      )}
                    </section>
                  </aside>
                </div>
              </div>
    </>
  );
}
