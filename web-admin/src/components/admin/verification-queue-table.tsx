'use client';

import { useLocale } from '@/i18n/provider';

import { useMemo } from 'react';
import {
  createColumnHelper,
  flexRender,
  getCoreRowModel,
  useReactTable,
} from '@tanstack/react-table';
import { ChevronRight, Inbox } from 'lucide-react';
import {
  formatAdminDateTime,
  VERIFICATION_STATUS_META,
} from '@/domain/entities/platform-verification.entity';
import { PageResult, Verification } from '@/infrastructure/repositories/tenant-workflow.repository';
import { Button } from '@/components/ui/button';

interface VerificationQueueTableProps {
  queue: PageResult<Verification> | null;
  loading: boolean;
  loadingDetailId: string | null;
  hasActiveFilters: boolean;
  onResetFilters: () => void;
  onOpenDetail: (requestId: string) => void;
  onPageChange: (nextPage: number) => void;
}

const columnHelper = createColumnHelper<Verification>();

export function VerificationQueueTable({
  queue,
  loading,
  loadingDetailId,
  hasActiveFilters,
  onResetFilters,
  onOpenDetail,
  onPageChange,
}: VerificationQueueTableProps) {
  const { t, intlLocale } = useLocale();
  const items = queue?.content ?? [];

  const columns = useMemo(
    () => [
      columnHelper.accessor((row) => row.business_name || t("Doanh nghiệp chưa đặt tên"), {
        id: 'business_name',
        header: () => <span>{t("Doanh nghiệp")}</span>,
        cell: (info) => {
          const row = info.row.original;
          return (
            <div className="min-w-0">
              <p className="truncate font-medium text-slate-950">{info.getValue()}</p>
              <p className="num mt-0.5 truncate text-xs text-slate-500">
                ID {row.tenant_id.slice(0, 8).toUpperCase()}{" "}{t("· Hồ sơ #")}{row.request_id.slice(0, 8).toUpperCase()}
              </p>
            </div>
          );
        },
      }),
      columnHelper.accessor('status', {
        header: () => <span>{t("Trạng thái")}</span>,
        cell: (info) => {
          const status = info.getValue();
          const meta = VERIFICATION_STATUS_META[status] ?? {
            label: t("Chưa xác định"),
            dotClass: 'bg-slate-400',
            badgeClass: 'bg-slate-100 text-slate-700',
          };
          return (
            <span
              className={`inline-flex items-center gap-1.5 rounded-md px-2 py-1 text-xs font-medium ${meta.badgeClass}`}
            >
              <span className={`h-1.5 w-1.5 rounded-full ${meta.dotClass}`} aria-hidden="true" />
              {t(meta.label)}
            </span>
          );
        },
      }),
      columnHelper.accessor('version', {
        header: () => <span className="block text-right">{t("Phiên bản")}</span>,
        cell: (info) => (
          <span className="num block text-right text-sm text-slate-600">
            v{info.getValue()}
          </span>
        ),
      }),
      columnHelper.accessor('submitted_at', {
        header: () => <span className="block text-right">{t("Thời điểm gửi")}</span>,
        cell: (info) => (
          <span className="num block text-right text-sm text-slate-600">
            {formatAdminDateTime(info.getValue(), intlLocale)}
          </span>
        ),
      }),
      columnHelper.display({
        id: 'actions',
        header: () => <span className="block text-right">{t("Thao tác")}</span>,
        cell: (info) => {
          const row = info.row.original;
          return (
            <div className="flex justify-end">
              <Button
                variant="outline"
                size="sm"
                onClick={(event) => {
                  event.stopPropagation();
                  onOpenDetail(row.request_id);
                }}
                isLoading={loadingDetailId === row.request_id}
                className="bg-white"
              >
                {t("Mở hồ sơ")}
                <ChevronRight className="h-3.5 w-3.5 text-slate-500" />
              </Button>
            </div>
          );
        },
      }),
    ],
    [intlLocale, loadingDetailId, onOpenDetail, t],
  );

  // TanStack Table handles headless rows & column mapping cleanly.
  // eslint-disable-next-line react-hooks/incompatible-library
  const table = useReactTable({
    data: items,
    columns,
    getCoreRowModel: getCoreRowModel(),
  });

  if (loading && items.length === 0) {
    return (
      <div className="flex h-64 items-center justify-center rounded-lg border border-slate-200 bg-white">
        <p className="text-sm text-slate-500">{t("Đang tải danh sách hồ sơ xét duyệt…")}</p>
      </div>
    );
  }

  if (!loading && items.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center rounded-lg border border-slate-200 bg-white px-6 py-14 text-center">
        <div className="flex h-10 w-10 items-center justify-center rounded-full border border-slate-200 bg-slate-50 text-slate-500">
          <Inbox className="h-5 w-5" aria-hidden="true" />
        </div>
        <p className="mt-3 text-sm font-semibold text-slate-900">{t("Không có hồ sơ phù hợp")}</p>
        <p className="mt-1 max-w-md text-xs leading-5 text-slate-500">
          {hasActiveFilters
            ? t("Không tìm thấy yêu cầu nào khớp bộ lọc hiện tại. Thử chuyển trạng thái hoặc xóa từ khóa tìm kiếm.")
            : t("Chưa có yêu cầu xét duyệt nào được gửi từ doanh nghiệp.")}
        </p>
        {hasActiveFilters && (
          <Button variant="outline" size="sm" onClick={onResetFilters} className="mt-4">
            {t("Đặt lại bộ lọc")}
          </Button>
        )}
      </div>
    );
  }

  return (
    <div className="space-y-3">
      <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white">
        <table className="w-full min-w-[680px] border-collapse text-left">
          <thead>
            {table.getHeaderGroups().map((headerGroup) => (
              <tr key={headerGroup.id} className="border-b border-slate-200 bg-slate-50/50">
                {headerGroup.headers.map((header) => (
                  <th
                    key={header.id}
                    scope="col"
                    className="px-4 py-3 text-xs font-medium text-slate-500"
                  >
                    {header.isPlaceholder
                      ? null
                      : flexRender(header.column.columnDef.header, header.getContext())}
                  </th>
                ))}
              </tr>
            ))}
          </thead>
          <tbody>
            {table.getRowModel().rows.map((row) => (
              <tr
                key={row.id}
                onClick={() => onOpenDetail(row.original.request_id)}
                className="cursor-pointer border-b border-slate-100 last:border-b-0 hover:bg-slate-50/70"
              >
                {row.getVisibleCells().map((cell) => (
                  <td key={cell.id} className="px-4 py-3.5 align-middle text-sm text-slate-700">
                    {flexRender(cell.column.columnDef.cell, cell.getContext())}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {queue && (
        <div className="flex flex-wrap items-center justify-between gap-3 text-xs text-slate-600">
          <span className="num">
            {queue.total_elements}{" "}{t("hồ sơ")}
            {queue.total_pages > 0 && ` · Trang ${queue.page_number + 1} / ${queue.total_pages}`}
          </span>
          {queue.total_pages > 1 && (
            <div className="flex items-center gap-2">
              <Button
                variant="outline"
                size="sm"
                disabled={queue.is_first || loading}
                onClick={() => onPageChange(queue.page_number - 1)}
              >
                {t("Trước")}
              </Button>
              <Button
                variant="outline"
                size="sm"
                disabled={queue.is_last || loading}
                onClick={() => onPageChange(queue.page_number + 1)}
              >
                {t("Sau")}
              </Button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
