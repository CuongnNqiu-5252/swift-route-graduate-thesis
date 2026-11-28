package com.swiftroute.orderservice.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.swiftroute.orderservice.entity.TenantTrackingConfig;

@Repository
public interface TenantTrackingConfigRepository extends JpaRepository<TenantTrackingConfig, UUID> {

}
