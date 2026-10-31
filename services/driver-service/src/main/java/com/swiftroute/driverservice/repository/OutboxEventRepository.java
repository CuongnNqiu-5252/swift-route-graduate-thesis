package com.swiftroute.driverservice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.swiftroute.driverservice.entity.OutboxEvent;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
}
