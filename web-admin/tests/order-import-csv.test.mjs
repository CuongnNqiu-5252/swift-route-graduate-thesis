import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { parseOrderCsv } from '../src/application/order-import-csv.ts';

const template = readFileSync(new URL('../public/templates/order-import.csv', import.meta.url), 'utf8');
const headers = template.replace(/^\uFEFF/, '').split(/\r?\n/, 1)[0];
const example = 'DEMO-001,Nguyễn Văn A,0900000000,"225 đường 3/2, TP. Hồ Chí Minh",,,2026-11-16T08:00:00+07:00,2026-11-16T12:00:00+07:00,1.5,125000,Hàng mẫu';

test('reads the template columns, preserving leading zeroes and an unresolved address', () => {
  const { rows, errors } = parseOrderCsv('\uFEFF' + headers + '\n' + example);
  assert.deepEqual(errors, []);
  assert.equal(rows[0].recipientPhone, '0900000000');
  assert.equal(rows[0].deliveryAddress, '225 đường 3/2, TP. Hồ Chí Minh');
  assert.equal(rows[0].deliveryLat, null);
});

test('preserves quoted newlines and reports a bad date on the physical CSV line', () => {
  const multiline = example.replace('Hàng mẫu', '"Hàng ""mẫu""\r\nGiao tận nơi"');
  const invalid = example.replace('2026-11-16T08:00', '2026-02-30T08:00');
  const { rows, errors } = parseOrderCsv([headers, multiline, invalid].join('\r\n'));
  assert.equal(rows[0].notes, 'Hàng "mẫu"\r\nGiao tận nơi');
  assert.equal(errors[0].line, 4);
  assert.equal(errors[0].message, 'timeWindowStart');
});
