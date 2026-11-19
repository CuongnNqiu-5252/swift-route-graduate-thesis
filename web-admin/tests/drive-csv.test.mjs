import assert from 'node:assert/strict';
import test from 'node:test';
import { downloadDriveCsv, downloadSheetTab, GoogleDriveAuthorizationError, listSheetTabs } from '../src/infrastructure/google/drive-csv.ts';
import { readOrderCsv } from '../src/application/order-import-csv.ts';
import { CSV_MAX_BYTES } from '../src/application/order-import-limits.mjs';

test('downloads the selected Drive file and passes its contents to the existing preview parser', async (t) => {
  const csv = 'externalOrderCode,recipientName,recipientPhone,timeWindowStart,timeWindowEnd,weightKg\n'
    + 'DRIVE-01,Khách thử,0900000000,2026-11-17T08:00:00+07:00,2026-11-17T17:00:00+07:00,1\n';
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    assert.equal(url.origin, 'https://www.googleapis.com');
    assert.equal(url.pathname, '/drive/v3/files/test-id');
    assert.equal(url.searchParams.get('alt'), 'media');
    assert.equal(options.headers.Authorization, 'Bearer test-token');
    return new Response(csv);
  });
  const file = await downloadDriveCsv({ id: 'test-id', name: 'orders.csv' }, 'test-token', AbortSignal.timeout(500));
  assert.equal(file.name, 'orders.csv');
  const { name, rows, errors } = await readOrderCsv(file);
  assert.equal(name, 'orders.csv');
  assert.equal(rows[0].recipientPhone, '0900000000');
  assert.equal(rows[0].deliveryLat, null);
  assert.deepEqual(errors, []);
});

test('rejects non-CSV files and stops oversized downloads without a content-length header', async (t) => {
  let canceled = false;
  const fetchMock = t.mock.method(globalThis, 'fetch', async () => new Response(new ReadableStream({
    start(controller) {
      controller.enqueue(new Uint8Array(CSV_MAX_BYTES + 1));
    },
    cancel() { canceled = true; },
  })));
  const signal = AbortSignal.timeout(500);
  await assert.rejects(downloadDriveCsv({ id: 'test-id', name: 'orders.xlsx' }, 'test-token', signal), /\.csv/);
  assert.equal(fetchMock.mock.callCount(), 0);
  await assert.rejects(downloadDriveCsv({ id: 'test-id', name: 'orders.csv' }, 'test-token', signal), /512 KB/);
  assert.equal(canceled, true);
});

test('lists grid tabs and imports a selected tab with formatted values and blanks', async (t) => {
  const title = "Đơn ' sáng";
  const selection = { id: 'sheet-id', name: 'Vận đơn' };
  t.mock.method(globalThis, 'fetch', async (url) => {
    if (!url.pathname.includes('/values/')) return Response.json({ sheets: [
      { properties: { title, sheetType: 'GRID' } },
      { properties: { title: 'Biểu đồ', sheetType: 'OBJECT' } },
    ] });
    assert.equal(decodeURIComponent(url.pathname.split('/values/')[1]), "'Đơn '' sáng'");
    assert.equal(url.searchParams.get('valueRenderOption'), 'FORMATTED_VALUE');
    return Response.json({ values: [
      ['externalOrderCode', 'recipientName', 'recipientPhone', 'timeWindowStart', 'timeWindowEnd', 'weightKg', 'notes'],
      ['SHEET-1', 'Khách, "thử"', '0900000000', '2026-11-17T08:00:00+07:00', '2026-11-17T17:00:00+07:00', '1'],
    ] });
  });
  const signal = AbortSignal.timeout(500);
  assert.deepEqual(await listSheetTabs(selection, 'mock', signal), [title]);
  const result = await readOrderCsv(await downloadSheetTab(selection, title, 'mock', signal));
  assert.equal(result.rows[0].recipientPhone, '0900000000');
  assert.equal(result.rows[0].recipientName, 'Khách, "thử"');
  assert.deepEqual(result.errors, []);
});

test('distinguishes an invalid Google session from a file permission error', async (t) => {
  let status = 401;
  t.mock.method(globalThis, 'fetch', async () => new Response(null, { status }));
  const signal = AbortSignal.timeout(500);
  const csv = { id: 'file-id', name: 'orders.csv' };
  const sheet = { id: 'sheet-id', name: 'Orders' };

  await assert.rejects(downloadDriveCsv(csv, 'expired', signal), GoogleDriveAuthorizationError);
  await assert.rejects(listSheetTabs(sheet, 'expired', signal), GoogleDriveAuthorizationError);
  await assert.rejects(downloadSheetTab(sheet, 'Morning', 'expired', signal), GoogleDriveAuthorizationError);

  status = 403;
  await assert.rejects(listSheetTabs(sheet, 'restricted', signal), (error) =>
    !(error instanceof GoogleDriveAuthorizationError) && /quyền/.test(error.message));
});
