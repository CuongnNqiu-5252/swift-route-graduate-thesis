package com.swiftroute.authservice.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.swiftroute.authservice.entity.ProcessedEvent;
import com.swiftroute.authservice.entity.ProcessedEventId;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, ProcessedEventId> {

    @Modifying
    @Query(value = """
            INSERT INTO processed_events (event_id, consumer_name, processed_at)
            VALUES (:eventId, :consumerName, now())
            ON CONFLICT (event_id, consumer_name) DO NOTHING
            """, nativeQuery = true)
    int insertIfNotExists(@Param("eventId") UUID eventId, @Param("consumerName") String consumerName);
}

