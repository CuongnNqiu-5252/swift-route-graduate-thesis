import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:swiftroute_app/features/driver/presentation/widgets/next_stop_spotlight_card.dart';
import 'package:swiftroute_app/features/order/data/models/order_model.dart';

void main() {
  OrderModel createOrder({
    String id = '26f16c00-0000-0000-0000-000000000000',
    String status = 'OUT_FOR_DELIVERY',
    String code = '26F16C',
    double cod = 0,
    bool isReturn = false,
  }) {
    return OrderModel.fromJson({
      'id': id,
      'delivery_task_status': status,
      'delivery_task_type': isReturn ? 'RETURN' : 'DELIVERY',
      'external_order_code': code,
      'recipient_name': 'dtruyt',
      'delivery_address': '184 Nguyễn Văn Cừ, An Khánh, Ninh Kiều, Cần Thơ',
      'recipient_phone': '0901234567',
      'cod_amount': cod,
      'package_count': 1,
      'weight_kg': 1.0,
      'pickup_lat': 10.03,
      'pickup_lng': 105.78,
      'delivery_lat': 10.04,
      'delivery_lng': 105.77,
    });
  }

  testWidgets(
    'NextStopSpotlightCard renders active delivery without overflow on standard width',
    (tester) async {
      final order = createOrder();

      tester.view.physicalSize = const Size(390 * 3, 844 * 3);
      tester.view.devicePixelRatio = 3.0;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      await tester.pumpWidget(
        ProviderScope(
          child: MaterialApp(
            home: Scaffold(
              body: ListView(
                padding: const EdgeInsets.fromLTRB(16, 16, 16, 28),
                children: [
                  NextStopSpotlightCard(orders: [order]),
                ],
              ),
            ),
          ),
        ),
      );

      expect(tester.takeException(), isNull);
      expect(find.text('dtruyt'), findsOneWidget);
    },
  );

  testWidgets(
    'NextStopSpotlightCard renders without overflow on narrow 320px screen with large COD',
    (tester) async {
      final order = createOrder(
        code: 'LONG-CODE-999999',
        cod: 2500000,
      );

      tester.view.physicalSize = const Size(320 * 2, 568 * 2);
      tester.view.devicePixelRatio = 2.0;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      await tester.pumpWidget(
        ProviderScope(
          child: MaterialApp(
            home: Scaffold(
              body: ListView(
                padding: const EdgeInsets.fromLTRB(16, 16, 16, 28),
                children: [
                  NextStopSpotlightCard(orders: [order]),
                ],
              ),
            ),
          ),
        ),
      );

      expect(tester.takeException(), isNull);
      expect(find.text('dtruyt'), findsOneWidget);
    },
  );

  testWidgets(
    'NextStopSpotlightCard renders return-to-sender task without overflow',
    (tester) async {
      final order = createOrder(
        isReturn: true,
        status: 'OUT_FOR_DELIVERY',
      );

      tester.view.physicalSize = const Size(375 * 3, 812 * 3);
      tester.view.devicePixelRatio = 3.0;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      await tester.pumpWidget(
        ProviderScope(
          child: MaterialApp(
            home: Scaffold(
              body: ListView(
                padding: const EdgeInsets.fromLTRB(16, 16, 16, 28),
                children: [
                  NextStopSpotlightCard(orders: [order]),
                ],
              ),
            ),
          ),
        ),
      );

      expect(tester.takeException(), isNull);
      expect(find.text('Hoàn hàng về kho'), findsOneWidget);
    },
  );
}
