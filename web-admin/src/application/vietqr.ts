export interface VietQrBank {
  code: string;
  shortName: string;
  fullName: string;
}

/** Major Vietnamese banks supported by NAPAS / img.vietqr.io. */
export const VIETQR_BANKS: readonly VietQrBank[] = [
  { code: 'VCB', shortName: 'Vietcombank', fullName: 'Ngân hàng TMCP Ngoại thương Việt Nam' },
  { code: 'MB', shortName: 'MB Bank', fullName: 'Ngân hàng TMCP Quân đội' },
  { code: 'TCB', shortName: 'Techcombank', fullName: 'Ngân hàng TMCP Kỹ thương Việt Nam' },
  { code: 'ACB', shortName: 'ACB', fullName: 'Ngân hàng TMCP Á Châu' },
  { code: 'BIDV', shortName: 'BIDV', fullName: 'Ngân hàng TMCP Đầu tư và Phát triển Việt Nam' },
  { code: 'ICB', shortName: 'VietinBank', fullName: 'Ngân hàng TMCP Công thương Việt Nam' },
  { code: 'TPB', shortName: 'TPBank', fullName: 'Ngân hàng TMCP Tiên Phong' },
  { code: 'VPB', shortName: 'VPBank', fullName: 'Ngân hàng TMCP Việt Nam Thịnh Vượng' },
  { code: 'STB', shortName: 'Sacombank', fullName: 'Ngân hàng TMCP Sài Gòn Thương Tín' },
  { code: 'VBA', shortName: 'Agribank', fullName: 'Ngân hàng Nông nghiệp và Phát triển Nông thôn' },
  { code: 'VIB', shortName: 'VIB', fullName: 'Ngân hàng TMCP Quốc tế Việt Nam' },
  { code: 'HDB', shortName: 'HDBank', fullName: 'Ngân hàng TMCP Phát triển TP. Hồ Chí Minh' },
  { code: 'SHB', shortName: 'SHB', fullName: 'Ngân hàng TMCP Sài Gòn - Hà Nội' },
  { code: 'OCB', shortName: 'OCB', fullName: 'Ngân hàng TMCP Phương Đông' },
  { code: 'MSB', shortName: 'MSB', fullName: 'Ngân hàng TMCP Hàng Hải Việt Nam' },
  { code: 'SEAB', shortName: 'SeABank', fullName: 'Ngân hàng TMCP Đông Nam Á' },
  { code: 'EIB', shortName: 'Eximbank', fullName: 'Ngân hàng TMCP Xuất Nhập khẩu Việt Nam' },
  { code: 'LPB', shortName: 'LPBank', fullName: 'Ngân hàng TMCP Lộc Phát Việt Nam' },
  { code: 'NAB', shortName: 'Nam A Bank', fullName: 'Ngân hàng TMCP Nam Á' },
  { code: 'CAKE', shortName: 'CAKE by VPBank', fullName: 'Ngân hàng số CAKE by VPBank' },
];

export interface BuildVietQrParams {
  bankCode: string;
  accountNumber: string;
  amount: number;
  description?: string;
  accountName?: string;
  template?: 'compact' | 'compact2' | 'qr_only' | 'print';
}

/** Normalizes Vietnamese text into uppercase ASCII suitable for NAPAS bank transfers. */
export function stripVietnameseDiacritics(input: string): string {
  return input
    .trim()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .replace(/[^A-Za-z0-9 ]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .toUpperCase();
}

export function findVietQrBank(bankCode?: string | null): VietQrBank | undefined {
  if (!bankCode) return undefined;
  const normalized = bankCode.trim().toUpperCase();
  return VIETQR_BANKS.find((bank) => bank.code === normalized);
}

export function formatBankDisplay(bankCode?: string | null): string {
  if (!bankCode) return '';
  const bank = findVietQrBank(bankCode);
  return bank ? `${bank.shortName} (${bank.code})` : bankCode.trim().toUpperCase();
}

/** Builds a concise ASCII payroll transfer note (<= 50 chars for universal bank app support). */
export function buildPayrollTransferNote(driverName: string, settlementId: string): string {
  const shortId = settlementId.replace(/[^A-Za-z0-9]/g, '').slice(-6).toUpperCase();
  const normalizedName = stripVietnameseDiacritics(driverName);
  const prefix = 'LUONG SWIFTROUTE';
  const suffix = shortId ? ` ${shortId}` : '';
  const maxNameLen = Math.max(0, 50 - prefix.length - suffix.length - (normalizedName ? 1 : 0));
  const clippedName = normalizedName.slice(0, maxNameLen).trim();
  return `${prefix}${clippedName ? ` ${clippedName}` : ''}${suffix}`.trim();
}

/** Generates a suggested reference code so accountants can confirm payout with one click. */
export function buildDefaultPaymentReference(settlementId: string, now: Date = new Date()): string {
  const yyyy = now.getFullYear();
  const mm = String(now.getMonth() + 1).padStart(2, '0');
  const dd = String(now.getDate()).padStart(2, '0');
  const shortId = settlementId.replace(/[^A-Za-z0-9]/g, '').slice(-6).toUpperCase() || 'PAYOUT';
  return `VIETQR-${yyyy}${mm}${dd}-${shortId}`;
}

/** Builds a dynamic img.vietqr.io QR image URL with pre-filled amount, memo, and account name. */
export function buildVietQrUrl({
  bankCode,
  accountNumber,
  amount,
  description,
  accountName,
  template = 'compact2',
}: BuildVietQrParams): string {
  const cleanBank = bankCode.trim().toUpperCase().replace(/[^A-Z0-9]/g, '');
  const cleanAccount = accountNumber.trim().replace(/[^A-Za-z0-9]/g, '');
  if (!cleanBank || !cleanAccount) {
    throw new Error('Bank code and account number are required to generate VietQR');
  }

  const params = new URLSearchParams();
  const roundedAmount = Math.max(0, Math.round(amount));
  if (roundedAmount > 0) {
    params.set('amount', String(roundedAmount));
  }
  if (description && description.trim()) {
    params.set('addInfo', stripVietnameseDiacritics(description).slice(0, 50));
  }
  if (accountName && accountName.trim()) {
    params.set('accountName', stripVietnameseDiacritics(accountName).slice(0, 100));
  }

  const query = params.toString();
  const base = `https://img.vietqr.io/image/${encodeURIComponent(cleanBank)}-${encodeURIComponent(cleanAccount)}-${template}.png`;
  return query ? `${base}?${query}` : base;
}

export function maskAccountNumber(accountNumber?: string | null): string {
  if (!accountNumber) return '';
  const trimmed = accountNumber.trim();
  if (trimmed.length <= 4) return trimmed;
  return `••••${trimmed.slice(-4)}`;
}
