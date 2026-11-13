import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:path/path.dart' as path;
import 'package:sqflite/sqflite.dart';

import 'offline_action.dart';
import 'offline_store.dart';

class SqliteOfflineStore implements OfflineStore {
  SqliteOfflineStore._();

  static final SqliteOfflineStore instance = SqliteOfflineStore._();
  Database? _database;
  bool _useMemoryFallback = false;
  final Map<String, OfflineAction> _memoryActions = {};
  final Map<String, String> _memoryCache = {};

  bool get _platformSupportsSqflite =>
      !kIsWeb &&
      (defaultTargetPlatform == TargetPlatform.android ||
          defaultTargetPlatform == TargetPlatform.iOS);

  Future<Database?> _getDbOrNull() async {
    if (_useMemoryFallback || !_platformSupportsSqflite) {
      _useMemoryFallback = true;
      return null;
    }
    final existing = _database;
    if (existing != null) return existing;
    try {
      final root = await getDatabasesPath();
      return _database = await openDatabase(
        path.join(root, 'swiftroute_offline.db'),
        version: 1,
        onCreate: (db, _) async {
          await db.execute('''
            CREATE TABLE offline_actions (
              id TEXT PRIMARY KEY,
              type TEXT NOT NULL,
              method TEXT NOT NULL,
              path TEXT NOT NULL,
              entity_id TEXT,
              body_json TEXT NOT NULL,
              created_at TEXT NOT NULL,
              status TEXT NOT NULL,
              attempts INTEGER NOT NULL DEFAULT 0,
              last_error TEXT
            )
          ''');
          await db.execute('''
            CREATE INDEX idx_offline_actions_status_created
            ON offline_actions(status, created_at)
          ''');
          await db.execute('''
            CREATE TABLE offline_cache (
              cache_key TEXT PRIMARY KEY,
              value_json TEXT NOT NULL,
              updated_at TEXT NOT NULL
            )
          ''');
        },
      );
    } on MissingPluginException {
      _useMemoryFallback = true;
      return null;
    } on StateError {
      _useMemoryFallback = true;
      return null;
    } on UnsupportedError {
      _useMemoryFallback = true;
      return null;
    }
  }

  @override
  Future<void> putAction(OfflineAction action) async {
    final db = await _getDbOrNull();
    if (db == null) {
      if (action.type == OfflineActionType.locationUpdate) {
        _memoryActions.removeWhere(
          (_, existing) =>
              existing.type == OfflineActionType.locationUpdate &&
              existing.status == OfflineActionStatus.pending,
        );
      }
      _memoryActions.putIfAbsent(action.id, () => action);
      return;
    }
    if (action.type == OfflineActionType.locationUpdate) {
      await db.delete(
        'offline_actions',
        where: 'type = ? AND status = ?',
        whereArgs: [
          OfflineActionType.locationUpdate.name,
          OfflineActionStatus.pending.name,
        ],
      );
    }
    await db.insert(
      'offline_actions',
      action.toRow(),
      conflictAlgorithm: ConflictAlgorithm.ignore,
    );
  }

  @override
  Future<List<OfflineAction>> pendingActions() async {
    final db = await _getDbOrNull();
    if (db == null) {
      final list = _memoryActions.values
          .where((action) => action.status == OfflineActionStatus.pending)
          .toList()
        ..sort((a, b) => a.createdAt.compareTo(b.createdAt));
      return list;
    }
    final rows = await db.query(
      'offline_actions',
      where: 'status = ?',
      whereArgs: [OfflineActionStatus.pending.name],
      orderBy: 'created_at ASC',
    );
    return rows.map(OfflineAction.fromRow).toList();
  }

  @override
  Future<List<OfflineAction>> conflictedActions() async {
    final db = await _getDbOrNull();
    if (db == null) {
      final list = _memoryActions.values
          .where((action) => action.status == OfflineActionStatus.conflict)
          .toList()
        ..sort((a, b) => a.createdAt.compareTo(b.createdAt));
      return list;
    }
    final rows = await db.query(
      'offline_actions',
      where: 'status = ?',
      whereArgs: [OfflineActionStatus.conflict.name],
      orderBy: 'created_at ASC',
    );
    return rows.map(OfflineAction.fromRow).toList();
  }

  @override
  Future<void> updateAction(OfflineAction action) async {
    final db = await _getDbOrNull();
    if (db == null) {
      _memoryActions[action.id] = action;
      return;
    }
    await db.update(
      'offline_actions',
      action.toRow(),
      where: 'id = ?',
      whereArgs: [action.id],
    );
  }

  @override
  Future<void> putCache(String key, String value) async {
    final db = await _getDbOrNull();
    if (db == null) {
      _memoryCache[key] = value;
      return;
    }
    await db.insert('offline_cache', {
      'cache_key': key,
      'value_json': value,
      'updated_at': DateTime.now().toUtc().toIso8601String(),
    }, conflictAlgorithm: ConflictAlgorithm.replace);
  }

  @override
  Future<String?> getCache(String key) async {
    final db = await _getDbOrNull();
    if (db == null) {
      return _memoryCache[key];
    }
    final rows = await db.query(
      'offline_cache',
      columns: ['value_json'],
      where: 'cache_key = ?',
      whereArgs: [key],
      limit: 1,
    );
    return rows.isEmpty ? null : rows.first['value_json'] as String;
  }

  @override
  Future<void> clearAll() async {
    final db = await _getDbOrNull();
    if (db == null) {
      _memoryActions.clear();
      _memoryCache.clear();
      return;
    }
    await db.transaction((transaction) async {
      await transaction.delete('offline_actions');
      await transaction.delete('offline_cache');
    });
  }
}
