package com.swiftroute.authservice.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.scheduling.annotation.Scheduled;

import com.swiftroute.authservice.repository.RefreshTokenRepository;

import jakarta.transaction.Transactional;

public class RefreshTokenCleanupJob {
    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenCleanupJob(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    // Chạy mỗi ngày lúc 3h sáng
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupExpiredTokens() {
        Instant cutoff = Instant.now().minus(60, ChronoUnit.DAYS);
        refreshTokenRepository.deleteAllExpiredBefore(cutoff);
    }
}
