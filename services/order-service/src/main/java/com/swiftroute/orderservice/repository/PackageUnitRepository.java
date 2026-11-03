package com.swiftroute.orderservice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.swiftroute.orderservice.entity.PackageUnit;

public interface PackageUnitRepository extends JpaRepository<PackageUnit, UUID> {
    List<PackageUnit> findByOrder_IdOrderByPackageNumberAsc(UUID orderId);
    List<PackageUnit> findByIdIn(List<UUID> ids);
}
