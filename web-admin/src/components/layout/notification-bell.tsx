'use client';

import React, { useEffect, useMemo, useRef, useState } from 'react';
import Link from 'next/link';
import {
  AlertCircle,
  AlertTriangle,
  Banknote,
  Bell,
  Building2,
  CheckCircle2,
  PackageCheck,
  Radio,
  RefreshCw,
  Route,
  X,
} from 'lucide-react';
import { useNotifications } from '@/application/contexts/notification-context';
import { cn } from '@/lib/utils';
import { InboxNotificationDto } from '@/infrastructure/repositories/notification.repository';

type FilterTab = 'ALL' | 'INCIDENT' | 'OPERATIONS';

function formatRelativeTime(dateStr: string): string {
  try {
    const diffSec = Math.floor((Date.now() - new Date(dateStr).getTime()) / 1000);
    if (diffSec < 60) return 'Vừa xong';
    const diffMin = Math.floor(diffSec / 60);
    if (diffMin < 60) return `${diffMin} phút trước`;
    const diffHour = Math.floor(diffMin / 60);
    if (diffHour < 24) return `${diffHour} giờ trước`;
    return new Date(dateStr).toLocaleDateString('vi-VN', {
      hour: '2-digit',
      minute: '2-digit',
      day: '2-digit',
      month: '2-digit',
    });
  } catch {
    return dateStr;
  }
}

function getItemPresentation(item: InboxNotificationDto) {
  if (item.template_code === 'order.delivery_completed.v1') {
    return {
      label: 'Đã giao hàng',
      className: 'bg-emerald-50 text-emerald-800 border-emerald-200',
      icon: PackageCheck,
      ctaLabel: 'Xem đơn hàng',
    };
  }
  if (item.category === 'COD') {
    return {
      label: item.severity === 'HIGH' ? 'Lệch COD' : 'Bàn giao COD',
      className:
        item.severity === 'HIGH'
          ? 'bg-orange-50 text-orange-800 border-orange-200'
          : 'bg-teal-50 text-teal-800 border-teal-200',
      icon: Banknote,
      ctaLabel: 'Đối soát COD',
    };
  }
  if (item.category === 'DISPATCH') {
    return {
      label: item.severity === 'HIGH' || item.severity === 'MEDIUM' ? 'Cảnh báo điều phối' : 'Điều phối',
      className:
        item.severity === 'HIGH'
          ? 'bg-orange-50 text-orange-800 border-orange-200'
          : 'bg-blue-50 text-blue-800 border-blue-200',
      icon: Route,
      ctaLabel: 'Mở trang điều phối',
    };
  }
  if (item.category === 'WORKSPACE') {
    return {
      label: 'Workspace',
      className: 'bg-slate-100 text-slate-800 border-slate-200',
      icon: Building2,
      ctaLabel: 'Xem chi tiết',
    };
  }
  switch (item.severity) {
    case 'CRITICAL':
      return {
        label: 'Khẩn cấp',
        className: 'bg-red-100 text-red-800 border-red-200',
        icon: AlertCircle,
        ctaLabel: 'Xử lý sự cố ngay',
      };
    case 'HIGH':
      return {
        label: 'Nghiêm trọng',
        className: 'bg-orange-100 text-orange-800 border-orange-200',
        icon: AlertTriangle,
        ctaLabel: 'Xử lý sự cố ngay',
      };
    case 'MEDIUM':
      return {
        label: 'Sự cố',
        className: 'bg-amber-100 text-amber-800 border-amber-200',
        icon: AlertTriangle,
        ctaLabel: 'Kiểm tra sự cố',
      };
    default:
      return {
        label: 'Cập nhật',
        className: 'bg-blue-50 text-blue-800 border-blue-200',
        icon: CheckCircle2,
        ctaLabel: 'Xem chi tiết',
      };
  }
}

export function NotificationBell() {
  const notifications = useNotifications();
  const [isOpen, setIsOpen] = useState(false);
  const [activeTab, setActiveTab] = useState<FilterTab>('ALL');
  const containerRef = useRef<HTMLDivElement>(null);

  const activeIncidents = notifications?.alerts ?? [];
  const inbox = useMemo(() => notifications?.inbox ?? [], [notifications?.inbox]);
  const unreadCount = notifications?.unreadCount ?? 0;
  const isConnected = notifications?.isConnected ?? false;

  const hasActiveIncidents = activeIncidents.length > 0;
  const badgeCount = unreadCount > 0 ? unreadCount : activeIncidents.length;

  const filteredItems = useMemo(() => {
    if (activeTab === 'INCIDENT') {
      return inbox.filter((item) => item.category === 'INCIDENT');
    }
    if (activeTab === 'OPERATIONS') {
      return inbox.filter((item) => item.category !== 'INCIDENT');
    }
    return inbox;
  }, [activeTab, inbox]);

  const toggleOpen = () => {
    const next = !isOpen;
    setIsOpen(next);
    if (next && notifications) {
      notifications.markAllAsRead();
    }
  };

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    };
    if (isOpen) {
      document.addEventListener('mousedown', handleClickOutside);
    }
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [isOpen]);

  return (
    <div className="relative" ref={containerRef}>
      <button
        type="button"
        onClick={toggleOpen}
        aria-label={`Hòm thư thông báo (${inbox.length} mục)`}
        aria-expanded={isOpen}
        className={cn(
          'relative flex h-9 w-9 items-center justify-center rounded-lg border transition-colors',
          hasActiveIncidents
            ? 'border-amber-200 bg-amber-50/50 text-amber-800 hover:bg-amber-100'
            : 'border-gray-200 bg-white text-gray-600 hover:bg-gray-50',
        )}
      >
        <Bell size={18} className={cn(unreadCount > 0 && 'animate-bounce text-amber-600')} />

        {badgeCount > 0 && (
          <span
            className={cn(
              'absolute -top-1 -right-1 flex h-4 min-w-4 items-center justify-center rounded-full px-1 text-[10px] font-bold text-white',
              unreadCount > 0 ? 'bg-red-600' : 'bg-amber-600',
            )}
          >
            {badgeCount > 99 ? '99+' : badgeCount}
          </span>
        )}
      </button>

      {isOpen && (
        <div className="absolute right-0 z-50 mt-2 w-84 overflow-hidden rounded-xl border border-gray-200 bg-white shadow-xl sm:w-[25rem]">
          {/* Header */}
          <div className="flex items-center justify-between border-b border-gray-100 bg-gray-50/80 px-4 py-3">
            <div className="flex items-center gap-2">
              <span className="text-sm font-semibold text-gray-900">Hòm thư vận hành</span>
              <span
                className={cn(
                  'inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-medium',
                  isConnected ? 'bg-emerald-50 text-emerald-700' : 'bg-gray-100 text-gray-500',
                )}
                title={
                  isConnected
                    ? 'Đang nhận thông báo trực tiếp thời gian thực (SSE)'
                    : 'Đang kết nối lại luồng sự kiện'
                }
              >
                <Radio
                  size={10}
                  className={cn(isConnected ? 'animate-pulse text-emerald-600' : 'text-gray-400')}
                />
                {isConnected ? 'Trực tiếp' : 'Đang nối lại...'}
              </span>
            </div>

            <div className="flex items-center gap-1">
              <button
                type="button"
                onClick={() => void notifications?.refresh()}
                className="rounded p-1 text-gray-400 hover:bg-gray-200/60 hover:text-gray-700"
                title="Làm mới thông báo"
                aria-label="Làm mới thông báo"
              >
                <RefreshCw size={13} />
              </button>
              <button
                type="button"
                onClick={() => setIsOpen(false)}
                className="rounded p-1 text-gray-400 hover:bg-gray-200/60 hover:text-gray-700"
                aria-label="Đóng bảng thông báo"
              >
                <X size={15} />
              </button>
            </div>
          </div>

          {/* Filter Tabs */}
          <div className="grid grid-cols-3 border-b border-slate-100 bg-slate-50/50 p-1 text-xs font-medium">
            <button
              type="button"
              onClick={() => setActiveTab('ALL')}
              className={cn(
                'rounded-md py-1.5 transition-colors',
                activeTab === 'ALL'
                  ? 'bg-white font-semibold text-slate-900 shadow-xs'
                  : 'text-slate-600 hover:text-slate-900',
              )}
            >
              Tất cả ({inbox.length})
            </button>
            <button
              type="button"
              onClick={() => setActiveTab('INCIDENT')}
              className={cn(
                'rounded-md py-1.5 transition-colors',
                activeTab === 'INCIDENT'
                  ? 'bg-white font-semibold text-slate-900 shadow-xs'
                  : 'text-slate-600 hover:text-slate-900',
              )}
            >
              Sự cố ({inbox.filter((i) => i.category === 'INCIDENT').length})
            </button>
            <button
              type="button"
              onClick={() => setActiveTab('OPERATIONS')}
              className={cn(
                'rounded-md py-1.5 transition-colors',
                activeTab === 'OPERATIONS'
                  ? 'bg-white font-semibold text-slate-900 shadow-xs'
                  : 'text-slate-600 hover:text-slate-900',
              )}
            >
              Giao nhận & COD
            </button>
          </div>

          {/* List of notifications */}
          <div className="max-h-[380px] divide-y divide-gray-100 overflow-y-auto">
            {filteredItems.length === 0 ? (
              <div className="flex flex-col items-center justify-center p-6 text-center">
                <CheckCircle2 size={30} className="mb-2 text-emerald-500" />
                <p className="text-sm font-medium text-gray-900">Chưa có thông báo nào</p>
                <p className="mt-0.5 text-xs text-gray-500">
                  Khi tài xế hoàn tất giao hàng, bàn giao COD hoặc báo sự cố, thông báo sẽ hiển thị ngay tại đây.
                </p>
              </div>
            ) : (
              filteredItems.map((item) => {
                const badge = getItemPresentation(item);
                const BadgeIcon = badge.icon;
                const actionHref = item.action_url || '/orders';

                return (
                  <div
                    key={item.id}
                    className="flex flex-col gap-1.5 p-3.5 transition-colors hover:bg-slate-50"
                  >
                    <div className="flex items-start justify-between gap-2">
                      <span
                        className={cn(
                          'inline-flex items-center gap-1 rounded border px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wider',
                          badge.className,
                        )}
                      >
                        <BadgeIcon size={11} />
                        {badge.label}
                      </span>
                      <span className="shrink-0 text-[11px] text-gray-400">
                        {formatRelativeTime(item.created_at)}
                      </span>
                    </div>

                    <p className="text-xs font-semibold leading-snug text-gray-900">{item.title}</p>

                    <p className="line-clamp-2 text-xs leading-relaxed text-gray-600">{item.body}</p>

                    <div className="mt-0.5 flex items-center justify-between pt-0.5">
                      <Link
                        href={actionHref}
                        onClick={() => setIsOpen(false)}
                        className="flex items-center gap-1 text-xs font-medium text-blue-600 hover:text-blue-800 hover:underline"
                      >
                        {badge.ctaLabel} &rarr;
                      </Link>
                    </div>
                  </div>
                );
              })
            )}
          </div>

          {/* Footer */}
          <div className="flex items-center justify-between border-t border-gray-100 bg-gray-50/60 px-4 py-2 text-xs">
            <Link
              href="/orders"
              onClick={() => setIsOpen(false)}
              className="font-medium text-gray-600 hover:text-blue-600 hover:underline"
            >
              Đơn hàng
            </Link>
            <Link
              href="/operations"
              onClick={() => setIsOpen(false)}
              className="font-medium text-gray-600 hover:text-blue-600 hover:underline"
            >
              Quản lý Sự cố & COD &rarr;
            </Link>
          </div>
        </div>
      )}
    </div>
  );
}
