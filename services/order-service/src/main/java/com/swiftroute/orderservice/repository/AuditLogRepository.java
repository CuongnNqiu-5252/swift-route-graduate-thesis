package com.swiftroute.orderservice.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.swiftroute.orderservice.entity.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {}
