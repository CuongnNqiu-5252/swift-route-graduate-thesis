import assert from 'node:assert/strict';
import test from 'node:test';
import axios, { AxiosError } from 'axios';
import api, { ApiError, isRequestCancelled, mapsApi, routingApi } from '../src/lib/api.ts';

function useSession(t) {
  const storage = new Map([
    ['access_token', 'old-access'],
    ['refresh_token', 'refresh-token'],
    ['tenant_id', 'test-tenant'],
  ]);
  const originalStorage = Object.getOwnPropertyDescriptor(globalThis, 'localStorage');
  Object.defineProperty(globalThis, 'localStorage', {
    configurable: true,
    value: {
      getItem: (key) => storage.get(key) ?? null,
      setItem: (key, value) => storage.set(key, value),
      removeItem: (key) => storage.delete(key),
    },
  });
  globalThis.window = new EventTarget();
  window.location = { pathname: '/orders' };
  t.after(() => {
    delete globalThis.window;
    if (originalStorage) Object.defineProperty(globalThis, 'localStorage', originalStorage);
    else delete globalThis.localStorage;
  });
}

const response = (config, data, status = 200) => ({ config, data, status, headers: {} });

function unauthorized(config) {
  return new AxiosError('Unauthorized', 'ERR_BAD_REQUEST', config, undefined, response(config, {
    error: { message: 'Token invalid' },
  }, 401));
}

test('a rejected password reset neither sends session headers nor refreshes the session', async (t) => {
  useSession(t);
  const refresh = t.mock.method(axios, 'post', () => assert.fail('Unexpected refresh'));
  await assert.rejects(api.post('/auth/password/reset', {}, {
    adapter: async (config) => {
      assert.equal(config.headers.has('Authorization'), false);
      assert.equal(config.headers.has('X-Tenant-Id'), false);
      throw unauthorized(config);
    },
  }), (error) => error instanceof ApiError && error.status === 401 && error.message === 'Token invalid');
  assert.equal(refresh.mock.callCount(), 0);
  assert.equal(window.location.pathname, '/orders');
  assert.equal(localStorage.getItem('access_token'), 'old-access');
});

test('gateway and routing 401s share a refresh and retry in the selected tenant', async (t) => {
  useSession(t);
  const refresh = t.mock.method(axios, 'post', async (url) => {
    if (url.includes('/auth/refresh?')) {
      return { data: { data: { access_token: 'new-access', refresh_token: 'rotated' } } };
    }
    assert.ok(url.endsWith('/auth/tenants/test-tenant/switch'));
    return { data: { data: { access_token: 'tenant-access' } } };
  });
  const adapter = async (config) => {
    assert.equal(config.headers.get('X-Tenant-Id'), 'test-tenant');
    if (config.headers.get('Authorization') === 'Bearer old-access') throw unauthorized(config);
    assert.equal(config.headers.get('Authorization'), 'Bearer tenant-access');
    return response(config, { data: config.url, error: null });
  };
  const routingData = { ordered_stops: [{ stop_id: 'order-1' }] };
  const results = await Promise.all([
    api.get('/orders', { adapter }),
    routingApi.post('/api/v1/optimize/batch', { stops: ['order-1'] }, {
      adapter: async (config) => {
        assert.equal(config.headers.get('X-Tenant-Id'), 'test-tenant');
        assert.deepEqual(JSON.parse(config.data), { stops: ['order-1'] });
        if (config.headers.get('Authorization') === 'Bearer old-access') throw unauthorized(config);
        assert.equal(config.headers.get('Authorization'), 'Bearer tenant-access');
        return response(config, routingData);
      },
    }),
  ]);
  assert.deepEqual(results.map(({ data }) => data), ['/orders', routingData]);
  assert.equal(refresh.mock.callCount(), 2);
  assert.equal(localStorage.getItem('refresh_token'), 'rotated');
});

test('the same-origin maps client returns raw data without tenant credentials', async (t) => {
  useSession(t);
  const data = { address: 'Bến Nghé' };
  const result = await mapsApi.get('/api/maps/reverse', {
    adapter: async (config) => {
      assert.equal(config.headers.has('Authorization'), false);
      assert.equal(config.headers.has('X-Tenant-Id'), false);
      return response(config, data);
    },
  });
  assert.deepEqual(result.data, data);
  await assert.rejects(mapsApi.get('/api/maps/reverse', { signal: AbortSignal.abort() }), isRequestCancelled);
});

test('routing returns a second 401 without repeatedly refreshing', async (t) => {
  useSession(t);
  const refresh = t.mock.method(axios, 'post', async (url) => {
    if (url.includes('/auth/refresh?')) {
      return { data: { data: { access_token: 'new-access' } } };
    }
    return { data: { data: { access_token: 'tenant-access' } } };
  });
  const attempts = [];
  await assert.rejects(routingApi.post('/api/v1/optimize/batch', {}, {
    adapter: async (config) => {
      attempts.push(config.headers.get('Authorization'));
      throw unauthorized(config);
    },
  }), (error) => error instanceof ApiError && error.status === 401);
  assert.deepEqual(attempts, ['Bearer old-access', 'Bearer tenant-access']);
  assert.equal(refresh.mock.callCount(), 2);
});

test('public tracking endpoint omits session headers even when signed in', async (t) => {
  useSession(t);
  const res = await api.get('/public/tracking/TRK123456', {
    adapter: async (config) => {
      assert.equal(config.headers.has('Authorization'), false);
      assert.equal(config.headers.has('X-Tenant-Id'), false);
      return response(config, { data: { tracking_token: 'TRK123456' }, error: null });
    },
  });
  assert.deepEqual(res.data, { tracking_token: 'TRK123456' });
});
