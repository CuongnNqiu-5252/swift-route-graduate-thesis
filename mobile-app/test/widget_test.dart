import 'package:flutter_test/flutter_test.dart';
import 'package:swiftroute_app/core/utils/formatters.dart';

void main() {
  test('Currency formatting preserves whole Vietnamese dong amounts', () {
    expect(AppFormatters.currency(248000), contains('248.000'));
    expect(AppFormatters.currency(0), contains('0'));
  });
}
