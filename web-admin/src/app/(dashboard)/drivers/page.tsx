'use client';

import { Suspense, useCallback, useEffect, useMemo, useState } from 'react';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { RefreshCw, UserPlus } from 'lucide-react';
import { useLocale } from '@/i18n/provider';
import { useDrivers } from '@/application/hooks/use-drivers';
import { TenantInvitation } from '@/domain/entities/member.entity';
import { DriverEarning, DriverSettlement, EarningPolicy } from '@/domain/entities/earning.entity';
import { memberRepository } from '@/infrastructure/repositories/member.repository';
import { earningRepository } from '@/infrastructure/repositories/earning.repository';
import { Button } from '@/components/ui/button';
import { AddDriverMode, DriverProfilesPanel } from '@/components/drivers/driver-profiles-panel';
import { DriverSettlementsPanel } from '@/components/drivers/driver-settlements-panel';
import { DriverEarningPolicyPanel } from '@/components/drivers/driver-earning-policy-panel';

type DriversTab = 'profiles' | 'settlements' | 'policy';

function parseTab(value: string | null): DriversTab {
  if (value === 'settlements' || value === 'policy') return value;
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
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const activeTab = parseTab(searchParams.get('tab'));

  const { drivers, isLoading, error, createDriver, reviewDriver, refetch } = useDrivers();
  const [mode, setMode] = useState<AddDriverMode | null>(null);
  const [invitations, setInvitations] = useState<TenantInvitation[]>([]);

  const [earnings, setEarnings] = useState<DriverEarning[]>([]);
  const [settlements, setSettlements] = useState<DriverSettlement[]>([]);
  const [policy, setPolicy] = useState<EarningPolicy | null>(null);
  const [settlementLoading, setSettlementLoading] = useState(true);
  const [settlementError, setSettlementError] = useState<string | null>(null);

  const loadInvitations = useCallback(async () => {
    try {
      const rows = await memberRepository.getInvitations();
      setInvitations(rows.filter((item) => item.role === 'DRIVER'));
    } catch {
      // Keep profile view functional even if invitation listing fails transiently.
    }
  }, []);

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
    memberRepository
      .getInvitations()
      .then((rows) => {
        if (active) setInvitations(rows.filter((item) => item.role === 'DRIVER'));
      })
      .catch(() => {
        // Ignore transient invitation fetch error on initial mount.
      });

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
      void loadInvitations();
      return;
    }
    void loadSettlementData();
  }

  const tabs: Array<{ key: DriversTab; label: string; count?: number }> = [
    { key: 'profiles', label: t('Hồ sơ & Duyệt'), count: reviewCount },
    { key: 'settlements', label: t('Đối soát & Thu nhập'), count: pendingSettlementCount },
    { key: 'policy', label: t('Chính sách thu nhập') },
  ];

  return (
    <div className="space-y-6">
      <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight text-slate-950">{t('Tài xế')}</h1>
          <p className="mt-1 text-sm text-slate-600">
            {t('Quản lý hồ sơ, đối soát thu nhập và chính sách chi trả cho đội ngũ tài xế.')}
          </p>
        </div>
        <div className="flex gap-2">
          <Button
            variant="outline"
            onClick={handleRefresh}
            disabled={activeTab === 'profiles' ? isLoading : settlementLoading}
          >
            <RefreshCw size={15} />
            {t('Làm mới')}
          </Button>
          {activeTab === 'profiles' && (
            <Button onClick={() => setMode('choose')}>
              <UserPlus size={16} />
              {t('Thêm tài xế')}
            </Button>
          )}
        </div>
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
                className={`flex shrink-0 items-center gap-2 border-b-2 py-3 text-sm font-medium transition-colors ${
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
          invitations={invitations}
          mode={mode}
          onModeChange={setMode}
          onCreateDriver={createDriver}
          onReviewDriver={reviewDriver}
          onReloadInvitations={loadInvitations}
        />
      )}

      {activeTab === 'settlements' && (
        <DriverSettlementsPanel
          earnings={earnings}
          settlements={settlements}
          loading={settlementLoading}
          error={settlementError}
          onReload={loadSettlementData}
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
