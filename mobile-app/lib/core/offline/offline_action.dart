import 'dart:convert';
import 'dart:math';

enum OfflineActionType {
  deliveryTaskUpdate,
  routeAcknowledgement,
  routeDecline,
  codCollection,
  codHandover,
  incident,
  packageHandover,
  returnProgress,
  locationUpdate,
}

enum OfflineActionStatus {
  pending,
  syncing,
  synced,
  conflict,
  resolved,
  failed,
}

class OfflineActionQueuedException implements Exception {
  const OfflineActionQueuedException(this.message);
  final String message;

  @override
  String toString() => message;
}

class OfflineAction {
  const OfflineAction({
    required this.id,
    required this.type,
    required this.method,
    required this.path,
    required this.body,
    required this.createdAt,
    this.entityId,
    this.status = OfflineActionStatus.pending,
    this.attempts = 0,
    this.lastError,
  });

  final String id;
  final OfflineActionType type;
  final String method;
  final String path;
  final String? entityId;
  final Map<String, dynamic> body;
  final DateTime createdAt;
  final OfflineActionStatus status;
  final int attempts;
  final String? lastError;

  factory OfflineAction.create({
    required OfflineActionType type,
    required String method,
    required String path,
    required Map<String, dynamic> body,
    String? entityId,
  }) => OfflineAction(
    id: _operationId(),
    type: type,
    method: method,
    path: path,
    entityId: entityId,
    body: Map<String, dynamic>.unmodifiable(body),
    createdAt: DateTime.now().toUtc(),
  );

  OfflineAction copyWith({
    OfflineActionStatus? status,
    int? attempts,
    String? lastError,
    bool clearError = false,
  }) => OfflineAction(
    id: id,
    type: type,
    method: method,
    path: path,
    entityId: entityId,
    body: body,
    createdAt: createdAt,
    status: status ?? this.status,
    attempts: attempts ?? this.attempts,
    lastError: clearError ? null : lastError ?? this.lastError,
  );

  Map<String, Object?> toRow() => {
    'id': id,
    'type': type.name,
    'method': method,
    'path': path,
    'entity_id': entityId,
    'body_json': jsonEncode(body),
    'created_at': createdAt.toIso8601String(),
    'status': status.name,
    'attempts': attempts,
    'last_error': lastError,
  };

  factory OfflineAction.fromRow(Map<String, Object?> row) => OfflineAction(
    id: row['id']! as String,
    type: OfflineActionType.values.byName(row['type']! as String),
    method: row['method']! as String,
    path: row['path']! as String,
    entityId: row['entity_id'] as String?,
    body: Map<String, dynamic>.from(
      jsonDecode(row['body_json']! as String) as Map,
    ),
    createdAt: DateTime.parse(row['created_at']! as String),
    status: OfflineActionStatus.values.byName(row['status']! as String),
    attempts: row['attempts']! as int,
    lastError: row['last_error'] as String?,
  );

  static String _operationId() {
    final random = Random.secure();
    final bytes = List<int>.generate(16, (_) => random.nextInt(256));
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    final hex = bytes
        .map((value) => value.toRadixString(16).padLeft(2, '0'))
        .join();
    return '${hex.substring(0, 8)}-${hex.substring(8, 12)}-'
        '${hex.substring(12, 16)}-${hex.substring(16, 20)}-'
        '${hex.substring(20)}';
  }
}
