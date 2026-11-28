import assert from 'node:assert/strict';
import test from 'node:test';
import {
  buildDefaultPaymentReference,
  buildPayrollTransferNote,
  buildVietQrUrl,
  formatBankDisplay,
  maskAccountNumber,
  stripVietnameseDiacritics,
} from '../src/application/vietqr.ts';

test('stripVietnameseDiacritics normalizes Vietnamese letters including đ/Đ to uppercase ASCII', () => {
  assert.equal(stripVietnameseDiacritics('Đỗ Nguyễn Văn Tài'), 'DO NGUYEN VAN TAI');
  assert.equal(stripVietnameseDiacritics('  Trần-Thị  Ánh! '), 'TRAN THI ANH');
});

test('buildPayrollTransferNote stays within 50 ASCII chars and includes driver name and settlement suffix', () => {
  const note = buildPayrollTransferNote('Nguyễn Văn Tài', '12345678-1234-1234-1234-123456abcdef');
  assert.equal(note, 'LUONG SWIFTROUTE NGUYEN VAN TAI ABCDEF');
  assert.ok(note.length <= 50);

  const longNote = buildPayrollTransferNote(
    'Nguyễn Hoàng Minh Phương Thảo Quỳnh Anh Bảo Ngọc',
    '12345678-1234-1234-1234-123456abcdef',
  );
  assert.ok(longNote.length <= 50);
  assert.ok(longNote.startsWith('LUONG SWIFTROUTE '));
  assert.ok(longNote.endsWith('ABCDEF'));
});

test('buildVietQrUrl builds a valid img.vietqr.io URL with encoded amount, addInfo, and accountName', () => {
  const raw = buildVietQrUrl({
    bankCode: ' vcb ',
    accountNumber: '0123 456 789',
    amount: 250000.4,
    description: 'Lương SwiftRoute Tài xế A',
    accountName: 'Nguyễn Văn Tài',
  });
  const parsed = new URL(raw);
  assert.equal(parsed.origin, 'https://img.vietqr.io');
  assert.equal(parsed.pathname, '/image/VCB-0123456789-compact2.png');
  assert.equal(parsed.searchParams.get('amount'), '250000');
  assert.equal(parsed.searchParams.get('addInfo'), 'LUONG SWIFTROUTE TAI XE A');
  assert.equal(parsed.searchParams.get('accountName'), 'NGUYEN VAN TAI');
});

test('buildVietQrUrl rejects missing bank code or account number', () => {
  assert.throws(() => buildVietQrUrl({ bankCode: '', accountNumber: '012345', amount: 10000 }));
  assert.throws(() => buildVietQrUrl({ bankCode: 'MB', accountNumber: '   ', amount: 10000 }));
});

test('formatBankDisplay, maskAccountNumber, and buildDefaultPaymentReference format cleanly', () => {
  assert.equal(formatBankDisplay('mb'), 'MB Bank (MB)');
  assert.equal(formatBankDisplay('CUSTOM'), 'CUSTOM');
  assert.equal(maskAccountNumber('0123456789'), '••••6789');
  assert.equal(maskAccountNumber('1234'), '1234');
  assert.equal(
    buildDefaultPaymentReference('00000000-0000-0000-0000-000000a1b2c3', new Date(2026, 10, 19)),
    'VIETQR-20261119-A1B2C3',
  );
});
