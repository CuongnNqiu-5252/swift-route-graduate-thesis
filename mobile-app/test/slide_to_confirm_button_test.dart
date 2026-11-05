import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:swiftroute_app/core/widgets/slide_to_confirm_button.dart';

void main() {
  testWidgets('SlideToConfirmButton renders label and triggers on full slide', (
    tester,
  ) async {
    var confirmed = false;

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Center(
            child: SizedBox(
              width: 320,
              child: SlideToConfirmButton(
                label: 'Vuốt để lấy hàng',
                onConfirmed: () async {
                  confirmed = true;
                },
              ),
            ),
          ),
        ),
      ),
    );

    expect(find.text('Vuốt để lấy hàng'), findsOneWidget);
    expect(confirmed, isFalse);

    // Find the thumb icon and drag it to the right
    final thumbFinder = find.byIcon(Icons.double_arrow_rounded);
    expect(thumbFinder, findsOneWidget);

    await tester.drag(thumbFinder, const Offset(300, 0));
    await tester.pumpAndSettle();

    expect(confirmed, isTrue);
  });

  testWidgets('SlideToConfirmButton does not trigger when disabled', (
    tester,
  ) async {
    var confirmed = false;

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Center(
            child: SizedBox(
              width: 320,
              child: SlideToConfirmButton(
                label: 'Vuốt để lấy hàng',
                enabled: false,
                onConfirmed: () async {
                  confirmed = true;
                },
              ),
            ),
          ),
        ),
      ),
    );

    final thumbFinder = find.byIcon(Icons.double_arrow_rounded);
    await tester.drag(thumbFinder, const Offset(300, 0));
    await tester.pumpAndSettle();

    expect(confirmed, isFalse);
  });
}
