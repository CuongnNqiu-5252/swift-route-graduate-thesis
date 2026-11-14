package com.swiftroute.orderservice.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.swiftroute.orderservice.entity.PackageCustodyEvent;

public interface PackageCustodyEventRepository extends JpaRepository<PackageCustodyEvent, UUID> {
    Optional<PackageCustodyEvent> findFirstByOrder_IdOrderByCreatedAtDesc(UUID packageId);
}
