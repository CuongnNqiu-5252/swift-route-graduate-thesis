package com.swiftroute.orderservice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.swiftroute.orderservice.entity.PackageCustodyEvent;

public interface PackageCustodyEventRepository extends JpaRepository<PackageCustodyEvent, UUID> {
    List<PackageCustodyEvent> findByPackageUnit_IdOrderByCreatedAtDesc(UUID packageId);
}
