package com.swiftroute.driverservice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.swiftroute.driverservice.entity.OutboxEvent;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query(value = "SELECT * FROM outbox_events WHERE published_at IS NULL AND dlq_published_at IS NULL AND (next_attempt_at IS NULL OR next_attempt_at <= now()) ORDER BY created_at ASC LIMIT 100 FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OutboxEvent> findPendingEventsForPublish();
}
