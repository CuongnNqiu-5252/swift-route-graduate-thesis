package com.swiftroute.authservice.repository;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.swiftroute.authservice.entity.RevokedAuthSession;

@Repository
public interface RevokedAuthSessionRepository extends JpaRepository<RevokedAuthSession, UUID> {

    boolean existsBySessionId(UUID sessionId);

    @Transactional
    void deleteByExpiresAtBefore(Instant cutoff);
}
