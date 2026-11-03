import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/theme/app_colors.dart';
import 'package:swiftroute_app/features/driver/application/driver_provider.dart';
import 'package:swiftroute_app/features/driver/data/models/driver_cod_collection_model.dart';
import 'package:swiftroute_app/features/driver/data/models/package_handover_model.dart';

class DriverCodHandoverTab extends ConsumerWidget {
  const DriverCodHandoverTab({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final codState = ref.watch(driverCodHandoverProvider);
    final packageState = ref.watch(packageHandoverProvider);
    return DefaultTabController(
      length: 2,
      child: Scaffold(
        appBar: AppBar(
          title: const Text('Bàn giao'),
          actions: [
            IconButton(
              onPressed: () {
                ref.read(driverCodHandoverProvider.notifier).refresh();
                ref.read(packageHandoverProvider.notifier).refresh();
              },
              icon: const Icon(Icons.refresh_rounded),
            ),
          ],
          bottom: const TabBar(
            tabs: [
              Tab(text: 'Kiện hàng'),
              Tab(text: 'COD'),
            ],
          ),
        ),
        body: TabBarView(
          children: [
            packageState.when(
              loading: () => const Center(child: CircularProgressIndicator()),
              error: (_, _) => Center(
                child: TextButton(
                  onPressed: () =>
                      ref.read(packageHandoverProvider.notifier).refresh(),
                  child: const Text('Không tải được bàn giao kiện. Thử lại'),
                ),
              ),
              data: (handovers) => handovers.isEmpty
                  ? const Center(
                      child: Padding(
                        padding: EdgeInsets.all(24),
                        child: Text(
                          'Không có lượt bàn giao kiện đang chờ xác nhận.',
                          textAlign: TextAlign.center,
                          style: TextStyle(color: AppColors.textSecondary),
                        ),
                      ),
                    )
                  : RefreshIndicator(
                      onRefresh: () =>
                          ref.read(packageHandoverProvider.notifier).refresh(),
                      child: ListView.separated(
                        padding: const EdgeInsets.all(16),
                        itemCount: handovers.length,
                        separatorBuilder: (_, _) => const SizedBox(height: 10),
                        itemBuilder: (_, index) =>
                            _PackageHandoverItem(handover: handovers[index]),
                      ),
                    ),
            ),
            codState.when(
              loading: () => const Center(child: CircularProgressIndicator()),
              error: (_, _) => Center(
                child: TextButton(
                  onPressed: () =>
                      ref.read(driverCodHandoverProvider.notifier).refresh(),
                  child: const Text('Không tải được danh sách. Thử lại'),
                ),
              ),
              data: (collections) => collections.isEmpty
                  ? const Center(
                      child: Text(
                        'Không có COD chờ bàn giao',
                        style: TextStyle(color: AppColors.textSecondary),
                      ),
                    )
                  : RefreshIndicator(
                      onRefresh: () => ref
                          .read(driverCodHandoverProvider.notifier)
                          .refresh(),
                      child: ListView.separated(
                        padding: const EdgeInsets.all(16),
                        itemCount: collections.length,
                        separatorBuilder: (_, _) => const SizedBox(height: 10),
                        itemBuilder: (_, index) =>
                            _CodHandoverItem(collection: collections[index]),
                      ),
                    ),
            ),
          ],
        ),
      ),
    );
  }
}

class _PackageHandoverItem extends ConsumerStatefulWidget {
  const _PackageHandoverItem({required this.handover});
  final PackageHandoverModel handover;
  @override
  ConsumerState<_PackageHandoverItem> createState() =>
      _PackageHandoverItemState();
}

class _PackageHandoverItemState extends ConsumerState<_PackageHandoverItem> {
  var _saving = false;
  Future<void> _act(String action) async {
    setState(() => _saving = true);
    try {
      await ref
          .read(packageHandoverProvider.notifier)
          .act(widget.handover, action);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Đã xác nhận bàn giao kiện hàng.'),
            backgroundColor: AppColors.success,
          ),
        );
      }
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
              'Không thể xác nhận bàn giao. Hãy tải lại và thử lại.',
            ),
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final me = ref.watch(driverProvider).valueOrNull?.userId;
    final outgoing = me == widget.handover.fromDriverId;
    final canConfirm =
        (outgoing && widget.handover.status == 'REQUESTED') ||
        (!outgoing && widget.handover.status == 'SENDER_CONFIRMED');
    final action = outgoing ? 'sender-confirm' : 'receiver-confirm';
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        border: Border.all(color: AppColors.divider),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(
                outgoing ? Icons.upload_rounded : Icons.download_rounded,
                color: AppColors.primary,
              ),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  outgoing ? 'Bàn giao đi' : 'Nhận bàn giao',
                  style: const TextStyle(fontWeight: FontWeight.w700),
                ),
              ),
              Text(
                '${widget.handover.packageCodes.length} kiện',
                style: const TextStyle(color: AppColors.textSecondary),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            widget.handover.packageCodes.join(' · '),
            style: const TextStyle(
              fontSize: 13,
              color: AppColors.textSecondary,
            ),
          ),
          const SizedBox(height: 4),
          Text(widget.handover.reason, style: const TextStyle(fontSize: 13)),
          if (!canConfirm)
            const Padding(
              padding: EdgeInsets.only(top: 10),
              child: Text(
                'Đang chờ tài xế giao xác nhận trước.',
                style: TextStyle(fontSize: 12, color: AppColors.textSecondary),
              ),
            ),
          if (canConfirm)
            Padding(
              padding: const EdgeInsets.only(top: 12),
              child: SizedBox(
                width: double.infinity,
                child: FilledButton.icon(
                  onPressed: _saving ? null : () => _act(action),
                  icon: _saving
                      ? const SizedBox(
                          width: 16,
                          height: 16,
                          child: CircularProgressIndicator(
                            strokeWidth: 2,
                            color: Colors.white,
                          ),
                        )
                      : const Icon(Icons.verified_rounded),
                  label: Text(
                    outgoing
                        ? 'Xác nhận đã giao kiện'
                        : 'Xác nhận đã nhận kiện',
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class _CodHandoverItem extends ConsumerStatefulWidget {
  const _CodHandoverItem({required this.collection});
  final DriverCodCollectionModel collection;
  @override
  ConsumerState<_CodHandoverItem> createState() => _CodHandoverItemState();
}

class _CodHandoverItemState extends ConsumerState<_CodHandoverItem> {
  var _saving = false;
  Future<void> _declare() async {
    setState(() => _saving = true);
    try {
      await ref
          .read(driverCodHandoverProvider.notifier)
          .declare(widget.collection);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Đã khai báo bàn giao COD. Chờ điều phối xác minh.'),
            backgroundColor: AppColors.success,
          ),
        );
      }
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Không thể khai báo bàn giao COD. Hãy thử lại.'),
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(16),
    decoration: BoxDecoration(
      color: Colors.white,
      border: Border.all(color: AppColors.divider),
      borderRadius: BorderRadius.circular(12),
    ),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          widget.collection.orderCode ??
              '#${widget.collection.orderId.substring(0, 8)}',
          style: const TextStyle(fontWeight: FontWeight.w700),
        ),
        const SizedBox(height: 6),
        Text(
          '${widget.collection.amount.toStringAsFixed(0)} đ',
          style: const TextStyle(
            fontSize: 22,
            fontWeight: FontWeight.bold,
            color: AppColors.textPrimary,
          ),
        ),
        if (widget.collection.notes?.isNotEmpty == true)
          Padding(
            padding: const EdgeInsets.only(top: 4),
            child: Text(
              widget.collection.notes!,
              style: const TextStyle(color: AppColors.textSecondary),
            ),
          ),
        const SizedBox(height: 14),
        SizedBox(
          width: double.infinity,
          child: FilledButton.icon(
            onPressed: _saving ? null : _declare,
            icon: _saving
                ? const SizedBox(
                    width: 16,
                    height: 16,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      color: Colors.white,
                    ),
                  )
                : const Icon(Icons.payments_rounded),
            label: const Text('Xác nhận đã bàn giao'),
            style: FilledButton.styleFrom(backgroundColor: AppColors.primary),
          ),
        ),
      ],
    ),
  );
}
