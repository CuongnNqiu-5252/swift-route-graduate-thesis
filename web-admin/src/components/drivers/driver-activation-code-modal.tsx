'use client';

import { useState } from 'react';
import { Check, Copy, KeyRound, Mail, Send, ShieldAlert } from 'lucide-react';
import { toast } from 'sonner';
import { DriverActivationKeyResult } from '@/domain/entities/driver.entity';
import { driverRepository } from '@/infrastructure/repositories/driver.repository';
import { useLocale } from '@/i18n/provider';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Modal } from '@/components/ui/modal';

interface DriverActivationCodeModalProps {
  items: DriverActivationKeyResult[];
  onClose: () => void;
  onEmailDelivered?: () => void;
}

export function DriverActivationCodeModal({
  items,
  onClose,
  onEmailDelivered,
}: DriverActivationCodeModalProps) {
  const { t, intlLocale } = useLocale();
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const [sendingId, setSendingId] = useState<string | null>(null);
  const [emailInputs, setEmailInputs] = useState<Record<string, string>>({});
  const [sentMap, setSentMap] = useState<Record<string, boolean>>(() => {
    const initial: Record<string, boolean> = {};
    for (const item of items) {
      if (item.email_sent) {
        initial[item.driver_id] = true;
      }
    }
    return initial;
  });

  if (items.length === 0) {
    return null;
  }

  async function handleCopy(driverId: string, formattedCode: string) {
    try {
      await navigator.clipboard.writeText(formattedCode);
      setCopiedId(driverId);
      toast.success(t('Đã sao chép mã kích hoạt vào bộ nhớ tạm'));
      window.setTimeout(() => {
        setCopiedId((current) => (current === driverId ? null : current));
      }, 2000);
    } catch {
      toast.error(t('Không thể sao chép tự động. Vui lòng chọn và sao chép thủ công.'));
    }
  }

  async function handleSendEmail(item: DriverActivationKeyResult) {
    const targetEmail = (emailInputs[item.driver_id] ?? item.email ?? '').trim();
    if (!targetEmail || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(targetEmail)) {
      toast.error(t('Vui lòng nhập địa chỉ email hợp lệ để gửi mã kích hoạt.'));
      return;
    }

    setSendingId(item.driver_id);
    try {
      await driverRepository.sendActivationEmail(item.driver_id, targetEmail);
      setSentMap((prev) => ({ ...prev, [item.driver_id]: true }));
      toast.success(`${t('Đã gửi mã kích hoạt mới tới')} ${targetEmail}`);
      onEmailDelivered?.();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : t('Không thể gửi email mã kích hoạt.'));
    } finally {
      setSendingId(null);
    }
  }

  const isSingle = items.length === 1;

  return (
    <Modal
      open
      onOpenChange={(open) => {
        if (!open && !sendingId) onClose();
      }}
      title={isSingle ? t('Mã kích hoạt ứng dụng tài xế') : `${t('Đã tạo')} ${items.length} ${t('mã kích hoạt')}`}
      description={
        isSingle
          ? items[0].driver_name
          : t('Sao chép hoặc gửi mã kích hoạt 12 chữ số cho từng tài xế bên dưới.')
      }
      className="max-h-[calc(100dvh-2rem)] w-[calc(100%-1.5rem)] max-w-xl overflow-y-auto"
    >
      <div className="space-y-4">
        <div className="flex items-start gap-2.5 rounded-lg border border-amber-200 bg-amber-50 px-3.5 py-2.5 text-xs leading-5 text-amber-900">
          <ShieldAlert size={16} className="mt-0.5 shrink-0 text-amber-700" aria-hidden="true" />
          <p>
            {t(
              'Vì lý do bảo mật, mã gốc chỉ hiển thị một lần tại đây. Sau khi đóng hộp thoại, bạn sẽ cần bấm "Tạo lại mã" nếu muốn cấp mã mới.'
            )}
          </p>
        </div>

        <div className="space-y-3">
          {items.map((item) => {
            const isCopied = copiedId === item.driver_id;
            const isSending = sendingId === item.driver_id;
            const wasSent = Boolean(sentMap[item.driver_id]);
            const effectiveEmail = emailInputs[item.driver_id] ?? item.email ?? '';

            return (
              <div
                key={item.driver_id}
                className="rounded-xl border border-slate-200 bg-slate-50/70 p-4"
              >
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <KeyRound size={16} className="text-blue-700" aria-hidden="true" />
                    <span className="text-sm font-semibold text-slate-950">{item.driver_name}</span>
                  </div>
                  <span className="text-xs tabular-nums text-slate-600">
                    {t('Hết hạn:')}{' '}
                    {item.expires_at ? new Date(item.expires_at).toLocaleString(intlLocale) : '24h'}
                  </span>
                </div>

                <div className="mt-3 flex flex-wrap items-center justify-between gap-3 rounded-lg border border-slate-200 bg-white px-4 py-3">
                  <span className="font-mono text-xl font-bold tracking-wider tabular-nums text-slate-950 sm:text-2xl">
                    {item.formatted_activation_key}
                  </span>
                  <Button
                    type="button"
                    size="sm"
                    variant="outline"
                    className="cursor-pointer"
                    onClick={() => void handleCopy(item.driver_id, item.formatted_activation_key)}
                  >
                    {isCopied ? (
                      <>
                        <Check size={14} className="text-emerald-700" aria-hidden="true" />
                        {t('Đã sao chép')}
                      </>
                    ) : (
                      <>
                        <Copy size={14} aria-hidden="true" />
                        {t('Sao chép mã')}
                      </>
                    )}
                  </Button>
                </div>

                <div className="mt-3 flex flex-col gap-2 sm:flex-row sm:items-center">
                  <div className="relative min-w-0 flex-1">
                    <Mail
                      size={14}
                      aria-hidden="true"
                      className="pointer-events-none absolute start-3 top-1/2 -translate-y-1/2 text-slate-400"
                    />
                    <Input
                      type="email"
                      value={effectiveEmail}
                      disabled={isSending}
                      onChange={(event) =>
                        setEmailInputs((prev) => ({
                          ...prev,
                          [item.driver_id]: event.target.value,
                        }))
                      }
                      placeholder={t('Nhập email tài xế để gửi mã (tùy chọn)')}
                      className="h-9 ps-8 text-sm"
                    />
                  </div>
                  <Button
                    type="button"
                    size="sm"
                    variant="outline"
                    className="cursor-pointer shrink-0"
                    disabled={isSending || !effectiveEmail.trim()}
                    isLoading={isSending}
                    onClick={() => void handleSendEmail(item)}
                  >
                    <Send size={14} aria-hidden="true" />
                    {wasSent ? t('Gửi lại email') : t('Gửi qua email')}
                  </Button>
                </div>
                {wasSent && (
                  <p className="mt-1.5 text-xs font-medium text-emerald-700">
                    {t('Mã kích hoạt đã được đưa vào hàng đợi gửi tới email tài xế.')}
                  </p>
                )}
              </div>
            );
          })}
        </div>

        <div className="rounded-lg border border-slate-200 bg-white p-3.5 text-xs leading-5 text-slate-700">
          <p className="font-semibold text-slate-900">{t('Hướng dẫn kích hoạt trên ứng dụng SwiftRoute Driver:')}</p>
          <ul className="mt-1.5 list-disc space-y-1 ps-4">
            <li>{t('Tài xế mở ứng dụng SwiftRoute Driver trên điện thoại Android hoặc iOS.')}</li>
            <li>{t('Tại màn hình đăng nhập, chọn nút "Kích hoạt bằng mã".')}</li>
            <li>{t('Nhập hoặc dán dãy 12 chữ số ở trên để đăng nhập trực tiếp không cần mật khẩu.')}</li>
          </ul>
        </div>

        <div className="flex justify-end border-t border-slate-200 pt-3">
          <Button type="button" className="cursor-pointer" disabled={Boolean(sendingId)} onClick={onClose}>
            {t('Hoàn tất')}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
