package com.swiftroute.orderservice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.swiftroute.orderservice.entity.Tenant;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {
    boolean existsByBusinessNameIgnoreCase(String businessName);

    @Query(value = """
                SELECT *
                    FROM
                tenants t
                WHERE t.status = 'APPROVED'
                ORDER BY (POWER(t.pickup_lat - :lat, 2) + POWER(t.pickup_lng - :lng, 2))
                LIMIT :limit
            """, nativeQuery = true)

    List<Tenant> getNearbyTenant(@Param("lat") double lat,
            @Param("lng") double lng,
            @Param("limit") int limit);
}
