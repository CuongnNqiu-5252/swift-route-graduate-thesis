import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:swiftroute_app/core/offline/offline_action.dart';
import 'package:swiftroute_app/core/offline/offline_conflict_sheet.dart';

void main() {
  testWidgets('driver can accept server state for one conflict', (
    tester,
  ) async {
    final conflict =
        OfflineAction.create(
          type: OfflineActionType.deliveryTaskUpdate,
          method: 'PATCH',
          path: '/delivery-tasks/order/o-1',
          entityId: 'o-1',
          body: const {'status': 'COMPLETED'},
        ).copyWith(
          status: OfflineActionStatus.conflict,
          lastError: 'Đơn đã bị hủy trên máy chủ',
        );
    String? acceptedId;

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Builder(
            builder: (context) => FilledButton(
              onPressed: () => showModalBottomSheet<void>(
                context: context,
                isScrollControlled: true,
                builder: (_) => OfflineConflictSheet(
                  conflicts: [conflict],
                  onAcceptServerState: (id) async => acceptedId = id,
                  onEscalate: (_, _) async => false,
                ),
              ),
              child: const Text('Mở xung đột'),
            ),
          ),
        ),
      ),
    );

    await tester.tap(find.text('Mở xung đột'));
    await tester.pumpAndSettle();
    expect(find.text('Cần xác nhận đồng bộ'), findsOneWidget);
    expect(find.text('Đã ghi nhận giao thành công'), findsOneWidget);
    expect(find.text('Đơn đã bị hủy trên máy chủ'), findsOneWidget);

    await tester.tap(find.text('Dùng trạng thái máy chủ'));
    await tester.pumpAndSettle();
    expect(find.text('Dùng trạng thái trên máy chủ?'), findsOneWidget);
    await tester.tap(find.text('Xác nhận'));
    await tester.pumpAndSettle();

    expect(acceptedId, conflict.id);
    expect(find.text('Cần xác nhận đồng bộ'), findsNothing);
  });

  testWidgets('driver can escalate field reality with a note', (tester) async {
    final conflict =
        OfflineAction.create(
          type: OfflineActionType.deliveryTaskUpdate,
          method: 'PATCH',
          path: '/delivery-tasks/order/o-2',
          entityId: 'o-2',
          body: const {'status': 'COMPLETED'},
        ).copyWith(
          status: OfflineActionStatus.conflict,
          lastError: 'Đơn đã bị hủy trên máy chủ',
        );
    String? escalatedId;
    String? escalatedNote;

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Builder(
            builder: (context) => FilledButton(
              onPressed: () => showModalBottomSheet<void>(
                context: context,
                isScrollControlled: true,
                builder: (_) => OfflineConflictSheet(
                  conflicts: [conflict],
                  onAcceptServerState: (_) async {},
                  onEscalate: (id, note) async {
                    escalatedId = id;
                    escalatedNote = note;
                    return true;
                  },
                ),
              ),
              child: const Text('Mở xung đột'),
            ),
          ),
        ),
      ),
    );

    await tester.tap(find.text('Mở xung đột'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Báo điều phối'));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byType(TextField),
      'Khách đã nhận hàng lúc 14:30',
    );
    await tester.tap(find.text('Gửi báo cáo'));
    await tester.pumpAndSettle();

    expect(escalatedId, conflict.id);
    expect(escalatedNote, 'Khách đã nhận hàng lúc 14:30');
    expect(find.text('Cần xác nhận đồng bộ'), findsNothing);
  });
}
