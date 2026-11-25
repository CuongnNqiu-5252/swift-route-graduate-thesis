'use client';

import {
  ChangeEvent,
  FocusEvent,
  FormEvent,
  InputHTMLAttributes,
  forwardRef,
  useEffect,
  useState,
} from 'react';
import { cn } from '@/lib/utils';

type ValidatableElement = HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement;

function isValidatableControl(node: EventTarget | null): node is ValidatableElement {
  if (!node || !(node instanceof HTMLElement)) return false;
  if (node instanceof HTMLInputElement) {
    return !['hidden', 'submit', 'button', 'reset', 'checkbox', 'radio', 'file'].includes(node.type);
  }
  return node instanceof HTMLTextAreaElement || node instanceof HTMLSelectElement;
}

function cleanLabelText(raw?: string | null): string {
  if (!raw) return '';
  return raw
    .replace(/\s+/g, ' ')
    .replace(/[*:]+\s*$/, '')
    .trim();
}

export function resolveFieldLabel(el: ValidatableElement, labelOverride?: string): string {
  if (labelOverride) {
    const cleaned = cleanLabelText(labelOverride);
    if (cleaned) return cleaned;
  }
  const ariaLabel = cleanLabelText(el.getAttribute('aria-label'));
  if (ariaLabel) return ariaLabel;

  if (el.id && typeof document !== 'undefined') {
    const explicit = document.querySelector(`label[for="${CSS.escape(el.id)}"]`);
    if (explicit) {
      const text = cleanLabelText(explicit.textContent);
      if (text) return text;
    }
  }

  const parentLabel = el.closest('label');
  if (parentLabel) {
    const clone = parentLabel.cloneNode(true) as HTMLElement;
    clone
      .querySelectorAll('input, textarea, select, button, [data-sr-field-error], [role="alert"]')
      .forEach((child) => child.remove());
    const text = cleanLabelText(clone.textContent);
    if (text) return text;
  }

  const prev = el.previousElementSibling;
  if (prev && prev.tagName === 'LABEL') {
    const text = cleanLabelText(prev.textContent);
    if (text) return text;
  }

  const placeholder = cleanLabelText(
    el.getAttribute('placeholder')?.replace(/^(VD|Ví dụ|e\.g\.)\s*:\s*/i, '')
  );
  if (placeholder && placeholder.length <= 45) return placeholder;

  return '';
}

export function getFieldValidationMessage(el: ValidatableElement, labelOverride?: string): string {
  const isEn = typeof document !== 'undefined' && document.documentElement.lang === 'en';
  const label = resolveFieldLabel(el, labelOverride);
  const lowerLabel = label ? label.charAt(0).toLowerCase() + label.slice(1) : isEn ? 'this field' : 'trường này';
  const capLabel = label || (isEn ? 'This field' : 'Trường này');
  const value = el.value ?? '';

  if (el.required && typeof value === 'string' && value.length > 0 && value.trim().length === 0) {
    return isEn
      ? `Please enter a valid ${lowerLabel} (cannot be only spaces).`
      : `Vui lòng nhập ${lowerLabel} hợp lệ (không chỉ nhập khoảng trắng).`;
  }

  const validity = el.validity;
  if (!validity || validity.valid) return '';

  if (validity.customError && el.validationMessage) {
    return el.validationMessage;
  }

  if (validity.valueMissing) {
    if (el instanceof HTMLSelectElement) {
      return isEn ? `Please select ${lowerLabel}.` : `Vui lòng chọn ${lowerLabel}.`;
    }
    return isEn ? `Please enter ${lowerLabel}.` : `Vui lòng nhập ${lowerLabel}.`;
  }

  if (validity.typeMismatch) {
    const type = el instanceof HTMLInputElement ? el.type : '';
    if (type === 'email') {
      return isEn
        ? 'Please enter a valid email address (e.g. name@company.vn).'
        : 'Email chưa đúng định dạng (ví dụ: ten@doanhnghiep.vn).';
    }
    if (type === 'url') {
      return isEn
        ? 'Please enter a valid URL starting with http:// or https://.'
        : 'Đường dẫn URL chưa đúng định dạng (bắt đầu bằng http:// hoặc https://).';
    }
    if (type === 'tel') {
      return isEn
        ? 'Please enter a valid phone number.'
        : 'Số điện thoại chưa đúng định dạng.';
    }
    return isEn ? `${capLabel} format is invalid.` : `${capLabel} chưa đúng định dạng.`;
  }

  if (validity.tooShort && (el instanceof HTMLInputElement || el instanceof HTMLTextAreaElement)) {
    return isEn
      ? `${capLabel} must be at least ${el.minLength} characters (currently ${value.length}).`
      : `${capLabel} cần tối thiểu ${el.minLength} ký tự (hiện tại ${value.length} ký tự).`;
  }

  if (validity.tooLong && (el instanceof HTMLInputElement || el instanceof HTMLTextAreaElement)) {
    return isEn
      ? `${capLabel} must not exceed ${el.maxLength} characters.`
      : `${capLabel} không được vượt quá ${el.maxLength} ký tự.`;
  }

  if (validity.patternMismatch && el instanceof HTMLInputElement) {
    const customMsg = el.dataset.patternMessage || cleanLabelText(el.getAttribute('title'));
    if (customMsg) return customMsg;
    const pattern = el.getAttribute('pattern') || '';
    const hint = `${el.name} ${label}`.toLowerCase();
    if (pattern.includes('9,15') || hint.includes('phone') || hint.includes('điện thoại')) {
      return isEn
        ? 'Phone number must contain 9–15 digits (e.g. 0901234567).'
        : 'Số điện thoại chưa đúng định dạng (cần 9–15 chữ số, ví dụ: 0901234567).';
    }
    if (pattern.includes('4,40') || hint.includes('tài khoản') || hint.includes('stk')) {
      return isEn
        ? 'Account number must be 4–40 letters or digits without spaces.'
        : 'Số tài khoản cần từ 4 đến 40 ký tự chữ hoặc số, không chứa ký tự đặc biệt.';
    }
    return isEn
      ? `${capLabel} does not match the required format.`
      : `${capLabel} chưa đúng định dạng yêu cầu.`;
  }

  if (validity.rangeUnderflow && el instanceof HTMLInputElement) {
    const min = el.getAttribute('min');
    return isEn
      ? `${capLabel} must be greater than or equal to ${min}.`
      : `${capLabel} cần lớn hơn hoặc bằng ${min}.`;
  }

  if (validity.rangeOverflow && el instanceof HTMLInputElement) {
    const max = el.getAttribute('max');
    return isEn
      ? `${capLabel} must be less than or equal to ${max}.`
      : `${capLabel} không được vượt quá ${max}.`;
  }

  if (validity.stepMismatch || validity.badInput) {
    return isEn
      ? `Please enter a valid number for ${lowerLabel}.`
      : `Vui lòng nhập giá trị số hợp lệ cho ${lowerLabel}.`;
  }

  return isEn ? `${capLabel} is invalid.` : `${capLabel} chưa hợp lệ.`;
}

function findErrorAnchor(el: ValidatableElement): HTMLElement {
  const parent = el.parentElement;
  if (
    parent &&
    parent.tagName !== 'LABEL' &&
    parent.tagName !== 'FORM' &&
    (parent.classList.contains('relative') || parent.classList.contains('flex-row'))
  ) {
    return parent;
  }
  return el;
}

function getOrCreateDomErrorNode(el: ValidatableElement, create: boolean): HTMLParagraphElement | null {
  const anchor = findErrorAnchor(el);
  const next = anchor.nextElementSibling;
  if (next instanceof HTMLParagraphElement && next.dataset.srFieldError === 'true') {
    return next;
  }
  if (!create) return null;
  const p = document.createElement('p');
  p.dataset.srFieldError = 'true';
  p.setAttribute('role', 'alert');
  p.className = 'mt-1 text-xs font-normal text-red-600';
  anchor.insertAdjacentElement('afterend', p);
  return p;
}

function setDomFieldError(el: ValidatableElement, message: string) {
  if (el.dataset.srInput === 'true') return;
  if (!message) {
    el.classList.remove('border-red-500');
    el.removeAttribute('aria-invalid');
    const existing = getOrCreateDomErrorNode(el, false);
    existing?.remove();
    return;
  }
  el.classList.add('border-red-500');
  el.setAttribute('aria-invalid', 'true');
  const node = getOrCreateDomErrorNode(el, true);
  if (node) {
    node.textContent = message;
  }
}

/**
 * Globally intercepts native browser HTML5 validation popups and renders
 * localized inline warning text beneath raw <input>, <textarea>, and <select> elements.
 */
export function useGlobalFormValidation() {
  useEffect(() => {
    const handleInvalid = (event: Event) => {
      const target = event.target;
      if (!isValidatableControl(target)) return;
      // Suppress browser's default tooltip balloon
      event.preventDefault();
      if (target.dataset.srInput === 'true') return;
      const msg = getFieldValidationMessage(target);
      setDomFieldError(target, msg);

      const form = target.form;
      if (form) {
        const firstInvalid = Array.from(form.elements).find(
          (item): item is ValidatableElement => isValidatableControl(item) && !item.validity.valid
        );
        if (firstInvalid === target) {
          target.focus({ preventScroll: false });
        }
      }
    };

    const handleInputOrChange = (event: Event) => {
      const target = event.target;
      if (!isValidatableControl(target)) return;
      if (target.dataset.srWhitespaceError === 'true') {
        target.setCustomValidity('');
        delete target.dataset.srWhitespaceError;
      }
      if (target.dataset.srInput === 'true') return;
      const hasActiveError =
        target.getAttribute('aria-invalid') === 'true' || Boolean(getOrCreateDomErrorNode(target, false));
      if (!hasActiveError) return;

      const whitespaceOnly =
        target.required && typeof target.value === 'string' && target.value.length > 0 && target.value.trim().length === 0;
      if (!whitespaceOnly && target.validity.valid) {
        setDomFieldError(target, '');
      } else {
        setDomFieldError(target, getFieldValidationMessage(target));
      }
    };

    const handleFocusOut = (event: FocusEvent | Event) => {
      const target = event.target;
      if (!isValidatableControl(target) || target.dataset.srInput === 'true') return;
      if (target.disabled || (target instanceof HTMLInputElement && target.readOnly)) return;
      const value = target.value ?? '';
      const whitespaceOnly =
        target.required && typeof value === 'string' && value.length > 0 && value.trim().length === 0;
      if (whitespaceOnly || (value.length > 0 && !target.validity.valid)) {
        setDomFieldError(target, getFieldValidationMessage(target));
      }
    };

    const handleSubmitCapture = (event: Event) => {
      const form = event.target;
      if (!(form instanceof HTMLFormElement) || form.noValidate) return;
      let firstInvalidControl: ValidatableElement | null = null;

      for (const element of Array.from(form.elements)) {
        if (!isValidatableControl(element) || element.disabled) continue;
        if (element.dataset.srWhitespaceError === 'true') {
          element.setCustomValidity('');
          delete element.dataset.srWhitespaceError;
        }
        const val = element.value ?? '';
        if (element.required && typeof val === 'string' && val.length > 0 && val.trim().length === 0) {
          element.dataset.srWhitespaceError = 'true';
          element.setCustomValidity(getFieldValidationMessage(element));
        }
        if (!element.validity.valid) {
          if (!firstInvalidControl) firstInvalidControl = element;
          if (element.dataset.srInput === 'true') {
            element.dispatchEvent(new Event('invalid', { cancelable: true }));
          } else {
            setDomFieldError(element, getFieldValidationMessage(element));
          }
        }
      }

      if (firstInvalidControl) {
        event.preventDefault();
        event.stopPropagation();
        firstInvalidControl.focus();
      }
    };

    document.addEventListener('invalid', handleInvalid, true);
    document.addEventListener('input', handleInputOrChange, true);
    document.addEventListener('change', handleInputOrChange, true);
    document.addEventListener('focusout', handleFocusOut, true);
    document.addEventListener('submit', handleSubmitCapture, true);
    return () => {
      document.removeEventListener('invalid', handleInvalid, true);
      document.removeEventListener('input', handleInputOrChange, true);
      document.removeEventListener('change', handleInputOrChange, true);
      document.removeEventListener('focusout', handleFocusOut, true);
      document.removeEventListener('submit', handleSubmitCapture, true);
    };
  }, []);
}

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
}

export const Input = forwardRef<HTMLInputElement, InputProps>(
  ({ className, label, error, id, onInvalid, onChange, onBlur, ...props }, ref) => {
    const [internalError, setInternalError] = useState('');
    const displayError = error || internalError;

    const handleInvalid = (event: FormEvent<HTMLInputElement>) => {
      event.preventDefault();
      setInternalError(getFieldValidationMessage(event.currentTarget, label));
      onInvalid?.(event);
    };

    const handleChange = (event: ChangeEvent<HTMLInputElement>) => {
      const el = event.currentTarget;
      if (el.dataset.srWhitespaceError === 'true') {
        el.setCustomValidity('');
        delete el.dataset.srWhitespaceError;
      }
      if (internalError) {
        const whitespaceOnly =
          el.required && el.value.length > 0 && el.value.trim().length === 0;
        if (!whitespaceOnly && el.validity.valid) {
          setInternalError('');
        } else {
          setInternalError(getFieldValidationMessage(el, label));
        }
      }
      onChange?.(event);
    };

    const handleBlur = (event: FocusEvent<HTMLInputElement>) => {
      const el = event.currentTarget;
      if (!el.disabled && !el.readOnly) {
        const whitespaceOnly =
          el.required && el.value.length > 0 && el.value.trim().length === 0;
        if (whitespaceOnly || (el.value.length > 0 && !el.validity.valid)) {
          setInternalError(getFieldValidationMessage(el, label));
        }
      }
      onBlur?.(event);
    };

    return (
      <div className="flex flex-col gap-1.5">
        {label && (
          <label htmlFor={id} className="text-sm font-medium text-gray-700">
            {label}
          </label>
        )}
        <input
          ref={ref}
          id={id}
          data-sr-input="true"
          aria-invalid={Boolean(displayError) || undefined}
          onInvalid={handleInvalid}
          onChange={handleChange}
          onBlur={handleBlur}
          className={cn(
            'w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm placeholder-gray-400 transition focus:outline-none',
            displayError && 'border-red-500 focus:border-red-500',
            className
          )}
          {...props}
        />
        {displayError && (
          <p role="alert" className="text-xs text-red-600">
            {displayError}
          </p>
        )}
      </div>
    );
  }
);
Input.displayName = 'Input';


