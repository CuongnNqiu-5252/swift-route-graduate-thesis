// features/driver/presentation/screens/driver_orders_screen.dart
// Compatibility wrapper forwarding to the redesigned DriverMainScreen

import 'package:flutter/material.dart';
import 'package:swiftroute_app/features/driver/presentation/screens/driver_main_screen.dart';

export 'package:swiftroute_app/features/driver/presentation/screens/driver_main_screen.dart';

/// Legacy alias for DriverMainScreen
class DriverOrdersScreen extends StatelessWidget {
  const DriverOrdersScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return const DriverMainScreen();
  }
}
