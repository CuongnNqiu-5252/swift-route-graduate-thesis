'use client';

import { FormEvent, useState } from 'react';
import { RefreshCw, Search } from 'lucide-react';
import { useAuth } from '@/application/contexts/auth-context';
import { useTenantVerifications } from '@/application/hooks/use-tenant-verifications';
import { VerificationDetailDialog } from '@/components/admin/verification-detail-dialog';
import { VerificationQueueTable } from '@/components/admin/verification-queue-table';
import { Button } from '@/components/ui/button';
import { VERIFICATION_FILTER_OPTIONS } from '@/domain/entities/platform-verification.entity';
import { isSystemAdmin } from '@/lib/portal-access';

export default function TenantVerificationsPage() {
  const { user } = useAuth();
  const allowed = isSystemAdmin(user);

  const {
    queue,
    statusFilter,
    searchQuery,
    detail,
    loadingQueue,
    loadingDetail,
    submittingAction,
    queueError,
    detailError,
    setPage,
    loadQueue,
    changeStatusFilter,
    applySearch,
    resetFilters,
    openDetail,
    closeDetail,
    submitDecision,
    updateTenantLifecycle,
  } = useTenantVerifications(allowed);

  const [searchInput, setSearchInput] = useState('');
  const [activeRequestId, setActiveRequestId] = useState<string | null>(null);

  if (!allowed) {
    return (
      <div className="rounded-lg border border-rose-200 bg-rose-50/70 p-4 text-sm text-rose-700">
        Chỉ tài khoản System Admin mới có quyền xét duyệt hồ sơ doanh nghiệp.
      </div>
    );
  }

  function handleSearchSubmit(event: FormEvent) {
    event.preventDefault();
    applySearch(searchInput);
  }

  function handleResetFilters() {
    setSearchInput('');
    resetFilters();
  }

  async function handleOpenDetail(requestId: string) {
    setActiveRequestId(requestId);
    try {
      await openDetail(requestId);
    } finally {
      setActiveRequestId(null);
    }
  }

  const hasActiveFilters = statusFilter !== 'SUBMITTED' || searchQuery.length > 0;

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4 border-b border-slate-200 pb-5">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight text-slate-950">
            Xét duyệt doanh nghiệp
          </h1>
          <p className="mt-1 text-sm text-slate-500">
            Kiểm tra hồ sơ vận hành đã gửi, phê duyệt kích hoạt và quản lý trạng thái hoạt động doanh nghiệp.
          </p>
        </div>

        <Button
          variant="outline"
          size="sm"
          onClick={() => void loadQueue()}
          isLoading={loadingQueue}
          className="bg-white"
        >
          <RefreshCw className="h-3.5 w-3.5" />
          Làm mới
        </Button>
      </div>

      {queueError && !detail && (
        <div
          role="alert"
          className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-rose-200 bg-rose-50/70 px-4 py-3 text-sm text-rose-800"
        >
          <span>{queueError}</span>
          <Button variant="outline" size="sm" onClick={() => void loadQueue()}>
            Thử lại
          </Button>
        </div>
      )}

      <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <div
          role="tablist"
          aria-label="Lọc theo trạng thái hồ sơ"
          className="flex flex-wrap items-center gap-1 rounded-lg border border-slate-200 bg-slate-50/70 p-1"
        >
          {VERIFICATION_FILTER_OPTIONS.map((option) => {
            const active = statusFilter === option.value;
            return (
              <button
                key={option.value || 'all'}
                role="tab"
                type="button"
                aria-selected={active}
                onClick={() => changeStatusFilter(option.value)}
                className={`rounded-md px-3 py-1.5 text-xs font-medium transition-colors ${
                  active
                    ? 'border border-slate-200 bg-white text-slate-950'
                    : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                {option.label}
              </button>
            );
          })}
        </div>

        <form onSubmit={handleSearchSubmit} className="flex w-full gap-2 lg:w-80">
          <div className="relative flex-1">
            <Search
              aria-hidden="true"
              className="pointer-events-none absolute left-3 top-3.5 h-4 w-4 text-slate-400"
            />
            <input
              value={searchInput}
              onChange={(event) => setSearchInput(event.target.value)}
              placeholder="Tìm tên doanh nghiệp…"
              aria-label="Tìm theo tên doanh nghiệp"
              className="w-full rounded-lg border border-slate-300 bg-white pl-9 pr-3 text-sm text-slate-900 outline-none placeholder:text-slate-400 focus:border-slate-500"
            />
          </div>
          <Button type="submit" variant="outline" className="shrink-0 bg-white">
            Tìm
          </Button>
        </form>
      </div>

      <VerificationQueueTable
        queue={queue}
        loading={loadingQueue}
        loadingDetailId={loadingDetail ? activeRequestId : null}
        hasActiveFilters={hasActiveFilters}
        onResetFilters={handleResetFilters}
        onOpenDetail={(requestId) => void handleOpenDetail(requestId)}
        onPageChange={(nextPage) => setPage(nextPage)}
      />

      <VerificationDetailDialog
        detail={detail}
        submitting={submittingAction}
        error={detailError}
        onClose={closeDetail}
        onSubmitDecision={submitDecision}
        onUpdateLifecycle={updateTenantLifecycle}
      />
    </div>
  );
}
