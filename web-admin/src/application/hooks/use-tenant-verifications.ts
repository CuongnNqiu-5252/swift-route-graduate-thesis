'use client';

import { useCallback, useEffect, useState } from 'react';
import { toast } from 'sonner';
import { ReviewDecisionValue } from '@/domain/entities/platform-verification.entity';
import {
  PageResult,
  tenantWorkflow,
  Verification,
  VerificationDetail,
} from '@/infrastructure/repositories/tenant-workflow.repository';

export function useTenantVerifications(allowed: boolean) {
  const [queue, setQueue] = useState<PageResult<Verification> | null>(null);
  const [statusFilter, setStatusFilter] = useState<string>('SUBMITTED');
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [page, setPage] = useState<number>(0);

  const [detail, setDetail] = useState<VerificationDetail | null>(null);
  const [loadingQueue, setLoadingQueue] = useState<boolean>(false);
  const [loadingDetail, setLoadingDetail] = useState<boolean>(false);
  const [submittingAction, setSubmittingAction] = useState<boolean>(false);
  const [queueError, setQueueError] = useState<string | null>(null);
  const [detailError, setDetailError] = useState<string | null>(null);

  const loadQueue = useCallback(async () => {
    if (!allowed) return;
    setLoadingQueue(true);
    setQueueError(null);
    try {
      const result = await tenantWorkflow.reviewQueue(statusFilter, searchQuery, page, 20);
      setQueue(result);
    } catch (error) {
      setQueueError(error instanceof Error ? error.message : 'Không tải được hàng đợi xét duyệt.');
    } finally {
      setLoadingQueue(false);
    }
  }, [allowed, page, searchQuery, statusFilter]);

  useEffect(() => {
    if (!allowed) return;
    let active = true;
    tenantWorkflow
      .reviewQueue(statusFilter, searchQuery, page, 20)
      .then((result) => {
        if (!active) return;
        setQueue(result);
        setQueueError(null);
      })
      .catch((error) => {
        if (!active) return;
        setQueueError(error instanceof Error ? error.message : 'Không tải được hàng đợi xét duyệt.');
      })
      .finally(() => {
        if (active) setLoadingQueue(false);
      });
    return () => {
      active = false;
    };
  }, [allowed, page, searchQuery, statusFilter]);

  const changeStatusFilter = useCallback((nextStatus: string) => {
    setStatusFilter(nextStatus);
    setPage(0);
  }, []);

  const applySearch = useCallback((nextSearch: string) => {
    setSearchQuery(nextSearch.trim());
    setPage(0);
  }, []);

  const resetFilters = useCallback(() => {
    setStatusFilter('SUBMITTED');
    setSearchQuery('');
    setPage(0);
  }, []);

  const openDetail = useCallback(async (requestId: string) => {
    setLoadingDetail(true);
    setDetailError(null);
    try {
      const result = await tenantWorkflow.reviewDetail(requestId);
      setDetail(result);
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Không tải được chi tiết hồ sơ.';
      setQueueError(message);
      toast.error(message);
    } finally {
      setLoadingDetail(false);
    }
  }, []);

  const closeDetail = useCallback(() => {
    setDetail(null);
    setDetailError(null);
  }, []);

  const submitDecision = useCallback(
    async (decision: ReviewDecisionValue, reason: string): Promise<boolean> => {
      if (!detail) return false;
      setSubmittingAction(true);
      setDetailError(null);
      try {
        await tenantWorkflow.review(detail.request, decision, reason.trim());
        toast.success('Đã ghi nhận kết quả xét duyệt doanh nghiệp.');
        setDetail(null);
        await loadQueue();
        return true;
      } catch (error) {
        const message =
          error instanceof Error ? error.message : 'Không thể ghi nhận quyết định xét duyệt.';
        setDetailError(message);
        try {
          const refreshed = await tenantWorkflow.reviewDetail(detail.request.request_id);
          setDetail(refreshed);
        } catch {
          // Keep the primary submission error visible if refresh also fails.
        }
        return false;
      } finally {
        setSubmittingAction(false);
      }
    },
    [detail, loadQueue],
  );

  const updateTenantLifecycle = useCallback(
    async (tenantId: string, nextStatus: 'SUSPENDED' | 'APPROVED', reason: string): Promise<boolean> => {
      setSubmittingAction(true);
      setDetailError(null);
      try {
        await tenantWorkflow.updateStatus(tenantId, nextStatus, reason.trim());
        toast.success(
          nextStatus === 'SUSPENDED'
            ? 'Đã tạm ngưng hoạt động doanh nghiệp.'
            : 'Đã khôi phục hoạt động doanh nghiệp.',
        );
        if (detail) {
          const refreshed = await tenantWorkflow.reviewDetail(detail.request.request_id);
          setDetail(refreshed);
        }
        await loadQueue();
        return true;
      } catch (error) {
        const message =
          error instanceof Error ? error.message : 'Không thể cập nhật trạng thái hoạt động doanh nghiệp.';
        setDetailError(message);
        return false;
      } finally {
        setSubmittingAction(false);
      }
    },
    [detail, loadQueue],
  );

  return {
    queue,
    statusFilter,
    searchQuery,
    page,
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
  };
}
