package com.swiftroute.authservice.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.swiftroute.authservice.entity.PasswordResetToken;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    boolean existsByUser_IdAndCreatedAtAfter(UUID userId, Instant cutoff);

    List<PasswordResetToken> findAllByUser_IdAndUsedAtIsNull(UUID userId);

    @Modifying
    @Query("""
            update PasswordResetToken token set token.usedAt = :now
            where token.tokenId = :tokenId
              and token.usedAt is null
              and token.expiresAt > :now
            """)
    int consume(@Param("tokenId") UUID tokenId, @Param("now") Instant now);
}
