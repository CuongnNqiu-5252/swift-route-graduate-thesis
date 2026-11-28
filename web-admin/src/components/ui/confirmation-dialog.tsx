'use client';

import { useLocale } from '@/i18n/provider';
import * as Dialog from '@radix-ui/react-dialog';
import { useId, useRef, useState } from 'react';
import { Button } from './button';

interface ConfirmationDialogProps {
  title: string;
  description: string;
  confirmLabel: string;
  destructive?: boolean;
  inputLabel?: string;
  defaultValue?: string;
  onCancel: () => void;
  onConfirm: (value: string) => void | boolean | Promise<void | boolean>;
}

/** Mount for one decision; unmount on cancel or successful completion. */
export function ConfirmationDialog({
  title, description, confirmLabel, destructive = false,
  inputLabel, defaultValue = '', onCancel, onConfirm,
}: ConfirmationDialogProps) {
  const { t } = useLocale();
  const inputId = useId();
  const [value, setValue] = useState(defaultValue);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const busy = useRef(false);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const cancelRef = useRef<HTMLButtonElement>(null);
  const returnFocus = useRef(typeof document === 'undefined' ? null : document.activeElement);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (busy.current || (inputLabel && !value.trim())) return;
    busy.current = true;
    setSubmitting(true);
    setError('');
    try {
      if (await onConfirm(value.trim()) !== false) onCancel();
    } catch {
      setError(t('Không thể thực hiện thao tác. Vui lòng thử lại.'));
    } finally {
      busy.current = false;
      setSubmitting(false);
    }
  };

  return (
    <Dialog.Root open onOpenChange={(open) => { if (!open && !busy.current) onCancel(); }}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-[100] bg-slate-950/45" />
        <Dialog.Content
          className="fixed left-1/2 top-1/2 z-[100] max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-md -translate-x-1/2 -translate-y-1/2 overflow-y-auto rounded-xl border border-slate-200 bg-white p-6 shadow-xl focus:outline-none"
          onOpenAutoFocus={(event) => {
            event.preventDefault();
            (inputRef.current ?? cancelRef.current)?.focus();
          }}
          onCloseAutoFocus={(event) => {
            event.preventDefault();
            if (returnFocus.current instanceof HTMLElement && returnFocus.current.isConnected) {
              returnFocus.current.focus();
            }
          }}
          onPointerDownOutside={(event) => event.preventDefault()}
          onEscapeKeyDown={(event) => { if (busy.current) event.preventDefault(); }}
          aria-busy={submitting}
        >
          <form onSubmit={submit}>
            <Dialog.Title className="text-lg font-semibold leading-7 text-slate-950">{title}</Dialog.Title>
            <Dialog.Description className="mt-2 break-words text-sm leading-6 text-slate-600">
              {description}
            </Dialog.Description>
            {inputLabel && (
              <div className="mt-5">
                <label htmlFor={inputId} className="mb-2 block text-sm font-medium text-slate-900">{inputLabel}</label>
                <textarea
                  ref={inputRef}
                  id={inputId}
                  required
                  rows={3}
                  value={value}
                  disabled={submitting}
                  onChange={(event) => setValue(event.target.value)}
                  className="block w-full resize-y rounded-sm border border-slate-300 px-3 py-2 text-sm leading-6 text-slate-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 disabled:opacity-50"
                />
              </div>
            )}
            {error && <p role="alert" className="mt-4 text-sm leading-6 text-red-700">{error}</p>}
            <div className="mt-6 flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
              <Button ref={cancelRef} type="button" variant="outline" disabled={submitting} onClick={onCancel}>
                {t('Đóng')}
              </Button>
              <Button type="submit" variant={destructive ? 'destructive' : 'default'} isLoading={submitting} disabled={Boolean(inputLabel && !value.trim())}>
                {confirmLabel}
              </Button>
            </div>
          </form>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
