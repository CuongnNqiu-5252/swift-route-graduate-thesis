'use client';

import { useAuth } from '@/application/contexts/auth-context';
import { AccessDeniedNotice } from '@/components/auth/permission-guard';
import { DeliveryReportView } from '@/components/reports/delivery-report';

export default function ReportsPage() {
  const { user, hasPermission } = useAuth();

  if (!user?.tenantId || !hasPermission('order:read')
      || !['OWNER', 'TENANT_ADMIN', 'OPERATOR'].includes(user.tenantRole ?? '')) {
    return <AccessDeniedNotice />;
  }

  return <DeliveryReportView key={user.tenantId} />;
}
