package com.swiftroute.authservice.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftroute.authservice.entity.TenantMembership;
import com.swiftroute.authservice.entity.User;
import com.swiftroute.authservice.entity.enums.TenantMembershipRole;
import com.swiftroute.authservice.entity.enums.TenantMembershipStatus;
import com.swiftroute.authservice.event.TenantEvent;
import com.swiftroute.authservice.exception.AppException;
import com.swiftroute.authservice.exception.ErrorCode;
import com.swiftroute.authservice.repository.ProcessedEventRepository;
import com.swiftroute.authservice.repository.TenantMembershipRepository;
import com.swiftroute.authservice.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Maintains Auth Service's local membership projection from tenant lifecycle events.
 * Enforces consumer idempotency via processed_events and ordering via event versions.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantMembershipService {
    public static final String CONSUMER_NAME = "auth-service:tenant-event";

    private final TenantMembershipRepository tenantMembershipRepository;
    private final UserRepository userRepository;
    private final ProcessedEventRepository processedEventRepository;
    @org.springframework.beans.factory.annotation.Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Transactional
    public void applyTenantEvent(TenantEvent event) {
        if (!java.util.Set.of("tenant.created", "tenant.created.v1", "tenant.approved", "tenant.approved.v1", "tenant.suspended", "tenant.suspended.v1").contains(event.eventType())) return;
        if (jdbc != null) jdbc.execute("SELECT pg_advisory_xact_lock("+(long)event.tenantId().hashCode()+")");
        UUID eventId = event.eventId();
        if (eventId != null) {
            int inserted = processedEventRepository.insertIfNotExists(eventId, CONSUMER_NAME);
            if (inserted == 0) {
                log.info("Duplicate event ignored: eventId={}, eventType={}", eventId, event.eventType());
                return; // Skip duplicate event and commit transaction (which ACKs the message)
            }
        }

        User owner = userRepository.findById(event.ownerUserId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        TenantMembership membership = tenantMembershipRepository
                .findByTenantIdAndUser_Id(event.tenantId(), event.ownerUserId())
                .orElseGet(() -> TenantMembership.builder()
                        .tenantId(event.tenantId())
                        .user(owner)
                        .membershipRole(TenantMembershipRole.OWNER)
                        .status(TenantMembershipStatus.ACTIVE)
                        .lastEventVersion(0L)
                        .build());

        // Event ordering check: reject older event if newer version has already been processed
        Long incomingVersion = event.version() != null ? event.version() : 1L;
        if (membership.getLastEventVersion() != null && incomingVersion < membership.getLastEventVersion()) {
            log.warn("Discarding out-of-order event: incomingVersion={}, currentVersion={}, eventType={}",
                    incomingVersion, membership.getLastEventVersion(), event.eventType());
            return;
        }

        if ("tenant.approved".equals(event.eventType()) || "tenant.approved.v1".equals(event.eventType())) {
            if (membership.getStatus()==TenantMembershipStatus.PENDING) membership.setStatus(TenantMembershipStatus.ACTIVE);
        } else if ("tenant.suspended".equals(event.eventType())) {
            membership.setStatus(TenantMembershipStatus.SUSPENDED);
        } else if ("tenant.created".equals(event.eventType()) || "tenant.created.v1".equals(event.eventType())) {
            if (membership.getStatus() == null || membership.getStatus()==TenantMembershipStatus.PENDING) {
                membership.setStatus(TenantMembershipStatus.ACTIVE);
            }
        }

        membership.setLastEventVersion(incomingVersion);
        membership.setLastEventOccurredAt(event.occurredAt() != null ? event.occurredAt() : Instant.now());

        tenantMembershipRepository.save(membership);
        log.info("Applied tenant event: eventId={}, eventType={}, tenantId={}, version={}, status={}",
                eventId, event.eventType(), event.tenantId(), incomingVersion, membership.getStatus());
    }
}
