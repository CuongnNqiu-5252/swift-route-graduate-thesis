'use client';

import {
  VIETQR_BANKS,
  buildDefaultPaymentReference,
  buildPayrollTransferNote,
  buildVietQrUrl,
  formatBankDisplay,
  stripVietnameseDiacritics,
} from '@/application/vietqr';
import { Button } from '@/components/ui/button';
import { Modal } from '@/components/ui/modal';
import { CustomSelect } from '@/components/ui/select';
import { DriverSettlement } from '@/domain/entities/earning.entity';
import { useLocale } from '@/i18n/provider';
import { driverRepository } from '@/infrastructure/repositories/driver.repository';
import { earningRepository } from '@/infrastructure/repositories/earning.repository';
import { AlertCircle, Banknote, Check, Copy, Landmark, Pencil, QrCode } from 'lucide-react';
import { FormEvent, useMemo, useState } from 'react';
import { toast } from 'sonner';
import { formatPeriodDate, formatVnd } from './driver-formatters';

interface SettlementVietQrModalProps {
  settlement: DriverSettlement | null;
  onClose: () => void;
  onPaid: () => Promise<void>;
  onBankAccountUpdated?: () => Promise<void>;
}

type PayoutMethod = 'vietqr' | 'manual';

interface LocalBankInfo {
  bankCode: string;
  accountNumber: string;
  accountName: string;
}

const BANK_ACCOUNT_PATTERN = /^[0-9A-Za-z]{4,40}$/;

/** Modal for paying an approved driver settlement via dynamic VietQR or manual cash reference. */
export function SettlementVietQrModal({
  settlement,
  onClose,
  onPaid,
  onBankAccountUpdated,
}: SettlementVietQrModalProps) {
  if (!settlement) return null;
  return (
    <SettlementVietQrModalContent
      key={settlement.id}
      settlement={settlement}
      onClose={onClose}
      onPaid={onPaid}
      onBankAccountUpdated={onBankAccountUpdated}
    />
  );
}

function SettlementVietQrModalContent({
  settlement,
  onClose,
  onPaid,
  onBankAccountUpdated,
}: {
  settlement: DriverSettlement;
  onClose: () => void;
  onPaid: () => Promise<void>;
  onBankAccountUpdated?: () => Promise<void>;
}) {
  const { t, intlLocale } = useLocale();
  const initialHasBank = Boolean(settlement.driver_bank_code && settlement.driver_bank_account_number);
  const initialHolderName =
    settlement.driver_bank_account_name || stripVietnameseDiacritics(settlement.driver_name);

  const [method, setMethod] = useState<PayoutMethod>('vietqr');
  const [bankInfo, setBankInfo] = useState<LocalBankInfo | null>(() =>
    initialHasBank
      ? {
        bankCode: settlement.driver_bank_code!,
        accountNumber: settlement.driver_bank_account_number!,
        accountName: initialHolderName,
      }
      : null
  );
  const [editingBank, setEditingBank] = useState(!initialHasBank);

  const [bankCode, setBankCode] = useState(settlement.driver_bank_code || 'VCB');
  const [accountNumber, setAccountNumber] = useState(settlement.driver_bank_account_number || '');
  const [accountName, setAccountName] = useState(initialHolderName);
  const [bankError, setBankError] = useState('');
  const [savingBank, setSavingBank] = useState(false);

  const [paymentReference, setPaymentReference] = useState(() =>
    buildDefaultPaymentReference(settlement.id)
  );
  const [manualReference, setManualReference] = useState('');
  const [payError, setPayError] = useState('');
  const [submittingPay, setSubmittingPay] = useState(false);

  const [qrLoading, setQrLoading] = useState(true);
  const [qrFailed, setQrFailed] = useState(false);
  const [copiedKey, setCopiedKey] = useState<string | null>(null);

  const transferNote = useMemo(
    () => buildPayrollTransferNote(settlement.driver_name, settlement.id),
    [settlement.driver_name, settlement.id]
  );

  const qrUrl = useMemo(() => {
    if (!bankInfo) return null;
    try {
      return buildVietQrUrl({
        bankCode: bankInfo.bankCode,
        accountNumber: bankInfo.accountNumber,
        accountName: bankInfo.accountName || settlement.driver_name,
        amount: settlement.total_amount,
        description: transferNote,
      });
    } catch {
      return null;
    }
  }, [settlement.driver_name, settlement.total_amount, bankInfo, transferNote]);

  const bankOptions = useMemo(
    () =>
      VIETQR_BANKS.map((bank) => ({
        value: bank.code,
        label: `${bank.shortName} (${bank.code})`,
      })),
    []
  );

  async function copyValue(key: string, value: string) {
    try {
      await navigator.clipboard.writeText(value);
      setCopiedKey(key);
      toast.success(t('Đã sao chép'));
      window.setTimeout(() => {
        setCopiedKey((current) => (current === key ? null : current));
      }, 1800);
    } catch {
      toast.error(t('Không thể sao chép vào bộ nhớ tạm'));
    }
  }

  async function handleSaveBankAccount(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!settlement) return;
    const cleanNumber = accountNumber.trim().replace(/\s+/g, '');
    if (!BANK_ACCOUNT_PATTERN.test(cleanNumber)) {
      setBankError(t('Số tài khoản cần từ 4 đến 40 ký tự chữ hoặc số.'));
      return;
    }
    setSavingBank(true);
    setBankError('');
    try {
      const updated = await driverRepository.updateBankAccount(settlement.driver_id, {
        bank_code: bankCode,
        bank_account_number: cleanNumber,
        bank_account_name: accountName.trim() || settlement.driver_name,
      });
      setBankInfo({
        bankCode: updated.bank_code || bankCode,
        accountNumber: updated.bank_account_number || cleanNumber,
        accountName:
          updated.bank_account_name || stripVietnameseDiacritics(accountName.trim() || settlement.driver_name),
      });
      setAccountNumber(updated.bank_account_number || cleanNumber);
      setAccountName(updated.bank_account_name || stripVietnameseDiacritics(accountName.trim() || settlement.driver_name));
      setEditingBank(false);
      setQrLoading(true);
      setQrFailed(false);
      toast.success(t('Đã lưu tài khoản ngân hàng tài xế'));
      if (onBankAccountUpdated) {
        void onBankAccountUpdated();
      }
    } catch (err) {
      setBankError(err instanceof Error ? err.message : t('Không thể lưu tài khoản ngân hàng'));
    } finally {
      setSavingBank(false);
    }
  }

  async function handleConfirmPaid(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!settlement) return;
    const rawRef = method === 'vietqr' ? paymentReference : manualReference;
    const reference = rawRef.trim();
    if (!reference) {
      setPayError(t('Vui lòng nhập mã tham chiếu thanh toán.'));
      return;
    }
    setSubmittingPay(true);
    setPayError('');
    try {
      await earningRepository.markPaid(settlement.id, reference);
      toast.success(t('Đã xác nhận chi trả và gửi thông báo cho tài xế'));
      await onPaid();
      onClose();
    } catch (err) {
      setPayError(err instanceof Error ? err.message : t('Không thể xác nhận thanh toán'));
    } finally {
      setSubmittingPay(false);
    }
  }

  const periodLabel = `${formatPeriodDate(settlement.period_start, intlLocale)} – ${formatPeriodDate(settlement.period_end, intlLocale)}`;

  return (
    <Modal
      open
      onOpenChange={(open) => {
        if (!open && !submittingPay && !savingBank) onClose();
      }}
      title={t('Chi trả kỳ đối soát')}
      description={`${settlement.driver_name} · ${periodLabel}`}
      className="max-h-[90vh] max-w-2xl overflow-y-auto"
    >
      <div className="space-y-5">
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-slate-200 bg-slate-50 px-4 py-3">
          <div>
            <p className="text-xs text-slate-500">{t('Tổng số tiền cần chi trả')}</p>
            <p className="mt-0.5 text-xl font-semibold tabular-nums text-slate-950">
              {formatVnd(settlement.total_amount, intlLocale)}
            </p>
          </div>
          <span className="rounded-md bg-blue-50 px-2.5 py-1 text-xs font-medium tabular-nums text-blue-800">
            {settlement.earning_count} {t('khoản thu nhập')}
          </span>
        </div>

        <div className="grid grid-cols-2 gap-2 rounded-sm bg-slate-100 p-1" role="tablist">
          <button
            type="button"
            role="tab"
            aria-selected={method === 'vietqr'}
            onClick={() => {
              setMethod('vietqr');
              setPayError('');
            }}
            className={`inline-flex items-center justify-center gap-2 rounded-md px-3 py-2 text-xs font-medium transition-colors sm:text-sm ${method === 'vietqr'
                ? 'bg-white text-slate-950 shadow-xs'
                : 'text-slate-600 hover:text-slate-900'
              }`}
          >
            <QrCode size={15} />
            <span>{t('Chuyển khoản VietQR')}</span>
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={method === 'manual'}
            onClick={() => {
              setMethod('manual');
              setPayError('');
            }}
            className={`inline-flex items-center justify-center gap-2 rounded-md px-3 py-2 text-xs font-medium transition-colors sm:text-sm ${method === 'manual'
                ? 'bg-white text-slate-950 shadow-xs'
                : 'text-slate-600 hover:text-slate-900'
              }`}
          >
            <Banknote size={15} />
            <span>{t('Tiền mặt / Khác')}</span>
          </button>
        </div>

        {method === 'vietqr' ? (
          editingBank || !bankInfo ? (
            <form onSubmit={(event) => void handleSaveBankAccount(event)} className="space-y-4 rounded-xl border border-slate-200 p-4">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <h3 className="text-sm font-semibold text-slate-950">
                    {bankInfo ? t('Cập nhật tài khoản nhận lương') : t('Tài xế chưa có tài khoản ngân hàng')}
                  </h3>
                  <p className="mt-0.5 text-xs text-slate-500">
                    {t('Nhập thông tin ngân hàng để tạo mã VietQR động điền sẵn số tiền và nội dung.')}
                  </p>
                </div>
                {bankInfo && (
                  <button
                    type="button"
                    onClick={() => {
                      setEditingBank(false);
                      setBankError('');
                    }}
                    className="text-xs font-medium text-slate-500 hover:text-slate-900"
                  >
                    {t('Hủy')}
                  </button>
                )}
              </div>

              <div className="grid gap-3 sm:grid-cols-2">
                <label className="flex flex-col gap-1.5 text-xs font-medium text-slate-700">
                  {t('Ngân hàng')}
                  <CustomSelect value={bankCode} onValueChange={setBankCode} options={bankOptions} />
                </label>
                <label className="flex flex-col gap-1.5 text-xs font-medium text-slate-700">
                  {t('Số tài khoản')}
                  <input
                    type="text"
                    required
                    pattern="^[0-9A-Za-z]{4,40}$"
                    maxLength={40}
                    value={accountNumber}
                    onChange={(event) => {
                      setAccountNumber(event.target.value);
                      if (bankError) setBankError('');
                    }}
                    placeholder={t('VD: 0123456789')}
                    className={`h-10 rounded-sm border bg-white px-3 text-sm tabular-nums text-slate-950 placeholder:text-slate-400 focus:outline-none focus:ring-2 ${bankError
                        ? 'border-red-500 focus:border-red-500 focus:ring-red-500/20'
                        : 'border-slate-300 focus:border-blue-500 focus:ring-blue-500/20'
                      }`}
                  />
                  {bankError && <p role="alert" className="text-xs font-normal text-red-600">{bankError}</p>}
                </label>
                <label className="flex flex-col gap-1.5 text-xs font-medium text-slate-700 sm:col-span-2">
                  {t('Tên chủ tài khoản (không dấu)')}
                  <input
                    type="text"
                    maxLength={120}
                    value={accountName}
                    onChange={(event) => setAccountName(event.target.value)}
                    placeholder={stripVietnameseDiacritics(settlement.driver_name)}
                    className="h-10 rounded-sm border border-slate-300 bg-white px-3 text-sm uppercase text-slate-950 placeholder:text-slate-400 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500/20"
                  />
                </label>
              </div>

              <div className="flex justify-end gap-2">
                <Button type="button" size="sm" variant="outline" onClick={onClose} disabled={savingBank}>
                  {t('Đóng')}
                </Button>
                <Button type="submit" size="sm" isLoading={savingBank}>
                  <QrCode size={14} />
                  {t('Lưu & Tạo mã VietQR')}
                </Button>
              </div>
            </form>
          ) : (
            <div className="space-y-5">
              <div className="grid gap-5 sm:grid-cols-[220px_minmax(0,1fr)]">
                <div className="flex flex-col items-center justify-center rounded-xl border border-slate-200 bg-white p-3">
                  {qrUrl && !qrFailed ? (
                    <div className="relative flex aspect-square w-full max-w-[200px] items-center justify-center">
                      {qrLoading && (
                        <div className="absolute inset-0 animate-pulse rounded-sm bg-slate-100" />
                      )}
                      {/* eslint-disable-next-line @next/next/no-img-element */}
                      <img
                        src={qrUrl}
                        alt={`VietQR ${bankInfo.bankCode} ${bankInfo.accountNumber}`}
                        onLoad={() => setQrLoading(false)}
                        onError={() => {
                          setQrLoading(false);
                          setQrFailed(true);
                        }}
                        className={`h-full w-full rounded-sm object-contain transition-opacity ${qrLoading ? 'opacity-0' : 'opacity-100'
                          }`}
                      />
                    </div>
                  ) : (
                    <div className="flex flex-col items-center gap-2 py-8 text-center text-xs text-slate-500">
                      <AlertCircle size={20} className="text-amber-500" />
                      <p>{t('Không tải được ảnh VietQR. Vui lòng chuyển khoản thủ công theo thông tin bên cạnh.')}</p>
                    </div>
                  )}
                  <p className="mt-2 text-center text-[11px] text-slate-500">
                    {t('Mở app ngân hàng quét mã để tự điền số tiền')}
                  </p>
                </div>

                <div className="flex flex-col justify-between space-y-3 rounded-xl border border-slate-200 bg-slate-50/70 p-4">
                  <div className="flex items-center justify-between gap-2 border-b border-slate-200/80 pb-2.5">
                    <span className="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-800">
                      <Landmark size={14} className="text-blue-600" />
                      {formatBankDisplay(bankInfo.bankCode)}
                    </span>
                    <button
                      type="button"
                      onClick={() => {
                        setBankCode(bankInfo.bankCode);
                        setAccountNumber(bankInfo.accountNumber);
                        setAccountName(bankInfo.accountName);
                        setEditingBank(true);
                      }}
                      className="inline-flex items-center gap-1 text-xs font-medium text-blue-600 hover:text-blue-700"
                    >
                      <Pencil size={12} />
                      {t('Đổi tài khoản')}
                    </button>
                  </div>

                  <dl className="space-y-2.5 text-xs">
                    <CopyRow
                      label={t('Số tài khoản')}
                      value={bankInfo.accountNumber}
                      mono
                      copied={copiedKey === 'account'}
                      onCopy={() => void copyValue('account', bankInfo.accountNumber)}
                    />
                    <div className="flex items-center justify-between gap-2">
                      <dt className="text-slate-500">{t('Chủ tài khoản')}</dt>
                      <dd className="text-right font-medium text-slate-900">
                        {bankInfo.accountName || stripVietnameseDiacritics(settlement.driver_name)}
                      </dd>
                    </div>
                    <CopyRow
                      label={t('Số tiền')}
                      value={formatVnd(settlement.total_amount, intlLocale)}
                      mono
                      copied={copiedKey === 'amount'}
                      onCopy={() => void copyValue('amount', String(Math.round(settlement.total_amount)))}
                    />
                    <CopyRow
                      label={t('Nội dung CK')}
                      value={transferNote}
                      mono
                      copied={copiedKey === 'note'}
                      onCopy={() => void copyValue('note', transferNote)}
                    />
                  </dl>
                </div>
              </div>

              <form onSubmit={(event) => void handleConfirmPaid(event)} className="space-y-3 border-t border-slate-100 pt-4">
                <div>
                  <label htmlFor="vietqr-payment-ref" className="block text-xs font-medium text-slate-700">
                    {t('Mã giao dịch / Tham chiếu ngân hàng')}
                  </label>
                  <input
                    id="vietqr-payment-ref"
                    type="text"
                    maxLength={120}
                    value={paymentReference}
                    onChange={(event) => {
                      setPaymentReference(event.target.value);
                      if (payError) setPayError('');
                    }}
                    placeholder={t('VD: VCB-FT2631800123')}
                    className="mt-1.5 w-full rounded-sm border border-slate-300 bg-white px-3 py-2 font-mono text-sm text-slate-950 placeholder:text-slate-400 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500/20"
                  />
                  {payError && <p className="mt-1 text-xs text-red-600">{payError}</p>}
                </div>

                <div className="flex justify-end gap-2">
                  <Button type="button" variant="outline" onClick={onClose} disabled={submittingPay}>
                    {t('Hủy')}
                  </Button>
                  <Button type="submit" isLoading={submittingPay}>
                    <Check size={15} />
                    {t('Xác nhận đã chuyển khoản')}
                  </Button>
                </div>
              </form>
            </div>
          )
        ) : (
          <form onSubmit={(event) => void handleConfirmPaid(event)} className="space-y-4">
            <div>
              <label htmlFor="manual-payment-ref" className="block text-xs font-medium text-slate-700">
                {t('Mã tham chiếu thanh toán')}
              </label>
              <input
                id="manual-payment-ref"
                type="text"
                autoFocus
                maxLength={120}
                value={manualReference}
                onChange={(event) => {
                  setManualReference(event.target.value);
                  if (payError) setPayError('');
                }}
                placeholder={t('VD: CASH-20261119-01 hoặc VCB-FT2631800123')}
                className="mt-1.5 w-full rounded-sm border border-slate-300 bg-white px-3 py-2 text-sm text-slate-950 placeholder:text-slate-400 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500/20"
              />
              {payError && <p className="mt-1 text-xs text-red-600">{payError}</p>}
            </div>

            <div className="flex justify-end gap-2">
              <Button type="button" variant="outline" onClick={onClose} disabled={submittingPay}>
                {t('Hủy')}
              </Button>
              <Button type="submit" isLoading={submittingPay}>
                {t('Xác nhận thanh toán')}
              </Button>
            </div>
          </form>
        )}
      </div>
    </Modal>
  );
}

function CopyRow({
  label,
  value,
  mono,
  copied,
  onCopy,
}: {
  label: string;
  value: string;
  mono?: boolean;
  copied: boolean;
  onCopy: () => void;
}) {
  const { t } = useLocale();
  return (
    <div className="flex items-start justify-between gap-2">
      <dt className="shrink-0 text-slate-500">{label}</dt>
      <dd className="flex min-w-0 items-center justify-end gap-1.5 text-right">
        <span className={`break-all font-medium text-slate-900 ${mono ? 'font-mono tabular-nums' : ''}`}>
          {value}
        </span>
        <button
          type="button"
          onClick={onCopy}
          aria-label={t('Sao chép')}
          className="shrink-0 rounded p-1 text-slate-400 hover:bg-slate-200/70 hover:text-slate-700"
        >
          {copied ? <Check size={13} className="text-emerald-600" /> : <Copy size={13} />}
        </button>
      </dd>
    </div>
  );
}
