package com.swiftroute.authservice.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.swiftroute.authservice.entity.EmailVerificationToken;

@Repository
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {
    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);
    void deleteByExpiresAtBefore(Instant now);
}

