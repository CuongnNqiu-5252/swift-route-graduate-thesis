import 'offline_action.dart';

abstract interface class OfflineStore {
  Future<void> putAction(OfflineAction action);

  Future<List<OfflineAction>> pendingActions();

  Future<List<OfflineAction>> conflictedActions();

  Future<void> updateAction(OfflineAction action);

  Future<void> putCache(String key, String value);

  Future<String?> getCache(String key);

  Future<void> clearAll();
}
