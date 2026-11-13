import 'offline_action.dart';
import 'offline_store.dart';

enum OfflineExecutionDisposition { success, retry, conflict, failed }

class OfflineExecutionResult {
  const OfflineExecutionResult._(this.disposition, [this.message]);

  const OfflineExecutionResult.success()
    : this._(OfflineExecutionDisposition.success);
  const OfflineExecutionResult.retry(String message)
    : this._(OfflineExecutionDisposition.retry, message);
  const OfflineExecutionResult.conflict(String message)
    : this._(OfflineExecutionDisposition.conflict, message);
  const OfflineExecutionResult.failed(String message)
    : this._(OfflineExecutionDisposition.failed, message);

  final OfflineExecutionDisposition disposition;
  final String? message;
}

typedef OfflineActionExecutor =
    Future<OfflineExecutionResult> Function(OfflineAction action);

class OfflineSyncSummary {
  const OfflineSyncSummary({
    required this.attempted,
    required this.synced,
    required this.conflicts,
    required this.failed,
    required this.remaining,
  });

  final int attempted;
  final int synced;
  final int conflicts;
  final int failed;
  final int remaining;
}

class OfflineSyncService {
  OfflineSyncService({required this.store, required this.executor});

  final OfflineStore store;
  final OfflineActionExecutor executor;
  bool _running = false;

  Future<OfflineSyncSummary> synchronize() async {
    if (_running) {
      return const OfflineSyncSummary(
        attempted: 0,
        synced: 0,
        conflicts: 0,
        failed: 0,
        remaining: 0,
      );
    }
    _running = true;
    var attempted = 0;
    var synced = 0;
    var conflicts = 0;
    var failed = 0;
    try {
      final actions = await store.pendingActions();
      for (final action in actions) {
        attempted++;
        await store.updateAction(
          action.copyWith(
            status: OfflineActionStatus.syncing,
            attempts: action.attempts + 1,
            clearError: true,
          ),
        );
        final result = await executor(action);
        switch (result.disposition) {
          case OfflineExecutionDisposition.success:
            synced++;
            await store.updateAction(
              action.copyWith(
                status: OfflineActionStatus.synced,
                attempts: action.attempts + 1,
                clearError: true,
              ),
            );
          case OfflineExecutionDisposition.retry:
            await store.updateAction(
              action.copyWith(
                status: OfflineActionStatus.pending,
                attempts: action.attempts + 1,
                lastError: result.message,
              ),
            );
          case OfflineExecutionDisposition.conflict:
            conflicts++;
            await store.updateAction(
              action.copyWith(
                status: OfflineActionStatus.conflict,
                attempts: action.attempts + 1,
                lastError: result.message,
              ),
            );
          case OfflineExecutionDisposition.failed:
            failed++;
            await store.updateAction(
              action.copyWith(
                status: OfflineActionStatus.failed,
                attempts: action.attempts + 1,
                lastError: result.message,
              ),
            );
        }
      }
      final remaining = (await store.pendingActions()).length;
      return OfflineSyncSummary(
        attempted: attempted,
        synced: synced,
        conflicts: conflicts,
        failed: failed,
        remaining: remaining,
      );
    } finally {
      _running = false;
    }
  }
}
