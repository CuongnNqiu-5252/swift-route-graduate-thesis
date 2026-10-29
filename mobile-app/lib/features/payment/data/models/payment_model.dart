class PaymentModel {
  const PaymentModel({
    required this.id,
    required this.orderId,
    required this.amount,
    required this.status,
    required this.txnRef,
  });

  final String id;
  final String orderId;
  final double amount;
  final String status;
  final String txnRef;

  factory PaymentModel.fromJson(Map<String, dynamic> json) => PaymentModel(
        id: json['payment_id'] as String? ?? '',
        orderId: json['order_id'] as String? ?? '',
        amount: (json['amount'] as num?)?.toDouble() ?? 0,
        status: json['status'] as String? ?? 'PENDING',
        txnRef: json['txn_ref'] as String? ?? '',
      );
}
