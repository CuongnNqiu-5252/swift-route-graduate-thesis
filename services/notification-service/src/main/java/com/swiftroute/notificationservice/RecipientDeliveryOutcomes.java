package com.swiftroute.notificationservice;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class RecipientDeliveryOutcomes {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    void record(UUID eventId, State state) {
        jdbc.update("""
                INSERT INTO processed_events(event_id, consumer_name)
                VALUES (?, ?) ON CONFLICT DO NOTHING
                """, eventId, "recipient-delivery-outcome-" + state.name());
    }

    Map<UUID, String> read(List<UUID> eventIds) {
        return namedJdbc.query("""
                SELECT event_id, CASE
                    WHEN bool_or(consumer_name = 'recipient-delivery-outcome-SIMULATED') THEN 'SIMULATED'
                    WHEN bool_or(consumer_name = 'recipient-delivery-outcome-SKIPPED') THEN 'SKIPPED'
                    WHEN bool_or(consumer_name = 'recipient-delivery-outcome-FAILED') THEN 'FAILED'
                    ELSE 'PROCESSED'
                END AS status
                FROM processed_events
                WHERE event_id IN (:ids) AND consumer_name IN (
                    'recipient-delivery-started', 'recipient-delivery-outcome-SIMULATED',
                    'recipient-delivery-outcome-SKIPPED', 'recipient-delivery-outcome-FAILED'
                )
                GROUP BY event_id
                """, Map.of("ids", eventIds), rs -> {
            Map<UUID, String> results = new java.util.HashMap<>();
            while (rs.next()) {
                results.put(rs.getObject("event_id", UUID.class), rs.getString("status"));
            }
            return results;
        });
    }

    enum State { SIMULATED, SKIPPED, FAILED }
}
