'use client';

import { Suspense, useCallback, useEffect, useMemo, useState } from 'react';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { RefreshCw } from 'lucide-react';
import { useLocale } from '@/i18n/provider';
import { useDrivers } from '@/application/hooks/use-drivers';
import { DriverEarning, DriverSettlement, EarningPolicy } from '@/domain/entities/earning.entity';
import { earningRepository } from '@/infrastructure/repositories/earning.repository';
import { Button } from '@/components/ui/button';
import { DriverActivationPanel } from '@/components/drivers/driver-activation-panel';
import { DriverProfilesPanel } from '@/components/drivers/driver-profiles-panel';
import { DriverSettlementsPanel } from '@/components/drivers/driver-settlements-panel';
import { DriverEarningPolicyPanel } from '@/components/drivers/driver-earning-policy-panel';
import { useAuth } from '@/application/contexts/auth-context';
import { AccessDeniedNotice } from '@/components/auth/permission-guard';

type DriversTab = 'profiles' | 'activation' | 'settlements' | 'policy';

function parseTab(value: string | null): DriversTab {
  if (value === 'activation' || value === 'settlements' || value === 'policy') return value;
  return 'profiles';
}

export default function DriversPage() {
  return (
    <Suspense fallback={<DriversPageFallback />}>
      <DriversWorkspace />
    </Suspense>
  );
}

function DriversPageFallback() {
  return (
    <div className="space-y-6">
      <div className="h-14 animate-pulse rounded-xl bg-slate-100" />
      <div className="h-24 animate-pulse rounded-xl bg-slate-100" />
      <div className="h-72 animate-pulse rounded-xl bg-slate-100" />
    </div>
  );
}

function DriversWorkspace() {
  const { t } = useLocale();
  const { hasPermission } = useAuth();
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const activeTab = parseTab(searchParams.get('tab'));

  const { drivers, isLoading, error, createDriver, updateDriver, reviewDriver, refetch } = useDrivers();
  const [isCreateOpen, setIsCreateOpen] = useState(false);

  const [earnings, setEarnings] = useState<DriverEarning[]>([]);
  const [settlements, setSettlements] = useState<DriverSettlement[]>([]);
  const [policy, setPolicy] = useState<EarningPolicy | null>(null);
  const [settlementLoading, setSettlementLoading] = useState(true);
  const [settlementError, setSettlementError] = useState<string | null>(null);

  const loadSettlementData = useCallback(async () => {
    setSettlementLoading(true);
    setSettlementError(null);
    try {
      const [earningRows, settlementRows, currentPolicy] = await Promise.all([
        earningRepository.listEarnings(),
        earningRepository.listSettlements(),
        earningRepository.getPolicy(),
      ]);
      setEarnings(earningRows);
      setSettlements(settlementRows);
      setPolicy(currentPolicy);
    } catch (err) {
      setSettlementError(err instanceof Error ? err.message : t('Không thể tải dữ liệu đối soát'));
    } finally {
      setSettlementLoading(false);
    }
  }, [t]);

  useEffect(() => {
    let active = true;
    Promise.all([
      earningRepository.listEarnings(),
      earningRepository.listSettlements(),
      earningRepository.getPolicy(),
    ])
      .then(([earningRows, settlementRows, currentPolicy]) => {
        if (!active) return;
        setEarnings(earningRows);
        setSettlements(settlementRows);
        setPolicy(currentPolicy);
        setSettlementError(null);
      })
      .catch((err) => {
        if (active) {
          setSettlementError(err instanceof Error ? err.message : t('Không thể tải dữ liệu đối soát'));
        }
      })
      .finally(() => {
        if (active) setSettlementLoading(false);
      });

    return () => {
      active = false;
    };
  }, [t]);

  const reviewCount = useMemo(
    () => drivers.filter((driver) => driver.onboarding_status === 'PENDING_APPROVAL').length,
    [drivers]
  );

  const pendingSettlementCount = useMemo(() => {
    const driversReadyToSettle = new Set(
      earnings
        .filter((item) => item.status === 'PENDING' && !item.settlement_id)
        .map((item) => item.driver_id)
    ).size;
    const activeSettlements = settlements.filter((item) => item.status !== 'PAID').length;
    return driversReadyToSettle + activeSettlements;
  }, [earnings, settlements]);

  function selectTab(nextTab: DriversTab) {
    const params = new URLSearchParams(searchParams.toString());
    if (nextTab === 'profiles') {
      params.delete('tab');
    } else {
      params.set('tab', nextTab);
    }
    const query = params.toString();
    router.replace(query ? `${pathname}?${query}` : pathname, { scroll: false });
  }

  function handleRefresh() {
    if (activeTab === 'profiles') {
      void refetch();
      return;
    }
    void loadSettlementData();
  }

  const tabs: Array<{ key: DriversTab; label: string; count?: number }> = [
    { key: 'profiles', label: t('Hồ sơ & Duyệt'), count: reviewCount },
    { key: 'activation', label: t('Kích hoạt ứng dụng') },
    { key: 'settlements', label: t('Đối soát & Thu nhập'), count: pendingSettlementCount },
    { key: 'policy', label: t('Chính sách thu nhập') },
  ];

  if (!hasPermission(['driver:read', 'vehicle:read'])) {
    return <AccessDeniedNotice requiredPermission="driver:read" />;
  }

  return (
    <div className="space-y-4">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold tracking-tight text-slate-950">{t('Tài xế')}</h1>
        {(activeTab === 'settlements' || activeTab === 'policy') && (
          <Button
            variant="outline"
            size="sm"
            className="cursor-pointer"
            onClick={handleRefresh}
            disabled={settlementLoading}
          >
            <RefreshCw size={15} aria-hidden="true" />
            {t('Làm mới')}
          </Button>
        )}
      </header>

      <div className="border-b border-slate-200">
        <nav className="-mb-px flex gap-6 overflow-x-auto" role="tablist" aria-label={t('Khu vực quản lý tài xế')}>
          {tabs.map((tab) => {
            const active = activeTab === tab.key;
            return (
              <button
                key={tab.key}
                type="button"
                role="tab"
                aria-selected={active}
                onClick={() => selectTab(tab.key)}
                className={`flex shrink-0 cursor-pointer items-center gap-2 border-b-2 py-3 text-sm font-medium transition-colors ${
                  active
                    ? 'border-slate-950 text-slate-950'
                    : 'border-transparent text-slate-500 hover:border-slate-300 hover:text-slate-700'
                }`}
              >
                <span>{tab.label}</span>
                {tab.count !== undefined && tab.count > 0 && (
                  <span
                    className={`rounded-full px-2 py-0.5 text-xs font-medium tabular-nums ${
                      active ? 'bg-slate-900 text-white' : 'bg-slate-100 text-slate-700'
                    }`}
                  >
                    {tab.count}
                  </span>
                )}
              </button>
            );
          })}
        </nav>
      </div>

      {activeTab === 'profiles' && (
        <DriverProfilesPanel
          drivers={drivers}
          isLoading={isLoading}
          error={error}
          isCreateOpen={isCreateOpen}
          onCreateOpenChange={setIsCreateOpen}
          onCreateDriver={createDriver}
          onUpdateDriver={updateDriver}
          onReviewDriver={reviewDriver}
          onRefresh={handleRefresh}
          onBankAccountUpdated={async () => {
            await Promise.all([refetch(), loadSettlementData()]);
          }}
        />
      )}

      {activeTab === 'activation' && <DriverActivationPanel onDriversMutated={refetch} />}

      {activeTab === 'settlements' && (
        <DriverSettlementsPanel
          earnings={earnings}
          settlements={settlements}
          loading={settlementLoading}
          error={settlementError}
          onReload={loadSettlementData}
          onBankAccountUpdated={refetch}
        />
      )}

      {activeTab === 'policy' && (
        <DriverEarningPolicyPanel
          policy={policy}
          loading={settlementLoading}
          error={settlementError}
          onSaved={loadSettlementData}
          onReload={loadSettlementData}
        />
      )}
    </div>
  );
}
