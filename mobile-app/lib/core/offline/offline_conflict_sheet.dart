import 'package:flutter/material.dart';

import 'offline_action.dart';

class OfflineConflictSheet extends StatefulWidget {
  const OfflineConflictSheet({
    super.key,
    required this.conflicts,
    required this.onAcceptServerState,
    required this.onEscalate,
  });

  final List<OfflineAction> conflicts;
  final Future<void> Function(String actionId) onAcceptServerState;
  final Future<bool> Function(String actionId, String? note) onEscalate;

  @override
  State<OfflineConflictSheet> createState() => _OfflineConflictSheetState();
}

class _OfflineConflictSheetState extends State<OfflineConflictSheet> {
  late final List<OfflineAction> _conflicts = [...widget.conflicts];
  final Set<String> _resolving = {};

  Future<void> _acceptServerState(OfflineAction action) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Dùng trạng thái trên máy chủ?'),
        content: const Text(
          'Thao tác đã ghi trên điện thoại sẽ được đóng lại và không gửi lại. '
          'Chỉ chọn khi trạng thái trên máy chủ phản ánh đúng thực tế.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: const Text('Quay lại'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(dialogContext, true),
            child: const Text('Xác nhận'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    await _runResolution(
      action,
      () => widget.onAcceptServerState(action.id),
      successMessage: () => 'Đã dùng trạng thái trên máy chủ',
    );
  }

  Future<void> _reportToDispatch(OfflineAction action) async {
    final note = await showDialog<String>(
      context: context,
      builder: (_) => const _ConflictReportDialog(),
    );
    if (note == null || !mounted) return;

    var queued = false;
    await _runResolution(
      action,
      () async {
        queued = await widget.onEscalate(action.id, note);
      },
      successMessage: () => queued
          ? 'Đã lưu báo cáo, sẽ gửi khi có mạng'
          : 'Đã chuyển cho điều phối viên',
    );
  }

  Future<void> _runResolution(
    OfflineAction action,
    Future<void> Function() operation, {
    required String Function() successMessage,
  }) async {
    if (_resolving.contains(action.id)) return;
    setState(() => _resolving.add(action.id));
    try {
      await operation();
      if (!mounted) return;
      setState(() => _conflicts.removeWhere((item) => item.id == action.id));
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(successMessage())));
      if (_conflicts.isEmpty) Navigator.pop(context);
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text(
            'Chưa thể xử lý xung đột. Kiểm tra kết nối rồi thử lại.',
          ),
        ),
      );
    } finally {
      if (mounted) setState(() => _resolving.remove(action.id));
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return SafeArea(
      top: false,
      child: Padding(
        padding: EdgeInsets.fromLTRB(
          20,
          12,
          20,
          20 + MediaQuery.viewInsetsOf(context).bottom,
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Center(
              child: Container(
                width: 40,
                height: 4,
                decoration: BoxDecoration(
                  color: theme.colorScheme.outlineVariant,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
            const SizedBox(height: 20),
            Text('Cần xác nhận đồng bộ', style: theme.textTheme.headlineSmall),
            const SizedBox(height: 8),
            Text(
              '${_conflicts.length} thao tác trên điện thoại khác với dữ liệu máy chủ. '
              'Ứng dụng sẽ không tự ghi đè.',
              style: theme.textTheme.bodyMedium?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
            const SizedBox(height: 16),
            Flexible(
              child: ListView.separated(
                shrinkWrap: true,
                itemCount: _conflicts.length,
                separatorBuilder: (_, _) => const SizedBox(height: 12),
                itemBuilder: (context, index) =>
                    _conflictItem(context, _conflicts[index]),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _conflictItem(BuildContext context, OfflineAction action) {
    final theme = Theme.of(context);
    final resolving = _resolving.contains(action.id);
    return Semantics(
      container: true,
      label: '${_actionLabel(action)}. ${action.lastError ?? ''}',
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: theme.colorScheme.surfaceContainerLow,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: theme.colorScheme.outlineVariant),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Icon(
                  Icons.sync_problem_rounded,
                  color: theme.colorScheme.error,
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        _actionLabel(action),
                        style: theme.textTheme.titleMedium,
                      ),
                      const SizedBox(height: 4),
                      Text(
                        action.lastError ?? 'Dữ liệu máy chủ đã thay đổi',
                        style: theme.textTheme.bodyMedium?.copyWith(
                          color: theme.colorScheme.onSurfaceVariant,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        _timeLabel(action.createdAt),
                        style: theme.textTheme.bodySmall,
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            OutlinedButton(
              onPressed: resolving ? null : () => _acceptServerState(action),
              style: OutlinedButton.styleFrom(
                minimumSize: const Size.fromHeight(48),
              ),
              child: const Text('Dùng trạng thái máy chủ'),
            ),
            const SizedBox(height: 8),
            FilledButton.tonalIcon(
              onPressed: resolving ? null : () => _reportToDispatch(action),
              style: FilledButton.styleFrom(
                minimumSize: const Size.fromHeight(48),
              ),
              icon: resolving
                  ? const SizedBox.square(
                      dimension: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.support_agent_rounded),
              label: const Text('Báo điều phối'),
            ),
          ],
        ),
      ),
    );
  }

  String _actionLabel(OfflineAction action) => switch (action.type) {
    OfflineActionType.deliveryTaskUpdate => switch (action.body['status']
        ?.toString()
        .toUpperCase()) {
      'COMPLETED' => 'Đã ghi nhận giao thành công',
      'FAILED' => 'Đã ghi nhận giao thất bại',
      'OUT_FOR_DELIVERY' => 'Đã ghi nhận bắt đầu giao',
      _ => 'Cập nhật trạng thái giao hàng',
    },
    OfflineActionType.codCollection => 'Ghi nhận thu COD',
    OfflineActionType.codHandover => 'Bàn giao COD',
    OfflineActionType.routeAcknowledgement => 'Xác nhận nhận tuyến',
    OfflineActionType.routeDecline => 'Từ chối tuyến',
    OfflineActionType.packageHandover => 'Bàn giao kiện hàng',
    OfflineActionType.returnProgress => 'Cập nhật hoàn hàng',
    OfflineActionType.incident => 'Báo sự cố',
    OfflineActionType.locationUpdate => 'Cập nhật vị trí',
  };

  String _timeLabel(DateTime value) {
    final local = value.toLocal();
    final hour = local.hour.toString().padLeft(2, '0');
    final minute = local.minute.toString().padLeft(2, '0');
    return 'Ghi nhận lúc $hour:$minute, ${local.day}/${local.month}/${local.year}';
  }
}

class _ConflictReportDialog extends StatefulWidget {
  const _ConflictReportDialog();

  @override
  State<_ConflictReportDialog> createState() => _ConflictReportDialogState();
}

class _ConflictReportDialogState extends State<_ConflictReportDialog> {
  final _noteController = TextEditingController();

  @override
  void dispose() {
    _noteController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: const Text('Báo điều phối'),
    content: SingleChildScrollView(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'Dùng khi thực tế ngoài hiện trường khác trạng thái máy chủ. '
            'Hệ thống sẽ tạo incident để điều phối viên kiểm tra.',
          ),
          const SizedBox(height: 16),
          TextField(
            controller: _noteController,
            maxLength: 500,
            maxLines: 3,
            textCapitalization: TextCapitalization.sentences,
            decoration: const InputDecoration(
              labelText: 'Ghi chú cho điều phối',
              hintText: 'Ví dụ: Khách đã nhận hàng lúc 14:30',
              alignLabelWithHint: true,
            ),
          ),
        ],
      ),
    ),
    actions: [
      TextButton(
        onPressed: () => Navigator.pop(context),
        child: const Text('Hủy'),
      ),
      FilledButton.icon(
        onPressed: () => Navigator.pop(context, _noteController.text.trim()),
        icon: const Icon(Icons.campaign_outlined),
        label: const Text('Gửi báo cáo'),
      ),
    ],
  );
}
