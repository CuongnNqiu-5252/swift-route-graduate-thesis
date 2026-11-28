import type { MapOverviewRoute, PlanStatus } from '@/domain/entities/routing.entity';

export const UNASSIGNED_DRIVER = 'unassigned';
export const UNKNOWN_WAREHOUSE = 'unknown';
export const DISPATCH_MAP_STATUSES = ['DRAFT', 'APPROVED', 'DISPATCHED'] as const satisfies readonly PlanStatus[];

export type DispatchMapFilters = {
  driver: string;
  warehouse: string;
  status: (typeof DISPATCH_MAP_STATUSES)[number] | '';
};

export const EMPTY_DISPATCH_MAP_FILTERS: DispatchMapFilters = { driver: '', warehouse: '', status: '' };

export function filterDispatchMapRoutes(routes: MapOverviewRoute[], filters: DispatchMapFilters) {
  return routes.filter((route) =>
    (!filters.driver || (route.driverId ?? UNASSIGNED_DRIVER) === filters.driver)
    && (!filters.warehouse || (route.warehouseId ?? UNKNOWN_WAREHOUSE) === filters.warehouse)
    && (!filters.status || route.status === filters.status));
}
