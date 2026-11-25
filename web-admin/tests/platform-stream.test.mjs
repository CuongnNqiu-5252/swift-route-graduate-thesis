import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import ts from 'typescript';
import api from '../src/lib/api.ts';

const path = new URL('../src/infrastructure/repositories/platform.repository.ts', import.meta.url);
const source = readFileSync(path, 'utf8').replace('@/lib/api', new URL('../src/lib/api.ts', import.meta.url).href);
const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText;
const { platformRepository } = await import(`data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`);

test('admin transport reuses Notification SSE and handles its existing plain-text ping', async (t) => {
  const wire = 'event:connect\ndata:{"scope":"platform"}\n\n'
    + 'event:ping\ndata:heartbeat\n\n'
    + 'event:platform-audit\ndata:{"audit_id":"event-id"}\n\n'
    + 'event:platform-resync\ndata:{}\n\n';
  t.mock.method(api, 'request', async (config) => {
    assert.equal(config.url, '/notifications/platform/stream');
    return { data: new ReadableStream({ start(controller) {
      controller.enqueue(new TextEncoder().encode(wire)); controller.close();
    } }) };
  });
  const get = t.mock.method(api, 'get', async () => { throw new Error('Heartbeat must not fetch data'); });
  const received = [];
  await platformRepository.stream(new AbortController().signal, (event, value) => received.push([event, value]));
  assert.deepEqual(received, [['connect', { scope: 'platform' }], ['heartbeat', null],
    ['platform-audit', { audit_id: 'event-id' }], ['platform-resync', {}]]);
  assert.equal(get.mock.callCount(), 0);
});

test('platform snapshot stays behind the existing authenticated Order API', async (t) => {
  const snapshot = { as_of: '2026-11-19T00:00:00Z', audit: { entries: [] }, overview: {} };
  t.mock.method(api, 'get', async (url, options) => {
    assert.equal(url, '/tenants/platform/snapshot');
    assert.ok(options.signal instanceof AbortSignal);
    return { data: snapshot };
  });
  assert.deepEqual(await platformRepository.snapshot(new AbortController().signal), snapshot);
});
