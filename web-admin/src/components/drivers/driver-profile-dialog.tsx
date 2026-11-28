'use client';

import * as Dialog from '@radix-ui/react-dialog';
import { X } from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { Button } from '@/components/ui/button';
import { useLocale } from '@/i18n/provider';

type ProfileSection = 'profile' | 'vehicle' | 'review' | 'bank';

interface DriverProfileDialogProps {
  name: string;
  initialSection: ProfileSection;
  profile: ReactNode;
  vehicle: ReactNode;
  review?: ReactNode;
  bank: ReactNode;
  profileAction?: ReactNode;
  bankAction?: ReactNode;
  busy: boolean;
  onClose: () => void;
}

export function DriverProfileDialog({
  name, initialSection, profile, vehicle, review, bank, profileAction, bankAction, busy, onClose,
}: DriverProfileDialogProps) {
  const { t } = useLocale();
  const [requested, setRequested] = useState(initialSection);
  const [returnFocus] = useState(() => typeof document === 'undefined' ? null : document.activeElement);
  const sections: Array<{ id: ProfileSection; label: string; content: ReactNode }> = [
    { id: 'profile', label: t('Thông tin hồ sơ'), content: profile },
    { id: 'vehicle', label: t('Phương tiện & ca làm'), content: vehicle },
    ...(review ? [{ id: 'review' as const, label: t('Xét duyệt'), content: review }] : []),
    { id: 'bank', label: t('Tài khoản nhận lương'), content: bank },
  ];
  const active = sections.find(({ id }) => id === requested) ?? sections[0];

  return (
    <Dialog.Root open onOpenChange={(open) => { if (!open && !busy) onClose(); }}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-[9999] bg-slate-950/45" />
        <Dialog.Content
          className="fixed left-1/2 top-1/2 z-[10000] flex max-h-[calc(100dvh-1.5rem)] w-[calc(100%-1.5rem)] max-w-3xl -translate-x-1/2 -translate-y-1/2 flex-col overflow-hidden rounded-xl border border-slate-200 bg-white shadow-xl focus:outline-none sm:max-h-[calc(100dvh-4rem)]"
          onCloseAutoFocus={(event) => {
            if (!(returnFocus instanceof HTMLElement) || !returnFocus.isConnected) return;
            event.preventDefault();
            returnFocus.focus();
          }}
          onPointerDownOutside={(event) => event.preventDefault()}
          onEscapeKeyDown={(event) => { if (busy) event.preventDefault(); }}
          aria-busy={busy}
        >
          <header className="flex items-start justify-between gap-3 border-b border-slate-200 px-4 py-3 sm:px-6 sm:py-4">
            <div className="min-w-0">
              <Dialog.Title className="text-base font-semibold text-slate-950 sm:text-lg">{t('Hồ sơ tài xế')}</Dialog.Title>
              <Dialog.Description className="mt-0.5 break-words text-sm text-slate-600">{name}</Dialog.Description>
            </div>
            <Dialog.Close disabled={busy} aria-label={t('Đóng')} className="grid size-9 shrink-0 place-items-center rounded-md text-slate-600 hover:bg-slate-100 focus-visible:outline-2 focus-visible:outline-blue-600 disabled:opacity-50">
              <X aria-hidden="true" size={17} />
            </Dialog.Close>
          </header>
          <div className="flex min-h-0 flex-1 flex-col sm:grid sm:min-h-72 sm:grid-cols-[11rem_minmax(0,1fr)]">
            <nav aria-label={t('Mục hồ sơ tài xế')} className="flex shrink-0 gap-1 overflow-x-auto border-b border-slate-200 bg-slate-50 p-2 sm:flex-col sm:overflow-y-auto sm:border-b-0 sm:border-e sm:p-3">
              {sections.map((section) => (
                <button key={section.id} type="button" aria-current={active.id === section.id ? 'true' : undefined} disabled={busy}
                  onClick={() => setRequested(section.id)}
                  className="min-h-10 shrink-0 rounded-md px-2.5 text-start text-sm font-medium text-slate-700 hover:bg-slate-100 focus-visible:outline-2 focus-visible:outline-blue-600 aria-current:bg-slate-200/80 aria-current:font-semibold aria-current:text-slate-950 disabled:opacity-60 sm:shrink">
                  {section.label}
                </button>
              ))}
            </nav>
            <section aria-label={active.label} className="min-w-0 overflow-y-auto px-4 py-4 sm:px-6 sm:py-5">
              <h3 className="mb-3 text-sm font-semibold text-slate-950">{active.label}</h3>
              {active.content}
            </section>
          </div>
          <footer className="flex shrink-0 flex-wrap justify-end gap-2 border-t border-slate-200 px-4 py-3 sm:px-6">
            <Dialog.Close asChild><Button type="button" variant="outline" size="sm" disabled={busy}>{t('Đóng')}</Button></Dialog.Close>
            {(active.id === 'profile' || active.id === 'vehicle') && profileAction}
            {active.id === 'bank' && bankAction}
          </footer>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
