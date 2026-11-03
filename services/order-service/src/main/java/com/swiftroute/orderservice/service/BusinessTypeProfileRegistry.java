package com.swiftroute.orderservice.service;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.swiftroute.orderservice.entity.OptimizationWeights;
import com.swiftroute.orderservice.entity.Tenant;
import com.swiftroute.orderservice.entity.TenantDispatchConfig;
import com.swiftroute.orderservice.entity.enums.BusinessType;

/**
 * Immutable registry defining recommended default configurations for each business type.
 * Defensive copies are returned to prevent mutation of the global preset definitions.
 */
@Component
public class BusinessTypeProfileRegistry {

    public record BusinessTypeProfile(
            BusinessType businessType,
            Double timeWeight,
            Double costWeight,
            Integer maxOrdersPerDriver,
            Integer maxWaitTimeSeconds,
            String urgentOrderMode,
            Boolean allowPoolDrivers,
            String description
    ) {
        /**
         * Creates a fresh, defensive copy of the TenantDispatchConfig configured with preset values.
         */
        public TenantDispatchConfig toDispatchConfig() {
            return TenantDispatchConfig.builder()
                    .autoDispatchEnabled(true)
                    .batchSizeThreshold(maxOrdersPerDriver)
                    .maxOrdersPerDriver(maxOrdersPerDriver)
                    .maxWaitTimeSeconds(maxWaitTimeSeconds)
                    .urgentOrderMode(urgentOrderMode)
                    .requireApproval(false)
                    .algorithm("auto")
                    .allowPoolDrivers(allowPoolDrivers)
                    .build();
        }

        /**
         * Creates a fresh, defensive copy of the OptimizationWeights configured with preset values.
         */
        public OptimizationWeights toOptimizationWeights() {
            return OptimizationWeights.builder()
                    .timeWeight(timeWeight)
                    .costWeight(costWeight)
                    .build();
        }
    }

    private static final Map<BusinessType, BusinessTypeProfile> PRESETS;

    static {
        Map<BusinessType, BusinessTypeProfile> map = new EnumMap<>(BusinessType.class);

        // RESTAURANT: 0.8 time, 0.2 cost, 3 orders, 180s wait, IMMEDIATE_SINGLE, Pool=true
        map.put(BusinessType.RESTAURANT, new BusinessTypeProfile(
                BusinessType.RESTAURANT,
                0.8,
                0.2,
                3,
                180,
                "IMMEDIATE_SINGLE",
                true,
                "Quán ăn / Nhà hàng: Ưu tiên tối đa tốc độ giao đồ ăn nóng, gom ít đơn"
        ));

        // CAFE: 0.7 time, 0.3 cost, 4 orders, 240s wait, IMMEDIATE_SINGLE, Pool=true
        map.put(BusinessType.CAFE, new BusinessTypeProfile(
                BusinessType.CAFE,
                0.7,
                0.3,
                4,
                240,
                "IMMEDIATE_SINGLE",
                true,
                "Quán cafe / Trà sữa: Giao nhanh cự ly gần để đảm bảo độ tươi của đồ uống"
        ));

        // PHARMACY: 0.9 time, 0.1 cost, 2 orders, 120s wait, IMMEDIATE_SINGLE, Pool=true
        map.put(BusinessType.PHARMACY, new BusinessTypeProfile(
                BusinessType.PHARMACY,
                0.9,
                0.1,
                2,
                120,
                "IMMEDIATE_SINGLE",
                true,
                "Hiệu thuốc / Y tế: Dược phẩm cấp thiết, ưu tiên thời gian tuyệt đối"
        ));

        // RETAIL: 0.4 time, 0.6 cost, 8 orders, 600s wait, BATCH_IMMEDIATE, Pool=false
        map.put(BusinessType.RETAIL, new BusinessTypeProfile(
                BusinessType.RETAIL,
                0.4,
                0.6,
                8,
                600,
                "BATCH_IMMEDIATE",
                false,
                "Bán lẻ / Tạp hóa: Hàng khô, gom nhiều đơn để tối ưu chi phí vận chuyển"
        ));

        // OTHER: 0.5 time, 0.5 cost, 5 orders, 300s wait, IMMEDIATE_SINGLE, Pool=false
        map.put(BusinessType.OTHER, new BusinessTypeProfile(
                BusinessType.OTHER,
                0.5,
                0.5,
                5,
                300,
                "IMMEDIATE_SINGLE",
                false,
                "Mặc định / Ngành nghề khác: Cân bằng hài hòa giữa thời gian và chi phí"
        ));

        PRESETS = Collections.unmodifiableMap(map);
    }

    /**
     * Retrieves the recommended profile for a business type.
     * Defaults to OTHER if null.
     */
    public BusinessTypeProfile getProfile(BusinessType type) {
        if (type == null) {
            return PRESETS.get(BusinessType.OTHER);
        }
        BusinessTypeProfile profile = PRESETS.get(type);
        return profile != null ? profile : PRESETS.get(BusinessType.OTHER);
    }

    /**
     * Returns an unmodifiable map of all available profiles.
     */
    public Map<BusinessType, BusinessTypeProfile> getAllProfiles() {
        return PRESETS;
    }

    /**
     * Applies the complete recommended profile onto a tenant entity,
     * updating its businessType, dispatchConfig, and optimizationWeights.
     * Preserves unrelated settings like requireApproval or custom algorithm if needed.
     */
    public void applyProfile(Tenant tenant, BusinessType type) {
        BusinessTypeProfile profile = getProfile(type);
        tenant.setBusinessType(profile.businessType());

        // Update optimization weights
        tenant.setOptimizationWeights(profile.toOptimizationWeights());

        // Update dispatch config, preserving custom preferences like requireApproval if previously set
        TenantDispatchConfig current = tenant.getDispatchConfig();
        boolean previousApproval = current != null && Boolean.TRUE.equals(current.getRequireApproval());
        String previousAlgorithm = current != null && current.getAlgorithm() != null ? current.getAlgorithm() : "auto";

        TenantDispatchConfig nextConfig = profile.toDispatchConfig();
        nextConfig.setRequireApproval(previousApproval);
        nextConfig.setAlgorithm(previousAlgorithm);

        tenant.setDispatchConfig(nextConfig);
    }
}

