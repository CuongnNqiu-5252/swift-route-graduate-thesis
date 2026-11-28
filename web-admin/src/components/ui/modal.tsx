'use client';

import { useLocale } from '@/i18n/provider';
import { cn } from '@/lib/utils';
import * as Dialog from '@radix-ui/react-dialog';
import { X } from 'lucide-react';

interface ModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description?: string;
  children: React.ReactNode;
  className?: string;
  bodyClassName?: string;
  trigger?: React.ReactElement;
}

export function Modal({ open, onOpenChange, title, description, children, className, bodyClassName, trigger }: ModalProps) {
  const { t } = useLocale();
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      {trigger && <Dialog.Trigger asChild>{trigger}</Dialog.Trigger>}
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-[9999] bg-black/40 backdrop-blur-sm data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0" />
        <Dialog.Content
          {...(!description && { 'aria-describedby': undefined })}
          className={cn(
            'fixed left-1/2 top-1/2 z-[10000] flex max-h-[90dvh] w-[calc(100vw-2rem)] max-w-lg -translate-x-1/2 -translate-y-1/2 flex-col overflow-hidden rounded-xl bg-white shadow-2xl focus:outline-none data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0 data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95',
            className
          )}
        >
          <div className="flex shrink-0 items-center justify-between border-b border-gray-200 px-6 py-4">
            <div>
              <Dialog.Title className="text-lg font-semibold text-gray-900">{title}</Dialog.Title>
              {description && (
                <Dialog.Description className="mt-1 text-sm text-gray-500">
                  {description}
                </Dialog.Description>
              )}
            </div>
            <Dialog.Close aria-label={t('Đóng')} className="cursor-pointer rounded-sm p-1 text-gray-500 transition-colors hover:bg-gray-100 hover:text-gray-700">
              <X size={18} />
            </Dialog.Close>
          </div>
          <div className={cn('min-h-0 flex-1 overflow-y-auto overscroll-contain p-6', bodyClassName)}>{children}</div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}

