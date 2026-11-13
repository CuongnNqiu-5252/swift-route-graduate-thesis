import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:swiftroute_app/core/offline/offline_action.dart';
import 'package:swiftroute_app/core/offline/offline_store.dart';
import 'package:swiftroute_app/core/offline/offline_sync_controller.dart';
import 'package:swiftroute_app/core/offline/offline_sync_service.dart';

class _MemoryStore implements OfflineStore {
  final Map<String, OfflineAction> actions = {};
  final Map<String, String> cache = {};

  @override
  Future<void> putAction(OfflineAction action) async =>
      actions[action.id] = action;

  @override
  Future<List<OfflineAction>> pendingActions() async => actions.values
      .where((action) => action.status == OfflineActionStatus.pending)
      .toList();

  @override
  Future<List<OfflineAction>> conflictedActions() async => actions.values
      .where((action) => action.status == OfflineActionStatus.conflict)
      .toList();

  @override
  Future<void> updateAction(OfflineAction action) async =>
      actions[action.id] = action;

  @override
  Future<void> putCache(String key, String value) async => cache[key] = value;

  @override
  Future<String?> getCache(String key) async => cache[key];

  @override
  Future<void> clearAll() async {
    actions.clear();
    cache.clear();
  }
}

void main() {
  test(
    'queued action survives coordinator recreation and is replayed once',
    () async {
      final store = _MemoryStore();
      final action = OfflineAction.create(
        type: OfflineActionType.deliveryTaskUpdate,
        method: 'PATCH',
        path: '/delivery-tasks/order/o-1',
        entityId: 'o-1',
        body: const {'status': 'COMPLETED'},
      );
      await store.putAction(action);

      var executions = 0;
      final recreated = OfflineSyncService(
        store: store,
        executor: (queued) async {
          executions++;
          return const OfflineExecutionResult.success();
        },
      );

      final result = await recreated.synchronize();

      expect(executions, 1);
      expect(result.synced, 1);
      expect(store.actions[action.id]!.status, OfflineActionStatus.synced);
    },
  );

  test(
    'server conflict is retained for operator resolution and not retried',
    () async {
      final store = _MemoryStore();
      final action = OfflineAction.create(
        type: OfflineActionType.deliveryTaskUpdate,
        method: 'PATCH',
        path: '/delivery-tasks/order/o-2',
        entityId: 'o-2',
        body: const {'status': 'COMPLETED'},
      );
      await store.putAction(action);
      final service = OfflineSyncService(
        store: store,
        executor: (_) async =>
            const OfflineExecutionResult.conflict('Đơn đã bị huỷ trên máy chủ'),
      );

      final first = await service.synchronize();
      final second = await service.synchronize();

      expect(first.conflicts, 1);
      expect(second.attempted, 0);
      expect(store.actions[action.id]!.status, OfflineActionStatus.conflict);
      expect(store.actions[action.id]!.lastError, contains('huỷ'));
    },
  );

  test('accepting server state resolves only the selected conflict', () async {
    final store = _MemoryStore();
    final selected =
        OfflineAction.create(
          type: OfflineActionType.deliveryTaskUpdate,
          method: 'PATCH',
          path: '/delivery-tasks/order/o-3',
          entityId: 'o-3',
          body: const {'status': 'COMPLETED'},
        ).copyWith(
          status: OfflineActionStatus.conflict,
          lastError: 'Đơn đã bị hủy trên máy chủ',
        );
    final remaining =
        OfflineAction.create(
          type: OfflineActionType.deliveryTaskUpdate,
          method: 'PATCH',
          path: '/delivery-tasks/order/o-4',
          entityId: 'o-4',
          body: const {'status': 'FAILED'},
        ).copyWith(
          status: OfflineActionStatus.conflict,
          lastError: 'Đơn đã được giao trên máy chủ',
        );
    await store.putAction(selected);
    await store.putAction(remaining);
    final controller = OfflineSyncController(store: store, dio: Dio());

    await controller.acceptServerState(selected.id);

    expect(store.actions[selected.id]!.status, OfflineActionStatus.resolved);
    expect(store.actions[remaining.id]!.status, OfflineActionStatus.conflict);
    expect(controller.state.conflicts, 1);
  });

  test('offline escalation queues an incident and resolves original', () async {
    final store = _MemoryStore();
    final conflict =
        OfflineAction.create(
          type: OfflineActionType.deliveryTaskUpdate,
          method: 'PATCH',
          path: '/delivery-tasks/order/o-5',
          entityId: 'o-5',
          body: const {'status': 'COMPLETED'},
        ).copyWith(
          status: OfflineActionStatus.conflict,
          lastError: 'Đơn đã bị hủy trên máy chủ',
        );
    await store.putAction(conflict);
    final controller = OfflineSyncController(
      store: store,
      dio: Dio(),
      incidentReporter: (_) async => throw DioException(
        requestOptions: RequestOptions(path: '/operations/incidents'),
        type: DioExceptionType.connectionError,
      ),
    );

    final queued = await controller.escalateConflict(
      conflict.id,
      driverNote: 'Khách đã nhận hàng',
    );

    expect(queued, isTrue);
    expect(store.actions[conflict.id]!.status, OfflineActionStatus.resolved);
    final pending = await store.pendingActions();
    expect(pending, hasLength(1));
    expect(pending.single.type, OfflineActionType.incident);
    expect(pending.single.body['incident_type'], 'OFFLINE_SYNC_CONFLICT');
    expect(pending.single.body['notes'], contains('Khách đã nhận hàng'));
    expect(controller.state.conflicts, 0);
    expect(controller.state.pending, 1);
  });
}
