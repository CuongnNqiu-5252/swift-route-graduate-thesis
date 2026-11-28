'use client';

import * as SelectPrimitive from '@radix-ui/react-select';
import { Check, ChevronDown } from 'lucide-react';
import { type ReactNode, useState } from 'react';

const EMPTY_VALUE_SENTINEL = '__sr_empty__';

export interface SelectOption {
  value: string;
  label: string;
  description?: string;
  disabled?: boolean;
  lang?: string;
}

export interface CustomSelectProps {
  id?: string;
  name?: string;
  value?: string;
  defaultValue?: string;
  onValueChange?: (value: string) => void;
  options: SelectOption[];
  placeholder?: string;
  disabled?: boolean;
  required?: boolean;
  size?: 'sm' | 'md';
  leadingIcon?: ReactNode;
  className?: string;
  contentClassName?: string;
  'aria-label'?: string;
}

function encodeValue(value: string): string {
  return value === '' ? EMPTY_VALUE_SENTINEL : value;
}

function decodeValue(value: string): string {
  return value === EMPTY_VALUE_SENTINEL ? '' : value;
}

export function CustomSelect({
  id,
  name,
  value,
  defaultValue,
  onValueChange,
  options,
  placeholder,
  disabled,
  required,
  size = 'md',
  leadingIcon,
  className = '',
  contentClassName = '',
  'aria-label': ariaLabel,
}: CustomSelectProps) {
  const isControlled = value !== undefined;
  const [uncontrolledValue, setUncontrolledValue] = useState(defaultValue ?? '');
  const currentValue = isControlled ? value : uncontrolledValue;

  const hasEmptyOption = options.some((option) => option.value === '');
  const radixValue =
    currentValue === '' && !hasEmptyOption ? '' : encodeValue(currentValue);

  const handleChange = (nextRaw: string) => {
    const nextValue = decodeValue(nextRaw);
    if (!isControlled) {
      setUncontrolledValue(nextValue);
    }
    onValueChange?.(nextValue);
  };

  const sizeStyles =
    size === 'sm'
      ? 'h-8 px-2.5 text-xs rounded-md'
      : 'h-10 px-3 text-sm rounded-sm';

  return (
    <>
      {name ? <input type="hidden" name={name} value={currentValue} /> : null}
      <SelectPrimitive.Root
        value={radixValue}
        onValueChange={handleChange}
        disabled={disabled}
        required={required}
      >
        <SelectPrimitive.Trigger
          id={id}
          aria-label={ariaLabel}
          className={`inline-flex w-full items-center justify-between gap-2 border border-slate-300 bg-white font-medium text-slate-900 shadow-2xs transition-colors hover:border-slate-400 hover:bg-slate-50/70 focus-visible:border-blue-600 focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-blue-600 disabled:cursor-not-allowed disabled:bg-slate-100 disabled:text-slate-400 data-[placeholder]:text-slate-500 ${sizeStyles} ${className}`}
        >
          <span className="flex min-w-0 items-center gap-2 truncate">
            {leadingIcon ? <span className="shrink-0 text-slate-400">{leadingIcon}</span> : null}
            <SelectPrimitive.Value placeholder={placeholder} />
          </span>
          <SelectPrimitive.Icon asChild>
            <ChevronDown
              size={size === 'sm' ? 14 : 15}
              className="shrink-0 text-slate-500 transition-transform duration-150"
              aria-hidden="true"
            />
          </SelectPrimitive.Icon>
        </SelectPrimitive.Trigger>

        <SelectPrimitive.Portal>
          <SelectPrimitive.Content
            position="popper"
            sideOffset={5}
            className={`z-[10050] max-h-72 min-w-[var(--radix-select-trigger-width)] max-w-[min(92vw,24rem)] overflow-hidden rounded-xl border border-slate-200 bg-white p-1 text-slate-900 shadow-lg ring-1 ring-slate-950/5 ${contentClassName}`}
          >
            <SelectPrimitive.Viewport className="max-h-68 overflow-y-auto">
              {options.map((option) => (
                <SelectPrimitive.Item
                  key={option.value || EMPTY_VALUE_SENTINEL}
                  value={encodeValue(option.value)}
                  disabled={option.disabled}
                  lang={option.lang}
                  className="relative flex cursor-pointer select-none items-center justify-between gap-2 rounded-sm py-2 pl-3 pr-8 text-sm text-slate-800 outline-none transition-colors data-[disabled]:pointer-events-none data-[highlighted]:bg-slate-100 data-[state=checked]:bg-blue-50/80 data-[state=checked]:font-semibold data-[state=checked]:text-blue-950 data-[disabled]:opacity-45"
                >
                  <div className="min-w-0 flex-1">
                    <SelectPrimitive.ItemText>{option.label}</SelectPrimitive.ItemText>
                    {option.description ? (
                      <p className="truncate text-xs font-normal text-slate-500">{option.description}</p>
                    ) : null}
                  </div>
                  <SelectPrimitive.ItemIndicator className="absolute right-2.5 flex items-center text-blue-600">
                    <Check size={14} strokeWidth={2.5} aria-hidden="true" />
                  </SelectPrimitive.ItemIndicator>
                </SelectPrimitive.Item>
              ))}
            </SelectPrimitive.Viewport>
          </SelectPrimitive.Content>
        </SelectPrimitive.Portal>
      </SelectPrimitive.Root>
    </>
  );
}
