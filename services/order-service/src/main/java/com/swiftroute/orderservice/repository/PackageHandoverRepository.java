package com.swiftroute.orderservice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.swiftroute.orderservice.entity.PackageHandover;

public interface PackageHandoverRepository extends JpaRepository<PackageHandover, UUID> {
    @Query("""
            select distinct h from PackageHandover h
            left join fetch h.items i
            left join fetch i.packageUnit p
            where h.tenantId = :tenantId
              and (h.fromDriverId = :driverId or h.toDriverId = :driverId)
              and h.status in :statuses
            order by h.createdAt desc
            """)
    List<PackageHandover> findDriverHandovers(
            @Param("tenantId") UUID tenantId,
            @Param("driverId") UUID driverId,
            @Param("statuses") List<String> statuses);
}
