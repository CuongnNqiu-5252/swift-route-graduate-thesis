import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  EMPTY_DISPATCH_MAP_FILTERS, filterDispatchMapRoutes, UNASSIGNED_DRIVER, UNKNOWN_WAREHOUSE,
} from '../src/application/dispatch-map-filters.ts';

const routes = [
  { id: 'a', driverId: 'driver-1', warehouseId: 'warehouse-1', status: 'DISPATCHED' },
  { id: 'b', driverId: 'driver-1', warehouseId: 'warehouse-2', status: 'APPROVED' },
  { id: 'c', driverId: 'driver-2', warehouseId: 'warehouse-1', status: 'APPROVED' },
  { id: 'd', status: 'DRAFT' },
];
const ids = (filters) => filterDispatchMapRoutes(routes, { ...EMPTY_DISPATCH_MAP_FILTERS, ...filters })
  .map((route) => route.id);

test('dispatch map filters are combined across driver, warehouse and status', () => {
  assert.deepEqual(ids({}), ['a', 'b', 'c', 'd']);
  assert.deepEqual(ids({ driver: 'driver-1', warehouse: 'warehouse-2', status: 'APPROVED' }), ['b']);
  assert.deepEqual(ids({ driver: 'driver-1', warehouse: 'warehouse-2', status: 'DISPATCHED' }), []);
});

test('unassigned routes and unknown warehouses have explicit choices', () => {
  assert.deepEqual(ids({ driver: UNASSIGNED_DRIVER }), ['d']);
  assert.deepEqual(ids({ warehouse: UNKNOWN_WAREHOUSE }), ['d']);
  assert.deepEqual(ids({ warehouse: 'warehouse-1' }), ['a', 'c']);
});
