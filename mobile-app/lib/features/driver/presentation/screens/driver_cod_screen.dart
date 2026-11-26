import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/theme/app_colors.dart';
import 'package:swiftroute_app/core/theme/app_theme.dart';
import 'package:swiftroute_app/core/utils/formatters.dart';
import 'package:swiftroute_app/core/widgets/field_ops_widgets.dart';
import 'package:swiftroute_app/features/driver/application/driver_provider.dart';
import 'package:swiftroute_app/features/driver/data/models/driver_cod_collection_model.dart';

class DriverCodScreen extends ConsumerWidget {
  const DriverCodScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(driverCodHandoverProvider);
    return Scaffold(
      appBar: AppBar(
        title: const Text('Nộp COD'),
        actions: [
          IconButton(
            tooltip: 'Làm mới',
            onPressed: ref.read(driverCodHandoverProvider.notifier).refresh,
            icon: const Icon(Icons.refresh_rounded),
          ),
        ],
      ),
      body: _CodList(state: state),
    );
  }
}

class _CodList extends ConsumerStatefulWidget {
  const _CodList({required this.state});

  final AsyncValue<DriverCodCustodyModel> state;

  @override
  ConsumerState<_CodList> createState() => _CodListState();
}

class _CodListState extends ConsumerState<_CodList> {
  final Set<String> _selectedIds = {};
  bool _submitting = false;

  Future<void> _submitReceipt(DriverCodCustodyModel custody) async {
    final selected = custody.eligibleCollections
        .where((item) => _selectedIds.contains(item.id))
        .toList();
    if (selected.isEmpty) return;
    final ledgerTotal = selected.fold<double>(
      0,
      (sum, item) => sum + item.amount,
    );
    final controller = TextEditingController(
      text: ledgerTotal.toStringAsFixed(0),
    );
    final notesController = TextEditingController();
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Tạo phiếu nộp COD'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              '${selected.length} đơn · Theo sổ ${AppFormatters.currency(ledgerTotal)}',
            ),
            const SizedBox(height: 16),
            TextField(
              controller: controller,
              keyboardType: TextInputType.number,
              decoration: const InputDecoration(
                labelText: 'Tổng tiền mặt thực nộp (VND)',
                prefixIcon: Icon(Icons.payments_rounded),
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: notesController,
              maxLines: 2,
              decoration: const InputDecoration(
                labelText: 'Ghi chú (không bắt buộc)',
              ),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: const Text('Hủy'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(dialogContext, true),
            child: const Text('Gửi phiếu'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) {
      controller.dispose();
      notesController.dispose();
      return;
    }
    final declaredTotal = double.tryParse(
      controller.text.replaceAll(RegExp(r'[^\d.]'), ''),
    );
    final notes = notesController.text.trim();
    controller.dispose();
    notesController.dispose();
    if (declaredTotal == null || declaredTotal < 0) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Tổng tiền kê khai không hợp lệ.')),
      );
      return;
    }
    setState(() => _submitting = true);
    try {
      await ref
          .read(driverCodHandoverProvider.notifier)
          .submit(
            collections: selected,
            declaredTotal: declaredTotal,
            notes: notes.isEmpty ? null : notes,
          );
      _selectedIds.clear();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            backgroundColor: AppColors.success,
            content: Text('Đã gửi phiếu nộp COD. Quầy sẽ kiểm đếm độc lập.'),
          ),
        );
      }
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Không gửi được phiếu nộp COD. Vui lòng thử lại.'),
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final notifier = ref.read(driverCodHandoverProvider.notifier);
    return widget.state.when(
      loading: () => ListView(
        padding: const EdgeInsets.all(16),
        children: const [
          ShimmerBox(height: 96),
          SizedBox(height: 12),
          ShimmerBox(height: 140),
        ],
      ),
      error: (_, _) => ListView(
        padding: const EdgeInsets.all(16),
        children: [
          EmptyStateCard(
            icon: Icons.cloud_off_rounded,
            title: 'Không tải được tiền COD',
            message: 'Kiểm tra kết nối rồi thử lại.',
            actionLabel: 'Thử lại',
            onAction: notifier.refresh,
          ),
        ],
      ),
      data: (custody) {
        final collections = custody.eligibleCollections;
        final selectedTotal = collections
            .where((item) => _selectedIds.contains(item.id))
            .fold<double>(0, (sum, item) => sum + item.amount);
        return RefreshIndicator(
          onRefresh: notifier.refresh,
          child: ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
            children: [
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: AppColors.warningSoft,
                  borderRadius: BorderRadius.circular(14),
                  border: Border.all(color: AppColors.warningBorder),
                ),
                child: Row(
                  children: [
                    const Icon(
                      Icons.payments_rounded,
                      color: AppColors.warning,
                      size: 26,
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Text(
                            'Tiền mặt đang giữ',
                            style: TextStyle(
                              fontSize: 13.5,
                              fontWeight: FontWeight.w600,
                              color: AppColors.warning,
                            ),
                          ),
                          Text(
                            AppFormatters.currency(custody.availableTotal),
                            style: AppTheme.numStyle(
                              fontSize: 24,
                              fontWeight: FontWeight.w800,
                              color: AppColors.textPrimary,
                            ),
                          ),
                        ],
                      ),
                    ),
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.end,
                      children: [
                        Text(
                          '${collections.length} đơn',
                          style: AppTheme.numStyle(
                            fontSize: 14,
                            color: AppColors.warning,
                          ),
                        ),
                        if (custody.lockedTotal > 0)
                          Text(
                            'Chờ đếm ${AppFormatters.currency(custody.lockedTotal)}',
                            style: const TextStyle(
                              fontSize: 11,
                              color: AppColors.textSecondary,
                            ),
                          ),
                      ],
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 14),
              if (collections.isEmpty)
                const EmptyStateCard(
                  icon: Icons.task_alt_rounded,
                  title: 'Không có COD chờ lập phiếu',
                  message:
                      'Tiền thu hộ sẽ xuất hiện sau mỗi đơn giao thành công.',
                )
              else ...[
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        'Chọn các khoản muốn nộp',
                        style: Theme.of(context).textTheme.titleSmall,
                      ),
                    ),
                    TextButton(
                      onPressed: () => setState(() {
                        if (_selectedIds.length == collections.length) {
                          _selectedIds.clear();
                        } else {
                          _selectedIds.addAll(
                            collections.map((item) => item.id),
                          );
                        }
                      }),
                      child: Text(
                        _selectedIds.length == collections.length
                            ? 'Bỏ chọn tất cả'
                            : 'Chọn tất cả',
                      ),
                    ),
                  ],
                ),
                for (final collection in collections)
                  Card(
                    margin: const EdgeInsets.only(bottom: 8),
                    child: CheckboxListTile(
                      value: _selectedIds.contains(collection.id),
                      onChanged: (checked) => setState(() {
                        if (checked == true) {
                          _selectedIds.add(collection.id);
                        } else {
                          _selectedIds.remove(collection.id);
                        }
                      }),
                      controlAffinity: ListTileControlAffinity.leading,
                      title: Text(
                        collection.orderCode ??
                            AppFormatters.shortCode(null, collection.orderId),
                        style: const TextStyle(fontWeight: FontWeight.w700),
                      ),
                      subtitle: Text(
                        collection.outcome == 'FULL'
                            ? 'Đã thu đủ'
                            : 'Thu ${AppFormatters.currency(collection.amount)} / ${AppFormatters.currency(collection.expectedAmount)}',
                      ),
                      secondary: Text(
                        AppFormatters.currency(collection.amount),
                        style: AppTheme.numStyle(fontWeight: FontWeight.w800),
                      ),
                    ),
                  ),
                const SizedBox(height: 6),
                SizedBox(
                  height: 52,
                  child: FilledButton.icon(
                    onPressed: _selectedIds.isEmpty || _submitting
                        ? null
                        : () => _submitReceipt(custody),
                    icon: _submitting
                        ? const SizedBox.square(
                            dimension: 18,
                            child: CircularProgressIndicator(
                              strokeWidth: 2,
                              color: Colors.white,
                            ),
                          )
                        : const Icon(Icons.outbox_rounded),
                    label: Text(
                      _selectedIds.isEmpty
                          ? 'Chọn khoản COD để lập phiếu'
                          : 'Lập phiếu ${_selectedIds.length} khoản · ${AppFormatters.currency(selectedTotal)}',
                    ),
                  ),
                ),
              ],
              if (custody.recentHandovers.isNotEmpty) ...[
                const SizedBox(height: 24),
                const Text(
                  'Phiếu nộp COD gần đây',
                  style: TextStyle(fontSize: 15, fontWeight: FontWeight.w800),
                ),
                const SizedBox(height: 8),
                for (final handover in custody.recentHandovers)
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: CircleAvatar(
                      backgroundColor: handover.status == 'SETTLED'
                          ? AppColors.successSoft
                          : AppColors.primarySoft,
                      child: Icon(
                        handover.status == 'SETTLED'
                            ? Icons.verified_rounded
                            : Icons.hourglass_top_rounded,
                        color: handover.status == 'SETTLED'
                            ? AppColors.success
                            : AppColors.primary,
                      ),
                    ),
                    title: Text(
                      AppFormatters.currency(handover.declaredTotal),
                      style: AppTheme.numStyle(fontWeight: FontWeight.w800),
                    ),
                    subtitle: Text(
                      '${handover.lines.length} đơn · ${handover.status == 'SUBMITTED'
                          ? 'Quầy đang kiểm đếm'
                          : handover.status == 'SETTLED'
                          ? 'Đã quyết toán đủ'
                          : handover.status == 'SETTLED_WITH_VARIANCE'
                          ? 'Đã quyết toán có chênh lệch'
                          : 'Cần kiểm tra'}',
                    ),
                  ),
              ],
            ],
          ),
        );
      },
    );
  }
}
