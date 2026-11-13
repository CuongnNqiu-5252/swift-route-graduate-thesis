import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/network/dio.dart';

import 'offline_action.dart';
import 'offline_store.dart';
import 'offline_sync_service.dart';
import 'sqlite_offline_store.dart';

typedef ConflictIncidentReporter =
    Future<void> Function(Map<String, dynamic> payload);

class OfflineSyncState {
  const OfflineSyncState({
    this.pending = 0,
    this.conflicts = 0,
    this.isSyncing = false,
    this.lastSyncedAt,
    this.lastError,
  });

  final int pending;
  final int conflicts;
  final bool isSyncing;
  final DateTime? lastSyncedAt;
  final String? lastError;

  bool get hasAttention => pending > 0 || conflicts > 0 || lastError != null;

  OfflineSyncState copyWith({
    int? pending,
    int? conflicts,
    bool? isSyncing,
    DateTime? lastSyncedAt,
    String? lastError,
    bool clearError = false,
  }) => OfflineSyncState(
    pending: pending ?? this.pending,
    conflicts: conflicts ?? this.conflicts,
    isSyncing: isSyncing ?? this.isSyncing,
    lastSyncedAt: lastSyncedAt ?? this.lastSyncedAt,
    lastError: clearError ? null : lastError ?? this.lastError,
  );
}

class OfflineSyncController extends StateNotifier<OfflineSyncState> {
  OfflineSyncController({
    required OfflineStore store,
    required Dio dio,
    ConflictIncidentReporter? incidentReporter,
  }) : _store = store,
       _dio = dio,
       _incidentReporter = incidentReporter,
       super(const OfflineSyncState()) {
    _service = OfflineSyncService(store: store, executor: _execute);
    unawaited(refreshStatus());
  }

  final OfflineStore _store;
  final Dio _dio;
  final ConflictIncidentReporter? _incidentReporter;
  late final OfflineSyncService _service;

  Future<void> queue(OfflineAction action) async {
    await _store.putAction(action);
    await refreshStatus();
  }

  Future<void> putCache(String key, String json) => _store.putCache(key, json);

  Future<String?> getCache(String key) => _store.getCache(key);

  Future<void> refreshStatus() async {
    final pending = await _store.pendingActions();
    final conflicts = await _store.conflictedActions();
    state = state.copyWith(
      pending: pending.length,
      conflicts: conflicts.length,
    );
  }

  Future<List<OfflineAction>> conflictedActions() => _store.conflictedActions();

  Future<void> acceptServerState(String actionId) async {
    final action = await _findConflict(actionId);
    await _store.updateAction(
      action.copyWith(
        status: OfflineActionStatus.resolved,
        lastError: 'Tài xế đã chấp nhận trạng thái trên máy chủ',
      ),
    );
    await refreshStatus();
  }

  Future<bool> escalateConflict(String actionId, {String? driverNote}) async {
    final action = await _findConflict(actionId);
    final payload = _incidentPayload(action, driverNote: driverNote);
    var queued = false;
    try {
      final reporter = _incidentReporter;
      if (reporter != null) {
        await reporter(payload);
      } else {
        await _dio.post<void>('/operations/incidents', data: payload);
      }
    } on DioException catch (error) {
      if (!_isTransient(error)) rethrow;
      queued = true;
      await _store.putAction(
        OfflineAction.create(
          type: OfflineActionType.incident,
          method: 'POST',
          path: '/operations/incidents',
          entityId: action.entityId,
          body: payload,
        ),
      );
    }
    await _store.updateAction(
      action.copyWith(
        status: OfflineActionStatus.resolved,
        lastError: queued
            ? 'Đã xếp hàng báo điều phối về xung đột'
            : 'Đã báo điều phối về xung đột',
      ),
    );
    await refreshStatus();
    return queued;
  }

  Future<OfflineSyncSummary> synchronize() async {
    state = state.copyWith(isSyncing: true, clearError: true);
    try {
      final summary = await _service.synchronize();
      state = state.copyWith(
        isSyncing: false,
        pending: summary.remaining,
        conflicts: state.conflicts + summary.conflicts,
        lastSyncedAt: DateTime.now(),
      );
      return summary;
    } catch (error) {
      state = state.copyWith(isSyncing: false, lastError: error.toString());
      rethrow;
    }
  }

  Future<OfflineExecutionResult> _execute(OfflineAction action) async {
    try {
      final conflict = await _preflightConflict(action);
      if (conflict != null) return OfflineExecutionResult.conflict(conflict);
      await _dio.request<void>(
        action.path,
        data: action.body.isEmpty ? null : action.body,
        options: Options(
          method: action.method,
          headers: {'Idempotency-Key': action.id, 'X-Operation-Id': action.id},
        ),
      );
      return const OfflineExecutionResult.success();
    } on DioException catch (error) {
      final code = error.response?.statusCode;
      if (code == 409) {
        return OfflineExecutionResult.conflict(
          _serverMessage(error) ?? 'Dữ liệu trên máy chủ đã thay đổi',
        );
      }
      if (code == null || code >= 500 || code == 408 || code == 429) {
        return OfflineExecutionResult.retry(
          _serverMessage(error) ?? 'Chưa thể kết nối máy chủ',
        );
      }
      return OfflineExecutionResult.failed(
        _serverMessage(error) ?? 'Máy chủ từ chối thao tác ($code)',
      );
    } catch (error) {
      return OfflineExecutionResult.retry(error.toString());
    }
  }

  Future<String?> _preflightConflict(OfflineAction action) async {
    if (action.type != OfflineActionType.deliveryTaskUpdate ||
        action.entityId == null) {
      return null;
    }
    final response = await _dio.get('/orders/${action.entityId}');
    final raw = response.data;
    final data = raw is Map && raw['data'] is Map
        ? Map<String, dynamic>.from(raw['data'] as Map)
        : raw is Map
        ? Map<String, dynamic>.from(raw)
        : const <String, dynamic>{};
    final serverStatus = data['status']?.toString().toUpperCase();
    final requestedTaskStatus = action.body['status']?.toString().toUpperCase();
    if (serverStatus == 'DELIVERED' && requestedTaskStatus == 'COMPLETED') {
      return null;
    }
    if (serverStatus == 'CANCELLED') return 'Đơn đã bị hủy trên máy chủ';
    if (serverStatus == 'DELIVERED') {
      return 'Đơn đã được ghi nhận giao thành công trên máy chủ';
    }
    if (serverStatus == 'FAILED') {
      return 'Đơn đã được ghi nhận giao thất bại trên máy chủ';
    }
    return null;
  }

  Future<OfflineAction> _findConflict(String actionId) async {
    final conflicts = await _store.conflictedActions();
    return conflicts.firstWhere(
      (action) => action.id == actionId,
      orElse: () => throw StateError('Xung đột không còn tồn tại'),
    );
  }

  Map<String, dynamic> _incidentPayload(
    OfflineAction action, {
    String? driverNote,
  }) {
    final isRouteAction =
        action.type == OfflineActionType.routeAcknowledgement ||
        action.type == OfflineActionType.routeDecline;
    final requestedStatus = action.body['status']?.toString();
    final detail = [
      'Đồng bộ thao tác ngoại tuyến bị xung đột.',
      if (action.lastError != null) 'Máy chủ: ${action.lastError}.',
      if (requestedStatus != null) 'Thao tác tài xế: $requestedStatus.',
      'Ghi nhận lúc: ${action.createdAt.toLocal().toIso8601String()}.',
      if (driverNote != null && driverNote.trim().isNotEmpty)
        'Ghi chú tài xế: ${driverNote.trim()}',
    ].join(' ');
    return <String, dynamic>{
      if (action.entityId != null && isRouteAction)
        'dispatch_plan_id': action.entityId,
      if (action.entityId != null && !isRouteAction)
        'order_id': action.entityId,
      'incident_type': 'OFFLINE_SYNC_CONFLICT',
      'severity': 'HIGH',
      'notes': detail,
      'can_continue_delivery': false,
      'needs_immediate_assistance': true,
      'contact_attempts': 1,
    };
  }

  bool _isTransient(DioException error) {
    final code = error.response?.statusCode;
    return code == null || code >= 500 || code == 408 || code == 429;
  }

  String? _serverMessage(DioException error) {
    final data = error.response?.data;
    if (data is Map) {
      return data['message']?.toString() ?? data['error']?.toString();
    }
    return null;
  }
}

final offlineStoreProvider = Provider<OfflineStore>(
  (_) => SqliteOfflineStore.instance,
);

final offlineSyncProvider =
    StateNotifierProvider<OfflineSyncController, OfflineSyncState>((ref) {
      return OfflineSyncController(
        store: ref.read(offlineStoreProvider),
        dio: ref.read(dioProvider),
      );
    });
