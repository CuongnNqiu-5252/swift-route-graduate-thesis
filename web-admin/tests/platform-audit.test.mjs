import assert from 'node:assert/strict';
import test from 'node:test';
import { mergeAuditEntries, matchesAudit } from '../src/application/platform-audit.ts';

const entry = (id, time, extra = {}) => ({ id, created_at: time, category: 'ACCESS', severity: 'WARNING',
  action: 'TENANT_SUSPENDED', tenant_name: 'Business', actor_id: 'account-id', tenant_id: 'tenant-id', ...extra });

test('snapshot and pushed events merge once per ID, including tied timestamps', () => {
  const a = entry('a', '2026-11-19T01:00:00Z'); const b = entry('b', a.created_at);
  const c = entry('c', '2026-11-19T02:00:00Z');
  assert.deepEqual(mergeAuditEntries([a, b], [c, a]).map((row) => row.id), ['c', 'b', 'a']);
});

test('live filtering agrees with category, severity, search and history window', () => {
  const now = Date.parse('2026-11-19T12:00:00Z');
  const filter = { query: 'BUSINESS', category: 'ACCESS', severity: 'WARNING', days: 7 };
  assert.equal(matchesAudit(entry('a', '2026-11-19T01:00:00Z'), filter, now), true);
  assert.equal(matchesAudit(entry('a', '2026-11-01T01:00:00Z'), filter, now), false);
  assert.equal(matchesAudit(entry('a', '2026-11-19T01:00:00Z', { severity: 'INFO' }), filter, now), false);
  assert.equal(matchesAudit(entry('a', '2026-11-19T01:00:00Z'), { ...filter, query: 'account-id' }, now), true);
});
