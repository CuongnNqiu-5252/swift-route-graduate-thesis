package com.swiftroute.orderservice.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.swiftroute.orderservice.entity.DeliveryProofConfig;

@Repository

public interface DeliveryProofConfigRepository
                extends JpaRepository<DeliveryProofConfig, UUID> {

        Optional<DeliveryProofConfig> findByTenantId(UUID tenantId);

}
