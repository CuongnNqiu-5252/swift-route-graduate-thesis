import 'package:flutter_test/flutter_test.dart';
import 'package:swiftroute_app/features/order/data/models/order_model.dart';

void main() {
  OrderModel order(Map<String, dynamic> fields) => OrderModel.fromJson({
    'id': 'abcdef12-3456',
    'pickup_lat': 10.03,
    'pickup_lng': 105.78,
    ...fields,
  });

  test('Pickup is available only before physical collection', () {
    for (final status in ['ASSIGNED', 'ACCEPTED', 'ARRIVED']) {
      expect(order({'delivery_task_status': status}).needsPickup, isTrue);
    }
    for (final status in [
      'PICKED_UP',
      'OUT_FOR_DELIVERY',
      'COMPLETED',
      'FAILED',
      'CANCELLED',
    ]) {
      expect(order({'delivery_task_status': status}).needsPickup, isFalse);
    }
    expect(order({'delivery_task_type': 'RETURN'}).needsPickup, isFalse);
  });

  test('Warehouse identity takes priority over coordinate grouping', () {
    expect(
      order({'pickup_location_id': 'warehouse-a'}).pickupGroupKey,
      'warehouse-a',
    );
    expect(order({}).pickupGroupKey, '10.0300,105.7800');
  });

  test('Display code uses external reference with readable fallback', () {
    expect(order({'external_order_code': ' SHOP-42 '}).displayCode, 'SHOP-42');
    expect(order({}).displayCode, '#ABCDEF');
  });
}
