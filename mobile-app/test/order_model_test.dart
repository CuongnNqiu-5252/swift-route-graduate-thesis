import 'package:flutter_test/flutter_test.dart';
import 'package:swiftroute_app/features/order/data/models/order_model.dart';

void main() {
  OrderModel order(Map<String, dynamic> fields) => OrderModel.fromJson({
    'id': 'abcdef12-3456',
    'pickup_lat': 10.03,
    'pickup_lng': 105.78,
    ...fields,
  });

  test('Assigned forward stops are immediately ready for delivery without depot pickup', () {
    for (final status in ['ASSIGNED', 'ACCEPTED', 'ARRIVED', 'PICKED_UP', 'OUT_FOR_DELIVERY']) {
      final item = order({'delivery_task_status': status});
      expect(item.needsPickup, isFalse);
      expect(item.isOutForDelivery, isTrue);
    }
    for (final status in ['COMPLETED', 'FAILED', 'CANCELLED']) {
      final item = order({'delivery_task_status': status});
      expect(item.needsPickup, isFalse);
      expect(item.isOutForDelivery, isFalse);
    }
    final returnItem = order({'delivery_task_type': 'RETURN', 'delivery_task_status': 'OUT_FOR_DELIVERY'});
    expect(returnItem.needsPickup, isFalse);
    expect(returnItem.isOutForDelivery, isFalse);
    expect(returnItem.isReturnTask, isTrue);
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
