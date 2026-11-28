package com.swiftroute.orderservice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.swiftroute.orderservice.entity.Tenant;
import com.swiftroute.orderservice.entity.enums.TenantStatus;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {
    boolean existsByBusinessNameIgnoreCase(String businessName);

    /** All tenants with a given operational status. Used by the planning scheduler. */
    List<Tenant> findAllByStatus(TenantStatus status);
}
