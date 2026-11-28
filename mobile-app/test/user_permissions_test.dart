import 'package:flutter_test/flutter_test.dart';
import 'package:swiftroute_app/data/models/auth_model.dart';

void main() {
  group('UserModel granular permissions', () {
    test('parses normalized permissions and permissionsVersion from payload', () {
      final user = UserModel.fromJson({
        'id': 'u-1',
        'email': 'driver@swiftroute.vn',
        'full_name': 'Nguyen Van Tai',
        'role': 'DRIVER',
        'permissions': ['ORDER:READ', 'order:deliver', 'driver:self', 'dispatch:read'],
        'permissions_version': 3,
      });

      expect(user.permissionsVersion, 3);
      expect(user.hasPermission('order:read'), isTrue);
      expect(user.hasPermission('driver:self'), isTrue);
      expect(user.hasPermission('dispatch:read'), isTrue);
      expect(user.hasPermission('member:manage'), isFalse);
    });

    test('denies driver:self when explicit permission list omits it', () {
      final user = UserModel.fromJson({
        'id': 'u-2',
        'email': 'revoked@swiftroute.vn',
        'role': 'DRIVER',
        'permissions': ['order:read'],
        'permissions_version': 4,
      });

      expect(user.hasPermission('driver:self'), isFalse);
      expect(user.hasPermission('order:read'), isTrue);
    });

    test('owner bypasses granular checks', () {
      final owner = UserModel.fromJson({
        'id': 'u-3',
        'email': 'owner@swiftroute.vn',
        'role': 'OWNER',
        'permissions': ['order:read'],
      });

      expect(owner.hasPermission('member:manage'), isTrue);
    });
  });
}
