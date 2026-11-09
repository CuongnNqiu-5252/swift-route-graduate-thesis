T? _value<T>(Map<String, dynamic> json, String snake, String camel) {
  final value = json[snake] ?? json[camel];
  return value is T ? value : null;
}

double _number(Map<String, dynamic> json, String snake, String camel) {
  final value = json[snake] ?? json[camel];
  if (value == null) return 0.0;
  if (value is num) return value.toDouble();
  if (value is String) return double.tryParse(value) ?? 0.0;
  return 0.0;
}

double? _optionalNumber(Map<String, dynamic> json, String snake, String camel) {
  final value = json[snake] ?? json[camel];
  if (value == null) return null;
  if (value is num) return value.toDouble();
  if (value is String) return double.tryParse(value);
  return null;
}

class DriverCodCollectionModel {
  const DriverCodCollectionModel({
    required this.id,
    required this.orderId,
    required this.orderCode,
    required this.expectedAmount,
    required this.amount,
    required this.outcome,
    required this.status,
    this.reason,
  });
  final String id;
  final String orderId;
  final String? orderCode;
  final double expectedAmount;
  final double amount;
  final String outcome;
  final String status;
  final String? reason;

  factory DriverCodCollectionModel.fromJson(Map<String, dynamic> json) =>
      DriverCodCollectionModel(
        id:
            _value<String>(json, 'id', 'id') ??
            _value<String>(json, 'collection_id', 'collectionId') ??
            '',
        orderId: _value<String>(json, 'order_id', 'orderId') ?? '',
        orderCode: _value<String>(json, 'order_code', 'orderCode'),
        expectedAmount: _number(json, 'expected_amount', 'expectedAmount'),
        amount: _number(json, 'amount', 'amount'),
        outcome: _value<String>(json, 'outcome', 'outcome') ?? 'FULL',
        status: _value<String>(json, 'status', 'status') ?? 'IN_CUSTODY',
        reason: _value<String>(json, 'reason', 'reason'),
      );
}

class DriverCodHandoverModel {
  const DriverCodHandoverModel({
    required this.id,
    required this.status,
    required this.ledgerTotal,
    required this.declaredTotal,
    required this.countedTotal,
    required this.discrepancyAmount,
    required this.submittedAt,
    required this.lines,
  });
  final String id;
  final String status;
  final double ledgerTotal;
  final double declaredTotal;
  final double? countedTotal;
  final double? discrepancyAmount;
  final DateTime? submittedAt;
  final List<DriverCodCollectionModel> lines;

  factory DriverCodHandoverModel.fromJson(Map<String, dynamic> json) {
    final rawLines = json['lines'] as List? ?? const [];
    return DriverCodHandoverModel(
      id: _value<String>(json, 'id', 'id') ?? '',
      status: _value<String>(json, 'status', 'status') ?? 'SUBMITTED',
      ledgerTotal: _number(json, 'ledger_total', 'ledgerTotal'),
      declaredTotal: _number(json, 'declared_total', 'declaredTotal'),
      countedTotal: _optionalNumber(json, 'counted_total', 'countedTotal'),
      discrepancyAmount:
          _optionalNumber(json, 'discrepancy_amount', 'discrepancyAmount'),
      submittedAt: DateTime.tryParse(
        _value<String>(json, 'submitted_at', 'submittedAt') ?? '',
      ),
      lines: rawLines.whereType<Map>().map((raw) {
        final line = Map<String, dynamic>.from(raw);
        final amount = _number(line, 'amount', 'amount');
        return DriverCodCollectionModel(
          id: _value<String>(line, 'collection_id', 'collectionId') ?? '',
          orderId: _value<String>(line, 'order_id', 'orderId') ?? '',
          orderCode: _value<String>(line, 'order_code', 'orderCode'),
          expectedAmount: amount,
          amount: amount,
          outcome: _value<String>(line, 'outcome', 'outcome') ?? 'FULL',
          status: 'LOCKED_HANDOVER',
        );
      }).toList(),
    );
  }
}

class DriverCodCustodyModel {
  const DriverCodCustodyModel({
    required this.availableTotal,
    required this.lockedTotal,
    required this.handedOverTotal,
    required this.openDiscrepancyAmount,
    required this.eligibleCollections,
    required this.recentHandovers,
  });
  final double availableTotal;
  final double lockedTotal;
  final double handedOverTotal;
  final double openDiscrepancyAmount;
  final List<DriverCodCollectionModel> eligibleCollections;
  final List<DriverCodHandoverModel> recentHandovers;

  factory DriverCodCustodyModel.fromJson(Map<String, dynamic> json) {
    final rawCollections = (json['eligible_collections'] ??
            json['eligibleCollections']) as List? ??
        const [];
    final rawHandovers = (json['recent_handovers'] ??
            json['recentHandovers']) as List? ??
        const [];

    return DriverCodCustodyModel(
      availableTotal: _number(json, 'available_total', 'availableTotal'),
      lockedTotal: _number(json, 'locked_total', 'lockedTotal'),
      handedOverTotal: _number(json, 'handed_over_total', 'handedOverTotal'),
      openDiscrepancyAmount: _number(
        json,
        'open_discrepancy_amount',
        'openDiscrepancyAmount',
      ),
      eligibleCollections: rawCollections
          .whereType<Map>()
          .map(
            (raw) => DriverCodCollectionModel.fromJson(
              Map<String, dynamic>.from(raw),
            ),
          )
          .toList(),
      recentHandovers: rawHandovers
          .whereType<Map>()
          .map(
            (raw) => DriverCodHandoverModel.fromJson(
              Map<String, dynamic>.from(raw),
            ),
          )
          .toList(),
    );
  }
}
