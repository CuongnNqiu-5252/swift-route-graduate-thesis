package com.swiftroute.orderservice.service.planning;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Thin wrapper around PostgreSQL advisory locks used to prevent duplicate
 * planning cycles when multiple Order Service instances run concurrently.
 *
 * <p>Uses {@code pg_try_advisory_lock} (session-level, non-blocking):
 * returns {@code true} immediately if the lock was acquired, {@code false} if
 * another session already holds it.
 *
 * <p>Locks are released explicitly via {@link #unlock(long)}, or automatically
 * released when the database session ends (crash-safe).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdvisoryLockHelper {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Attempts to acquire a PostgreSQL session-level advisory lock.
     *
     * @param lockKey a {@code long} key derived from the tenant UUID
     * @return {@code true} if the lock was acquired, {@code false} if already held
     */
    public boolean tryLock(long lockKey) {
        Boolean acquired = jdbcTemplate.queryForObject(
                "SELECT pg_try_advisory_lock(?)", Boolean.class, lockKey);
        boolean result = Boolean.TRUE.equals(acquired);
        log.debug("Advisory lock key={} acquired={}", lockKey, result);
        return result;
    }

    /**
     * Releases a previously acquired advisory lock.
     *
     * @param lockKey the same key passed to {@link #tryLock(long)}
     */
    public void unlock(long lockKey) {
        jdbcTemplate.execute("SELECT pg_advisory_unlock(" + lockKey + ")");
        log.debug("Advisory lock key={} released.", lockKey);
    }
}

