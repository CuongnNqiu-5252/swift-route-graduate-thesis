// features/driver/application/driver_provider.dart
//
// Riverpod providers for the Driver feature.
// Uses AsyncNotifier pattern for async API flows (as per Flutter coding rules).

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/features/driver/data/datasources/driver_remote_data_source.dart';
import 'package:swiftroute_app/features/driver/data/models/driver_model.dart';

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

/// Holds the driver profile loaded from the backend.
/// AsyncValue<DriverModel?> covers loading / data / error states automatically.
class DriverNotifier extends AsyncNotifier<DriverModel?> {
  @override
  Future<DriverModel?> build() async {
    // No auto-fetch on init; call loadProfile() explicitly after login.
    return null;
  }

  /// Fetch driver profile from the backend using the driver entity UUID.
  Future<void> loadProfile(String driverId) async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(
      () => ref.read(driverRemoteDataSourceProvider).getDriverProfile(driverId),
    );
  }

  /// Toggle online / offline status.
  Future<void> toggleAvailability() async {
    final current = state.valueOrNull;
    if (current == null) return;

    // Optimistic update: flip locally before the network call
    state = AsyncData(current.copyWith(isAvailable: !current.isAvailable));

    final result = await AsyncValue.guard(
      () => ref.read(driverRemoteDataSourceProvider).updateAvailability(
            driverId: current.id,
            isAvailable: !current.isAvailable,
          ),
    );

    // On error, roll back to the original state
    if (result is AsyncError) {
      state = AsyncData(current);
    } else {
      state = result;
    }
  }

  /// Send GPS coordinates to the backend.
  Future<void> reportLocation({
    required double lat,
    required double lng,
  }) async {
    final current = state.valueOrNull;
    if (current == null) return;

    await ref.read(driverRemoteDataSourceProvider).updateLocation(
          driverId: current.id,
          lat: lat,
          lng: lng,
        );
  }
}

/// Global provider for the driver feature state
final driverProvider = AsyncNotifierProvider<DriverNotifier, DriverModel?>(
  DriverNotifier.new,
);
