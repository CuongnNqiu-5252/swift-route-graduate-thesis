'use client';

import * as DropdownMenuPrimitive from '@radix-ui/react-dropdown-menu';
import { type ComponentPropsWithoutRef } from 'react';

export const DropdownMenu = DropdownMenuPrimitive.Root;
export const DropdownMenuTrigger = DropdownMenuPrimitive.Trigger;

export function DropdownMenuContent({
  className = '',
  sideOffset = 6,
  align = 'start',
  ...props
}: ComponentPropsWithoutRef<typeof DropdownMenuPrimitive.Content>) {
  return (
    <DropdownMenuPrimitive.Portal>
      <DropdownMenuPrimitive.Content
        sideOffset={sideOffset}
        align={align}
        className={`z-50 max-h-72 min-w-56 max-w-[min(92vw,22rem)] overflow-y-auto rounded-xl border border-slate-200 bg-white p-1.5 text-slate-900 shadow-xl ring-1 ring-slate-950/5 ${className}`}
        {...props}
      />
    </DropdownMenuPrimitive.Portal>
  );
}

export function DropdownMenuLabel({
  className = '',
  ...props
}: ComponentPropsWithoutRef<typeof DropdownMenuPrimitive.Label>) {
  return (
    <DropdownMenuPrimitive.Label
      className={`px-2.5 py-1.5 text-[11px] font-semibold uppercase tracking-wide text-slate-500 ${className}`}
      {...props}
    />
  );
}

export function DropdownMenuSeparator({
  className = '',
  ...props
}: ComponentPropsWithoutRef<typeof DropdownMenuPrimitive.Separator>) {
  return (
    <DropdownMenuPrimitive.Separator
      className={`my-1 h-px bg-slate-100 ${className}`}
      {...props}
    />
  );
}

export function DropdownMenuItem({
  className = '',
  ...props
}: ComponentPropsWithoutRef<typeof DropdownMenuPrimitive.Item>) {
  return (
    <DropdownMenuPrimitive.Item
      className={`flex cursor-pointer select-none items-center gap-2.5 rounded-sm px-2.5 py-2 text-sm text-slate-800 outline-none transition-colors data-[disabled]:pointer-events-none data-[highlighted]:bg-slate-100 data-[highlighted]:text-slate-950 data-[disabled]:opacity-45 ${className}`}
      {...props}
    />
  );
}
