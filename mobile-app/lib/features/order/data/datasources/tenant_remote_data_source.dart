// features/order/data/datasources/tenant_remote_data_source.dart

import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/network/dio.dart';
import 'package:swiftroute_app/features/order/data/models/tenant_model.dart';

class TenantRemoteDataSource {
  final Dio _dio;

  TenantRemoteDataSource(this._dio);

  /// Fetches approved tenants/shops nearby given coordinates ([lat], [lng]).
  Future<List<TenantModel>> getNearbyTenants({
    required double lat,
    required double lng,
    int limit = 10,
  }) async {
    try {
      final response = await _dio.get(
        '/tenants/nearby',
        queryParameters: {'lat': lat, 'lng': lng, 'limit': limit},
      );

      final body = response.data;
      if (body is Map<String, dynamic> && body['data'] is List) {
        final list = body['data'] as List<dynamic>;
        return list
            .map((item) => TenantModel.fromJson(item as Map<String, dynamic>))
            .toList();
      }
      return _mockNearbyTenants(lat, lng);
    } catch (e) {
      debugPrint(
        '[TenantRemoteDataSource] API error, falling back to mock: $e',
      );
      return _mockNearbyTenants(lat, lng);
    }
  }

  /// High quality realistic fallback stores matching seed database in Ho Chi Minh City.
  List<TenantModel> _mockNearbyTenants(double lat, double lng) {
    return [
      const TenantModel(
        id: 'a1111111-1111-1111-1111-111111111111',
        businessName: 'Kho Hàng Trung Tâm Quận 1',
        businessType: BusinessType.retail,
        address: 'Bến Nghé, Quận 1, TP. Hồ Chí Minh',
        pickupLat: 10.776889,
        pickupLng: 106.700806,
        status: TenantStatus.approved,
        subscriptionPlan: SubscriptionPlan.premium,
      ),
      const TenantModel(
        id: '2d1a50c4-2dc0-4efc-9825-dea77358becd',
        businessName: 'Chuỗi Cafe Sài Gòn Quận 3',
        businessType: BusinessType.cafe,
        address: '88 Võ Văn Tần, Phường Võ Thị Sáu, Quận 3, TP. Hồ Chí Minh',
        pickupLat: 10.7761,
        pickupLng: 106.6872,
        status: TenantStatus.approved,
        subscriptionPlan: SubscriptionPlan.premium,
      ),
      const TenantModel(
        id: '101ad721-d86f-4347-b768-88a9225521be',
        businessName: 'Chi Nhánh Quận 10 (Cơm Tấm & Fastfood)',
        businessType: BusinessType.restaurant,
        address: 'Đường 3 Tháng 2, Quận 10, TP. Hồ Chí Minh',
        pickupLat: 10.7725,
        pickupLng: 106.6690,
        status: TenantStatus.approved,
        subscriptionPlan: SubscriptionPlan.freeTrial,
      ),
      const TenantModel(
        id: '93fffdfa-1d48-4549-8ec0-10d82bc764ba',
        businessName: 'Trạm Tiếp Vận Bình Thạnh (Siêu Thị Tiện Lợi)',
        businessType: BusinessType.retail,
        address: '152 Điện Biên Phủ, Phường 25, Bình Thạnh, TP. Hồ Chí Minh',
        pickupLat: 10.8012,
        pickupLng: 106.7115,
        status: TenantStatus.approved,
        subscriptionPlan: SubscriptionPlan.premium,
      ),
      const TenantModel(
        id: '559cc525-c2d9-4d26-8643-baa3e5b00f08',
        businessName: 'Nhà Thuốc Tiện Lợi Quận 5',
        businessType: BusinessType.pharmacy,
        address: '235 Nguyễn Trãi, Phường 2, Quận 5, TP. Hồ Chí Minh',
        pickupLat: 10.7554,
        pickupLng: 106.6668,
        status: TenantStatus.approved,
        subscriptionPlan: SubscriptionPlan.basic,
      ),
      const TenantModel(
        id: 'b3444119-c52c-4256-8edf-44bc044b0804',
        businessName: 'Bếp Trung Tâm Tân Bình (Đồ Ăn Nóng)',
        businessType: BusinessType.restaurant,
        address: '45 Hoàng Hoa Thám, Phường 13, Tân Bình, TP. Hồ Chí Minh',
        pickupLat: 10.8038,
        pickupLng: 106.6521,
        status: TenantStatus.approved,
        subscriptionPlan: SubscriptionPlan.basic,
      ),
      const TenantModel(
        id: '18c3f7a7-84d6-41d1-9a30-92eeea46d7c2',
        businessName: 'Kho Hàng Nam Sài Gòn Quận 7',
        businessType: BusinessType.other,
        address: '101 Nguyễn Thị Thập, Tân Phú, Quận 7, TP. Hồ Chí Minh',
        pickupLat: 10.7341,
        pickupLng: 106.7198,
        status: TenantStatus.approved,
        subscriptionPlan: SubscriptionPlan.freeTrial,
      ),
    ];
  }
}

final tenantRemoteDataSourceProvider = Provider<TenantRemoteDataSource>((ref) {
  return TenantRemoteDataSource(ref.watch(dioProvider));
});
