package com.swiftroute.orderservice.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.swiftroute.orderservice.entity.Tenant;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {
    boolean existsByBusinessNameIgnoreCase(String businessName);
}
