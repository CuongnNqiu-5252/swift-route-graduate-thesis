export type ReviewDecisionValue = 'UNDER_REVIEW' | 'APPROVED' | 'CHANGES_REQUIRED' | 'REJECTED';

export interface VerificationPickupSnapshot {
  id?: string;
  name?: string;
  address?: string;
  lat?: number;
  lng?: number;
  contact_name?: string;
  contact_phone?: string;
  is_default?: boolean;
}

export interface VerificationSnapshot {
  schema_version?: number;
  business_name?: string;
  business_type?: string;
  contact_phone?: string;
  address?: string;
  country_code?: string;
  delivery_mode?: string;
  delivery_settings?: {
    preparation_enabled?: boolean;
    preparation_minutes?: number;
    delivery_verification_required?: boolean;
    sensitive_handling?: boolean;
    routing_approval_required?: boolean;
    service_priority?: string;
  };
  pickup_points?: VerificationPickupSnapshot[];
}

export const VERIFICATION_STATUS_META: Record<
  string,
  { label: string; dotClass: string; badgeClass: string }
> = {
  SUBMITTED: {
    label: 'Chờ tiếp nhận',
    dotClass: 'bg-slate-600',
    badgeClass: 'bg-slate-100 text-slate-700',
  },
  UNDER_REVIEW: {
    label: 'Đang xem xét',
    dotClass: 'bg-blue-600',
    badgeClass: 'bg-blue-50 text-blue-700',
  },
  CHANGES_REQUIRED: {
    label: 'Cần bổ sung',
    dotClass: 'bg-amber-600',
    badgeClass: 'bg-amber-50 text-amber-800',
  },
  APPROVED: {
    label: 'Đã duyệt',
    dotClass: 'bg-emerald-600',
    badgeClass: 'bg-emerald-50 text-emerald-700',
  },
  REJECTED: {
    label: 'Từ chối',
    dotClass: 'bg-rose-600',
    badgeClass: 'bg-rose-50 text-rose-700',
  },
};

export const VERIFICATION_FILTER_OPTIONS: Array<{ value: string; label: string }> = [
  { value: 'SUBMITTED', label: 'Chờ tiếp nhận' },
  { value: 'UNDER_REVIEW', label: 'Đang xem xét' },
  { value: 'CHANGES_REQUIRED', label: 'Cần bổ sung' },
  { value: 'APPROVED', label: 'Đã duyệt' },
  { value: 'REJECTED', label: 'Từ chối' },
  { value: '', label: 'Tất cả trạng thái' },
];

export const TENANT_LIFECYCLE_LABEL: Record<string, { label: string; badgeClass: string }> = {
  PENDING: { label: 'Chờ kích hoạt', badgeClass: 'bg-slate-100 text-slate-700' },
  APPROVED: { label: 'Đang hoạt động', badgeClass: 'bg-emerald-50 text-emerald-700' },
  SUSPENDED: { label: 'Đang tạm ngưng', badgeClass: 'bg-rose-50 text-rose-700' },
  REJECTED: { label: 'Bị từ chối', badgeClass: 'bg-rose-50 text-rose-700' },
};

export const VERIFICATION_ACTION_LABEL: Record<string, string> = {
  VERIFICATION_SUBMITTED: 'Doanh nghiệp đã gửi hồ sơ',
  VERIFICATION_UNDER_REVIEW: 'Hồ sơ được tiếp nhận xem xét',
  VERIFICATION_APPROVED: 'Hồ sơ đã được phê duyệt',
  VERIFICATION_CHANGES_REQUIRED: 'Yêu cầu bổ sung thông tin',
  VERIFICATION_REJECTED: 'Hồ sơ bị từ chối',
  TENANT_SUSPENDED: 'Tạm ngưng doanh nghiệp',
  TENANT_REACTIVATED: 'Khôi phục hoạt động doanh nghiệp',
};

export const BUSINESS_TYPE_LABEL: Record<string, string> = {
  RESTAURANT: 'Nhà hàng',
  CAFE: 'Cà phê / Trà sữa',
  PHARMACY: 'Nhà thuốc / Y tế',
  RETAIL: 'Bán lẻ',
  ECOMMERCE: 'Thương mại điện tử',
  OTHER: 'Khác',
};

export const DELIVERY_MODE_LABEL: Record<string, string> = {
  OWN_FLEET: 'Đội giao hàng riêng',
  SHARED_POOL: 'Mạng lưới tài xế dùng chung',
  SHARED_NETWORK: 'Mạng lưới tài xế dùng chung',
  HYBRID: 'Kết hợp nội bộ và dùng chung',
};

export const REVIEW_DECISION_OPTIONS: Array<{
  value: ReviewDecisionValue;
  label: string;
  description: string;
}> = [
  {
    value: 'UNDER_REVIEW',
    label: 'Nhận xem xét',
    description: 'Đánh dấu hồ sơ đang được quản trị viên đối chiếu.',
  },
  {
    value: 'APPROVED',
    label: 'Phê duyệt',
    description: 'Chấp thuận hồ sơ để doanh nghiệp mở khóa vận hành.',
  },
  {
    value: 'CHANGES_REQUIRED',
    label: 'Yêu cầu bổ sung',
    description: 'Trả hồ sơ lại doanh nghiệp kèm nội dung cần chỉnh sửa.',
  },
  {
    value: 'REJECTED',
    label: 'Từ chối',
    description: 'Kết thúc yêu cầu xét duyệt kèm lý do chính thức.',
  },
];

export function formatAdminDateTime(value?: string, locale = 'vi-VN'): string {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  return new Intl.DateTimeFormat(locale, {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(date);
}

export function parseVerificationSnapshot(raw?: string): VerificationSnapshot | null {
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw);
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return null;
    return parsed as VerificationSnapshot;
  } catch {
    return null;
  }
}

export function parseTimelineDetails(raw?: string): Record<string, unknown> {
  if (!raw) return {};
  try {
    const parsed = JSON.parse(raw);
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return {};
    return parsed as Record<string, unknown>;
  } catch {
    return {};
  }
}
