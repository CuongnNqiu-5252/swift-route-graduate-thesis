'use client';

import { PlanStatus } from '@/domain/entities/routing.entity';

interface PlanStatusBadgeProps {
  status: PlanStatus;
  acknowledgedAt?: string | null;
}

const STATUS_META: Record<PlanStatus, { label: string; className: string }> = {
  DRAFT: {
    label: 'Bản nháp chờ duyệt',
    className: 'border-amber-300 bg-amber-50 text-amber-900',
  },
  APPROVED: {
    label: 'Đã duyệt · Chờ giao tuyến',
    className: 'border-sky-300 bg-sky-50 text-sky-900',
  },
  DISPATCHED: {
    label: 'Đã điều phối cho tài xế',
    className: 'border-emerald-300 bg-emerald-50 text-emerald-900',
  },
  COMPLETED: {
    label: 'Đã hoàn thành tuyến',
    className: 'border-blue-300 bg-blue-50 text-blue-900',
  },
  CANCELLED: {
    label: 'Đã hủy kế hoạch',
    className: 'border-neutral-300 bg-neutral-100 text-neutral-700',
  },
};

export function PlanStatusBadge({ status, acknowledgedAt }: PlanStatusBadgeProps) {
  const meta = STATUS_META[status] || STATUS_META.DRAFT;
  const label =
    status === 'DISPATCHED' && acknowledgedAt
      ? 'Tài xế đã nhận tuyến'
      : meta.label;

  return (
    <span
      className={`inline-flex items-center rounded border px-2 py-0.5 text-xs font-medium leading-4 ${meta.className}`}
    >
      {label}
    </span>
  );
}

