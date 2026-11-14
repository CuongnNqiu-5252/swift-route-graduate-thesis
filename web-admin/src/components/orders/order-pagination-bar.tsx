'use client';

import { FormEvent, useState } from 'react';
import type { Table } from '@tanstack/react-table';
import { ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight, X } from 'lucide-react';
import { useLocale } from '@/i18n/provider';
import { Button } from '@/components/ui/button';

export const ORDER_PAGE_SIZE_PRESETS = [5, 10, 15, 20, 25, 50] as const;

interface OrderPaginationBarProps<TData> {
  table: Table<TData>;
  totalOrders: number;
  isLoading: boolean;
  onClearSelection?: () => void;
}

type PageToken = number | 'ellipsis-left' | 'ellipsis-right';

function getVisiblePages(currentPage: number, totalPages: number): PageToken[] {
  if (totalPages <= 7) {
    return Array.from({ length: totalPages }, (_, index) => index + 1);
  }
  const tokens: PageToken[] = [1];
  const windowStart = Math.max(2, currentPage - 1);
  const windowEnd = Math.min(totalPages - 1, currentPage + 1);

  if (windowStart > 2) tokens.push('ellipsis-left');
  for (let page = windowStart; page <= windowEnd; page += 1) {
    tokens.push(page);
  }
  if (windowEnd < totalPages - 1) tokens.push('ellipsis-right');
  tokens.push(totalPages);
  return tokens;
}

/** Pagination footer backed by TanStack Table state with preset and custom page sizes (1-100). */
export function OrderPaginationBar<TData>({
  table,
  totalOrders,
  isLoading,
  onClearSelection,
}: OrderPaginationBarProps<TData>) {
  const { t } = useLocale();
  const { pageIndex, pageSize } = table.getState().pagination;
  const currentPage = pageIndex + 1;
  const totalPages = Math.max(1, table.getPageCount());

  const [editingCustomSize, setEditingCustomSize] = useState(false);
  const [customInput, setCustomInput] = useState(String(pageSize));
  const [customError, setCustomError] = useState('');

  const isPresetSize = (ORDER_PAGE_SIZE_PRESETS as readonly number[]).includes(pageSize);
  const rangeStart = totalOrders === 0 ? 0 : pageIndex * pageSize + 1;
  const rangeEnd = totalOrders === 0 ? 0 : Math.min(currentPage * pageSize, totalOrders);
  const visiblePages = getVisiblePages(currentPage, totalPages);

  function goToPage(nextPageIndex: number) {
    onClearSelection?.();
    table.setPageIndex(nextPageIndex);
  }

  function applyPageSize(nextSize: number) {
    onClearSelection?.();
    table.setPagination({ pageIndex: 0, pageSize: nextSize });
  }

  function handleCustomSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const parsed = Number(customInput);
    if (!Number.isInteger(parsed) || parsed < 1 || parsed > 100) {
      setCustomError(t('Nhập số nguyên từ 1 đến 100'));
      return;
    }
    setCustomError('');
    setEditingCustomSize(false);
    applyPageSize(parsed);
  }

  return (
    <div className="flex flex-col gap-3 border-t border-slate-200 bg-slate-50/70 px-4 py-3 text-xs text-slate-600 lg:flex-row lg:items-center lg:justify-between">
      <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
        <div className="flex flex-wrap items-center gap-2">
          <label htmlFor="orders-page-size" className="text-slate-600">
            {t('Số đơn mỗi trang')}
          </label>
          <select
            id="orders-page-size"
            aria-label={t('Số đơn mỗi trang')}
            value={editingCustomSize ? 'custom' : String(pageSize)}
            disabled={isLoading}
            onChange={(event) => {
              const value = event.target.value;
              if (value === 'custom') {
                setCustomInput(String(pageSize));
                setCustomError('');
                setEditingCustomSize(true);
                return;
              }
              setEditingCustomSize(false);
              setCustomError('');
              applyPageSize(Number(value));
            }}
            className="h-8 rounded-md border border-slate-300 bg-white px-2 text-xs font-medium tabular-nums text-slate-800 focus:border-blue-600 focus:outline-none"
          >
            {ORDER_PAGE_SIZE_PRESETS.map((size) => (
              <option key={size} value={size}>
                {size}
              </option>
            ))}
            {!isPresetSize && (
              <option value={pageSize}>
                {pageSize} ({t('tùy chỉnh')})
              </option>
            )}
            <option value="custom">{t('Nhập số khác…')}</option>
          </select>

          {editingCustomSize && (
            <form
              onSubmit={handleCustomSubmit}
              onKeyDown={(event) => {
                if (event.key === 'Escape') {
                  setEditingCustomSize(false);
                  setCustomError('');
                }
              }}
              className="flex flex-wrap items-center gap-1.5"
            >
              <input
                type="number"
                min={1}
                max={100}
                step={1}
                autoFocus
                value={customInput}
                onChange={(event) => {
                  setCustomInput(event.target.value);
                  if (customError) setCustomError('');
                }}
                aria-label={t('Số đơn tùy chỉnh (1–100)')}
                className="h-8 w-16 rounded-md border border-slate-300 bg-white px-2 text-xs tabular-nums text-slate-900 focus:border-blue-600 focus:outline-none"
              />
              <Button type="submit" size="sm" className="h-8 px-2.5 text-xs">
                {t('Áp dụng')}
              </Button>
              <button
                type="button"
                aria-label={t('Hủy')}
                onClick={() => {
                  setEditingCustomSize(false);
                  setCustomError('');
                }}
                className="flex h-8 w-8 items-center justify-center rounded-md border border-slate-200 bg-white text-slate-500 hover:bg-slate-100 hover:text-slate-800"
              >
                <X size={13} />
              </button>
              {customError && <span className="text-xs text-rose-600">{customError}</span>}
            </form>
          )}
        </div>

        <span className="tabular-nums text-slate-500">
          {isLoading && totalOrders === 0
            ? t('Đang tải…')
            : totalOrders === 0
              ? `0 ${t('đơn')}`
              : `${rangeStart}–${rangeEnd} / ${totalOrders} ${t('đơn')}`}
        </span>
      </div>

      <div className="flex flex-wrap items-center gap-1.5">
        <Button
          size="sm"
          variant="outline"
          disabled={isLoading || !table.getCanPreviousPage()}
          onClick={() => goToPage(0)}
          aria-label={t('Trang đầu')}
          title={t('Trang đầu')}
          className="h-8 w-8 px-0 text-xs"
        >
          <ChevronsLeft size={14} />
        </Button>
        <Button
          size="sm"
          variant="outline"
          disabled={isLoading || !table.getCanPreviousPage()}
          onClick={() => {
            onClearSelection?.();
            table.previousPage();
          }}
          className="h-8 px-2.5 text-xs"
        >
          <ChevronLeft size={14} /> {t('Trước')}
        </Button>

        <div className="hidden items-center gap-1 sm:flex">
          {visiblePages.map((token) =>
            typeof token === 'number' ? (
              <button
                key={token}
                type="button"
                disabled={isLoading}
                onClick={() => goToPage(token - 1)}
                aria-current={token === currentPage ? 'page' : undefined}
                className={`h-8 min-w-8 rounded-md px-2 text-xs font-medium tabular-nums transition-colors ${
                  token === currentPage
                    ? 'bg-slate-900 text-white'
                    : 'border border-slate-200 bg-white text-slate-700 hover:bg-slate-100'
                }`}
              >
                {token}
              </button>
            ) : (
              <span key={token} className="px-1 text-slate-400">
                …
              </span>
            )
          )}
        </div>

        <span className="px-1.5 tabular-nums sm:hidden">
          {t('Trang')} <strong>{currentPage}</strong> / {totalPages}
        </span>

        <Button
          size="sm"
          variant="outline"
          disabled={isLoading || !table.getCanNextPage()}
          onClick={() => {
            onClearSelection?.();
            table.nextPage();
          }}
          className="h-8 px-2.5 text-xs"
        >
          {t('Tiếp')} <ChevronRight size={14} />
        </Button>
        <Button
          size="sm"
          variant="outline"
          disabled={isLoading || !table.getCanNextPage()}
          onClick={() => goToPage(totalPages - 1)}
          aria-label={t('Trang cuối')}
          title={t('Trang cuối')}
          className="h-8 w-8 px-0 text-xs"
        >
          <ChevronsRight size={14} />
        </Button>
      </div>
    </div>
  );
}
