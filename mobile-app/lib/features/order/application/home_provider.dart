// features/order/application/home_provider.dart

import 'dart:math';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/features/order/data/datasources/tenant_remote_data_source.dart';
import 'package:swiftroute_app/features/order/data/models/tenant_model.dart';

class HomeState {
  final String currentAddress;
  final double currentLat;
  final double currentLng;
  final List<TenantModel> allTenants;
  final BusinessType? selectedCategory;
  final String searchQuery;
  final bool isLoading;
  final String? errorMessage;

  const HomeState({
    this.currentAddress = 'Dinh Độc Lập, 135 Nam Kỳ Khởi Nghĩa, Quận 1',
    this.currentLat = 10.7769,
    this.currentLng = 106.7009,
    this.allTenants = const [],
    this.selectedCategory,
    this.searchQuery = '',
    this.isLoading = false,
    this.errorMessage,
  });

  /// Filtered tenants matching search keyword and chosen business category.
  List<TenantModel> get filteredTenants {
    final query = searchQuery.trim().toLowerCase();
    return allTenants.where((t) {
      final matchesCategory =
          selectedCategory == null || t.businessType == selectedCategory;
      final matchesQuery =
          query.isEmpty ||
          t.businessName.toLowerCase().contains(query) ||
          (t.address != null && t.address!.toLowerCase().contains(query));
      return matchesCategory && matchesQuery;
    }).toList();
  }

  HomeState copyWith({
    String? currentAddress,
    double? currentLat,
    double? currentLng,
    List<TenantModel>? allTenants,
    BusinessType? selectedCategory,
    bool clearCategory = false,
    String? searchQuery,
    bool? isLoading,
    String? errorMessage,
  }) {
    return HomeState(
      currentAddress: currentAddress ?? this.currentAddress,
      currentLat: currentLat ?? this.currentLat,
      currentLng: currentLng ?? this.currentLng,
      allTenants: allTenants ?? this.allTenants,
      selectedCategory:
          clearCategory ? null : (selectedCategory ?? this.selectedCategory),
      searchQuery: searchQuery ?? this.searchQuery,
      isLoading: isLoading ?? this.isLoading,
      errorMessage: errorMessage,
    );
  }
}

class HomeNotifier extends StateNotifier<HomeState> {
  final TenantRemoteDataSource _tenantDataSource;

  HomeNotifier(this._tenantDataSource) : super(const HomeState()) {
    loadNearbyTenants();
  }

  /// Calculates geodesic distance in km using the Haversine formula.
  static double calculateDistanceKm(
    double lat1,
    double lng1,
    double lat2,
    double lng2,
  ) {
    const p = 0.017453292519943295; // Math.PI / 180
    final a =
        0.5 -
        cos((lat2 - lat1) * p) / 2 +
        cos(lat1 * p) * cos(lat2 * p) * (1 - cos((lng2 - lng1) * p)) / 2;
    return 12742 * asin(sqrt(a));
  }

  Future<void> loadNearbyTenants() async {
    state = state.copyWith(isLoading: true, errorMessage: null);
    try {
      final tenants = await _tenantDataSource.getNearbyTenants(
        lat: state.currentLat,
        lng: state.currentLng,
        limit: 15,
      );

      // Sort by distance to customer's current location
      tenants.sort((a, b) {
        final distA = calculateDistanceKm(
          state.currentLat,
          state.currentLng,
          a.pickupLat,
          a.pickupLng,
        );
        final distB = calculateDistanceKm(
          state.currentLat,
          state.currentLng,
          b.pickupLat,
          b.pickupLng,
        );
        return distA.compareTo(distB);
      });

      state = state.copyWith(allTenants: tenants, isLoading: false);
    } catch (e) {
      state = state.copyWith(
        isLoading: false,
        errorMessage: 'Không thể tải danh sách cửa hàng: $e',
      );
    }
  }

  void setSearchQuery(String query) {
    state = state.copyWith(searchQuery: query);
  }

  void selectCategory(BusinessType? category) {
    if (state.selectedCategory == category) {
      // Toggle off
      state = state.copyWith(clearCategory: true);
    } else {
      state = state.copyWith(selectedCategory: category);
    }
  }

  Future<void> updateLocation({
    required String address,
    required double lat,
    required double lng,
  }) async {
    state = state.copyWith(
      currentAddress: address,
      currentLat: lat,
      currentLng: lng,
    );
    await loadNearbyTenants();
  }
}

final homeProvider = StateNotifierProvider<HomeNotifier, HomeState>((ref) {
  final dataSource = ref.watch(tenantRemoteDataSourceProvider);
  return HomeNotifier(dataSource);
});

