package com.swiftroute.orderservice.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.swiftroute.orderservice.entity.Tenant;
import com.swiftroute.orderservice.entity.enums.DeliveryMode;
import com.swiftroute.orderservice.entity.enums.PickupLocationStatus;
import com.swiftroute.orderservice.entity.enums.TenantStatus;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import com.swiftroute.orderservice.repository.PickupLocationRepository;

@Service
public class OperationalReadinessService {
    private final PickupLocationRepository pickups;
    private final RestClient fleet;
    private final String key;

    public OperationalReadinessService(PickupLocationRepository pickups,
            @Value("${driver-service.url:http://localhost:8083}") String url,
            @Value("${tenant.internal-key:}") String key) {
        this.pickups = pickups;
        this.key = key;
        var factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(java.time.Duration.ofSeconds(5));
        fleet = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    public record FleetState(boolean available, List<Map<String, Object>> drivers) {
    }

    public FleetState fleetState(UUID tenantId) {
        try {
            if (key.isBlank())
                return new FleetState(false, List.of());
            var result = fleet.get().uri("/internal/fleet/" + tenantId).header("X-Service-Key", key).retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {
                    });
            return new FleetState(true, result == null ? List.of() : result);
        } catch (org.springframework.web.client.RestClientException ex) {
            return new FleetState(false, List.of());
        }
    }

    public boolean hasPickup(Tenant tenant) {
        // Existing tenants may use their established depot coordinates.
        return pickups.countByTenantIdAndStatus(tenant.getId(), PickupLocationStatus.ACTIVE) > 0
                || (tenant.getPickupLat() != null && tenant.getPickupLng() != null && tenant.getAddress() != null
                        && !tenant.getAddress().isBlank());
    }

    public Map<String, Object> check(Tenant tenant) {
        var state = fleetState(tenant.getId());
        List<String> missing = new ArrayList<>();
        if (tenant.getStatus() != TenantStatus.APPROVED)
            missing.add("TENANT_" + tenant.getStatus().name());
        if (!hasPickup(tenant))
            missing.add("NO_PICKUP_POINT");
        if (tenant.getDeliveryMode() == DeliveryMode.SHARED_POOL)
            missing.add("SHARED_NETWORK_UNAVAILABLE");
        else if (!state.available())
            missing.add("DRIVER_SERVICE_UNAVAILABLE");
        else if (state.drivers().isEmpty())
            missing.add("NO_ACTIVE_DRIVER");
        return Map.of("tenant_id", tenant.getId(), "tenant_approved", tenant.getStatus() == TenantStatus.APPROVED,
                "ready_for_operations", missing.isEmpty(), "missing_requirements", missing, "shared_network_available",
                false,
                "own_fleet_available", state.available() && !state.drivers().isEmpty(), "delivery_mode",
                tenant.getDeliveryMode().name());
    }

    public void requireCreation(Tenant tenant) {
        requireApproved(tenant);
        if (!hasPickup(tenant) || tenant.getDeliveryMode() == DeliveryMode.SHARED_POOL)
            throw new AppException(ErrorCode.OPERATIONS_NOT_READY);
    }

    public void requireAssignment(Tenant tenant, UUID driverId) {
        requireCreation(tenant);
        var state = fleetState(tenant.getId());
        if (!state.available() || state.drivers().stream()
                .noneMatch(x -> driverId.toString().equals(String.valueOf(x.get("driver_id")))
                        || driverId.toString().equals(String.valueOf(x.get("user_id")))))
            throw new AppException(ErrorCode.OPERATIONS_NOT_READY);
    }

    public static void requireApproved(Tenant tenant) {
        if (tenant.getStatus() == TenantStatus.PENDING)
            throw new AppException(ErrorCode.TENANT_PENDING);
        if (tenant.getStatus() == TenantStatus.SUSPENDED)
            throw new AppException(ErrorCode.TENANT_SUSPENDED);
        if (tenant.getStatus() != TenantStatus.APPROVED)
            throw new AppException(ErrorCode.TENANT_REJECTED);
    }
}
