import assert from 'node:assert/strict';
import test from 'node:test';
import {
  DISCREPANCY_LABELS,
  INCIDENT_LABELS,
  buildOperationsPersonDirectory,
  formatDiscrepancyType,
  formatIncidentType,
  resolveDriverDisplayName,
  resolveReporterDisplayName,
} from '../src/application/operations-display.ts';

const identity = (text) => text;

test('formatIncidentType maps known enums and hides unknown system codes', () => {
  assert.equal(formatIncidentType('REFUSED_BY_RECIPIENT', identity), 'Khách từ chối nhận hàng');
  assert.equal(formatIncidentType('  refused_by_recipient ', identity), 'Khách từ chối nhận hàng');
  assert.equal(formatIncidentType('CUSTOMER_UNREACHABLE', identity), 'Không liên lạc được khách');
  assert.equal(formatIncidentType('PACKAGE_DAMAGED', identity), 'Hàng hóa hư hỏng');
  assert.equal(formatIncidentType('TRAFFIC_CONGESTION', identity), 'Kẹt xe / Ngập đường');
  assert.equal(formatIncidentType('DRIVER_EMERGENCY', identity), 'Tài xế gặp tình huống khẩn cấp');
  assert.equal(formatIncidentType('OFFLINE_SYNC_CONFLICT', identity), 'Xung đột đồng bộ ngoại tuyến');
  assert.equal(formatIncidentType('RETURN_DISCREPANCY_DAMAGED', identity), 'Hàng hoàn bị hư hỏng');
  assert.equal(
    formatIncidentType('RETURN_DISCREPANCY_SOMETHING', identity),
    'Chênh lệch khi nhận hàng hoàn',
  );
  assert.equal(formatIncidentType('NEW_UNKNOWN_CODE', identity), 'Sự cố khác');
  assert.equal(formatIncidentType('', identity), 'Sự cố khác');
  assert.equal(formatIncidentType(null, identity), 'Sự cố khác');
});

test('formatDiscrepancyType maps discrepancy codes and hides unknown system codes', () => {
  assert.equal(formatDiscrepancyType('DAMAGED', identity), 'Hàng hóa bị vỡ / hư hỏng');
  assert.equal(formatDiscrepancyType('MISSING_ITEMS', identity), 'Thiếu kiện hàng / thiếu sản phẩm');
  assert.equal(formatDiscrepancyType('WRONG_ITEMS', identity), 'Sai sản phẩm hoàn về');
  assert.equal(
    formatDiscrepancyType('REFUSED_BY_RECIPIENT', identity),
    'Bao bì rách nát / khách từ chối',
  );
  assert.equal(formatDiscrepancyType('OTHER', identity), 'Lý do khác');
  assert.equal(formatDiscrepancyType('UNMAPPED_ENUM', identity), 'Chênh lệch khác');
  assert.equal(formatDiscrepancyType(undefined, identity), 'Chênh lệch khác');
});

test('resolveDriverDisplayName and resolveReporterDisplayName resolve full names without leaking UUIDs', () => {
  const directory = buildOperationsPersonDirectory(
    [
      {
        id: 'driver-profile-1',
        user_id: '4aa14b10-1111-2222-3333-444444444444',
        name: 'Nguyễn Văn Tài',
      },
      {
        id: 'driver-profile-empty',
        user_id: '5bb25c20-1111-2222-3333-444444444444',
        name: '   ',
      },
    ],
    [
      {
        user_id: '4aa14b10-1111-2222-3333-444444444444',
        full_name: 'Nguyễn Văn Tài',
        email: 'tai@swiftroute.vn',
      },
      {
        user_id: '5bb25c20-1111-2222-3333-444444444444',
        full_name: 'Trần Thị Điều Phối',
        email: 'dieuphoi@swiftroute.vn',
      },
      {
        user_id: '6cc36d30-1111-2222-3333-444444444444',
        full_name: '   ',
        email: 'email-only@swiftroute.vn',
      },
    ],
    {
      id: '7dd47e40-1111-2222-3333-444444444444',
      name: 'Quản trị viên Hiện tại',
      email: 'admin@swiftroute.vn',
    },
  );

  // Resolves by driver user_id and driver profile id
  assert.equal(
    resolveDriverDisplayName('4aa14b10-1111-2222-3333-444444444444', directory, identity),
    'Nguyễn Văn Tài',
  );
  assert.equal(
    resolveDriverDisplayName('driver-profile-1', directory, identity),
    'Nguyễn Văn Tài',
  );

  // Blank driver name falls back to member full_name before email
  assert.equal(
    resolveDriverDisplayName('5bb25c20-1111-2222-3333-444444444444', directory, identity),
    'Trần Thị Điều Phối',
  );

  // Member without full_name falls back to email
  assert.equal(
    resolveReporterDisplayName('6cc36d30-1111-2222-3333-444444444444', directory, identity),
    'email-only@swiftroute.vn',
  );

  // Reporter resolves from currentUser when not in members/drivers
  assert.equal(
    resolveReporterDisplayName('7dd47e40-1111-2222-3333-444444444444', directory, identity),
    'Quản trị viên Hiện tại',
  );

  // Empty/null IDs use configurable empty label
  assert.equal(resolveDriverDisplayName(null, directory, identity), 'Chưa gán');
  assert.equal(resolveDriverDisplayName('  ', directory, identity, 'Chưa có tên'), 'Chưa có tên');
  assert.equal(resolveReporterDisplayName(null, directory, identity), 'Hệ thống');

  // Unknown IDs never leak raw UUIDs or prefixes
  assert.equal(
    resolveDriverDisplayName('99999999-aaaa-bbbb-cccc-1234567890ab', directory, identity),
    'Tài xế chưa rõ tên',
  );
  assert.equal(
    resolveReporterDisplayName('99999999-aaaa-bbbb-cccc-1234567890ab', directory, identity),
    'Chưa rõ tên',
  );
});

test('incident and discrepancy catalogs have no duplicate aliases and align 1-to-1', () => {
  const incidentValues = Object.values(INCIDENT_LABELS);
  assert.equal(new Set(incidentValues).size, incidentValues.length);

  const discrepancyValues = Object.values(DISCREPANCY_LABELS);
  assert.equal(new Set(discrepancyValues).size, discrepancyValues.length);

  for (const key of Object.keys(DISCREPANCY_LABELS)) {
    assert.equal(Object.hasOwn(INCIDENT_LABELS, `RETURN_DISCREPANCY_${key}`), true);
  }
});
