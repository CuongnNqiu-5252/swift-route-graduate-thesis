import 'dart:convert';
import 'package:flutter_test/flutter_test.dart';
import 'package:swiftroute_app/features/driver/data/models/driver_cod_collection_model.dart';

void main() {
  test('parses DriverCodCustodyModel from real JSON', () {
    const rawJson = '''
{
  "driver_id": "fbc15367-76d5-41de-b051-c23f081fe9b4",
  "available_total": 177000.00,
  "locked_total": 0,
  "handed_over_total": 0,
  "open_discrepancy_amount": 0,
  "eligible_collections": [
    {
      "id": "e30af136-4498-4ffe-b557-92355e962c87",
      "payment_id": "b011c6a4-37c4-4a32-b02c-cf3bda8c3dc4",
      "order_id": "b5ceb3fe-7723-42d7-907c-9c648a96cec6",
      "order_code": null,
      "driver_id": "fbc15367-76d5-41de-b051-c23f081fe9b4",
      "expected_amount": 27000,
      "amount": 27000.00,
      "outcome": "FULL",
      "status": "IN_CUSTODY",
      "reason": null,
      "evidence_url": "https://res.cloudinary.com/dtciesamk/image/upload/v1794025249/swiftroute/evidence/evyu3cyocfubtkbnacci.png",
      "occurred_at": "2026-11-07T04:20:09.778039Z",
      "received_at": "2026-11-07T04:20:09.778056Z"
    },
    {
      "id": "f5896199-1b57-41b3-9618-d82f5d88590b",
      "payment_id": "d5704904-fc94-4aaf-80aa-1ed22f940202",
      "order_id": "8baa9cd3-5aa4-4365-89c5-9d4ba43cdb25",
      "order_code": null,
      "driver_id": "fbc15367-76d5-41de-b051-c23f081fe9b4",
      "expected_amount": 150000,
      "amount": 150000.00,
      "outcome": "FULL",
      "status": "IN_CUSTODY",
      "reason": null,
      "evidence_url": "https://res.cloudinary.com/dtciesamk/image/upload/v1794024147/swiftroute/evidence/tt9ohckkegpy3m1y2fsh.jpg",
      "occurred_at": "2026-11-07T04:17:58.341242Z",
      "received_at": "2026-11-07T04:17:58.341288Z"
    }
  ],
  "recent_handovers": []
}
''';
    final map = jsonDecode(rawJson) as Map<String, dynamic>;
    final model = DriverCodCustodyModel.fromJson(map);
    expect(model.availableTotal, 177000.00);
    expect(model.eligibleCollections.length, 2);
    expect(model.eligibleCollections[0].expectedAmount, 27000.0);
    expect(model.eligibleCollections[0].amount, 27000.0);
    expect(model.eligibleCollections[0].orderCode, null);
  });
}

