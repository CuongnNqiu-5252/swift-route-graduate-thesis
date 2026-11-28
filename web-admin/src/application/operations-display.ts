export interface OperationsDriverLike {
  id?: string | null;
  user_id?: string | null;
  name?: string | null;
}

export interface OperationsMemberLike {
  user_id?: string | null;
  full_name?: string | null;
  email?: string | null;
}

export interface OperationsCurrentUserLike {
  id?: string | null;
  name?: string | null;
  email?: string | null;
}

export type DisplayTranslator = (text: string) => string;

export interface OperationsPersonDirectory {
  driverNames: Map<string, string>;
  memberFullNames: Map<string, string>;
  memberEmails: Map<string, string>;
  currentUserId: string | null;
  currentUserName: string | null;
  currentUserEmail: string | null;
}

export const INCIDENT_LABELS: Record<string, string> = {
  CUSTOMER_UNREACHABLE: 'Không liên lạc được khách',
  REFUSED_BY_RECIPIENT: 'Khách từ chối nhận hàng',
  WRONG_ADDRESS: 'Sai địa chỉ',
  PACKAGE_DAMAGED: 'Hàng hóa hư hỏng',
  VEHICLE_BREAKDOWN: 'Xe gặp sự cố',
  TRAFFIC_CONGESTION: 'Kẹt xe / Ngập đường',
  WEATHER_DELAY: 'Ảnh hưởng thời tiết xấu',
  DRIVER_EMERGENCY: 'Tài xế gặp tình huống khẩn cấp',
  OFFLINE_SYNC_CONFLICT: 'Xung đột đồng bộ ngoại tuyến',
  OTHER: 'Sự cố khác',
  RETURN_DISCREPANCY_DAMAGED: 'Hàng hoàn bị hư hỏng',
  RETURN_DISCREPANCY_MISSING_ITEMS: 'Hàng hoàn thiếu kiện / sản phẩm',
  RETURN_DISCREPANCY_WRONG_ITEMS: 'Hàng hoàn sai sản phẩm',
  RETURN_DISCREPANCY_REFUSED_BY_RECIPIENT: 'Hàng hoàn rách bao bì / khách từ chối',
  RETURN_DISCREPANCY_OTHER: 'Chênh lệch hàng hoàn khác',
};

export const DISCREPANCY_LABELS: Record<string, string> = {
  DAMAGED: 'Hàng hóa bị vỡ / hư hỏng',
  MISSING_ITEMS: 'Thiếu kiện hàng / thiếu sản phẩm',
  WRONG_ITEMS: 'Sai sản phẩm hoàn về',
  REFUSED_BY_RECIPIENT: 'Bao bì rách nát / khách từ chối',
  OTHER: 'Lý do khác',
};

const RAW_SYSTEM_CODE_PATTERN = /^[A-Z0-9]+(?:_[A-Z0-9]+)*$/;

function normalizeText(value: string | null | undefined): string | null {
  const trimmed = value?.trim();
  return trimmed ? trimmed : null;
}

export function buildOperationsPersonDirectory(
  drivers: readonly OperationsDriverLike[],
  members: readonly OperationsMemberLike[],
  currentUser?: OperationsCurrentUserLike | null,
): OperationsPersonDirectory {
  const driverNames = new Map<string, string>();
  for (const driver of drivers) {
    const name = normalizeText(driver.name);
    if (!name) continue;
    const userId = normalizeText(driver.user_id);
    const driverId = normalizeText(driver.id);
    if (userId) driverNames.set(userId, name);
    if (driverId) driverNames.set(driverId, name);
  }

  const memberFullNames = new Map<string, string>();
  const memberEmails = new Map<string, string>();
  for (const member of members) {
    const userId = normalizeText(member.user_id);
    if (!userId) continue;
    const fullName = normalizeText(member.full_name);
    const email = normalizeText(member.email);
    if (fullName) memberFullNames.set(userId, fullName);
    if (email) memberEmails.set(userId, email);
  }

  return {
    driverNames,
    memberFullNames,
    memberEmails,
    currentUserId: normalizeText(currentUser?.id),
    currentUserName: normalizeText(currentUser?.name),
    currentUserEmail: normalizeText(currentUser?.email),
  };
}

export function resolveDriverDisplayName(
  driverId: string | null | undefined,
  directory: OperationsPersonDirectory,
  t: DisplayTranslator,
  emptyLabel = 'Chưa gán',
): string {
  const id = normalizeText(driverId);
  if (!id) return t(emptyLabel);

  const resolved =
    directory.driverNames.get(id) ??
    directory.memberFullNames.get(id) ??
    (id === directory.currentUserId ? directory.currentUserName : null) ??
    directory.memberEmails.get(id) ??
    (id === directory.currentUserId ? directory.currentUserEmail : null);

  return resolved ?? t('Tài xế chưa rõ tên');
}

export function resolveReporterDisplayName(
  reporterId: string | null | undefined,
  directory: OperationsPersonDirectory,
  t: DisplayTranslator,
  emptyLabel = 'Hệ thống',
): string {
  const id = normalizeText(reporterId);
  if (!id) return t(emptyLabel);

  const resolved =
    directory.memberFullNames.get(id) ??
    directory.driverNames.get(id) ??
    (id === directory.currentUserId ? directory.currentUserName : null) ??
    directory.memberEmails.get(id) ??
    (id === directory.currentUserId ? directory.currentUserEmail : null);

  return resolved ?? t('Chưa rõ tên');
}

export function isRawSystemCode(value: string): boolean {
  return RAW_SYSTEM_CODE_PATTERN.test(value);
}

export function formatIncidentType(
  code: string | null | undefined,
  t: DisplayTranslator,
): string {
  const trimmed = code?.trim();
  if (!trimmed) return t('Sự cố khác');

  const normalized = trimmed.toUpperCase();
  if (Object.hasOwn(INCIDENT_LABELS, normalized)) {
    return t(INCIDENT_LABELS[normalized]);
  }
  if (normalized.startsWith('RETURN_DISCREPANCY_')) {
    return t('Chênh lệch khi nhận hàng hoàn');
  }
  // Never surface unmapped backend enum tokens directly in the UI.
  if (isRawSystemCode(trimmed)) {
    return t('Sự cố khác');
  }
  return t(trimmed);
}

export function formatDiscrepancyType(
  code: string | null | undefined,
  t: DisplayTranslator,
): string {
  const trimmed = code?.trim();
  if (!trimmed) return t('Chênh lệch khác');

  const normalized = trimmed.toUpperCase();
  if (Object.hasOwn(DISCREPANCY_LABELS, normalized)) {
    return t(DISCREPANCY_LABELS[normalized]);
  }
  if (isRawSystemCode(trimmed)) {
    return t('Chênh lệch khác');
  }
  return t(trimmed);
}
