package com.swiftroute.orderservice.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftroute.orderservice.dto.request.CreateTenantRequest;
import com.swiftroute.orderservice.dto.request.TenantStatusRequest;
import com.swiftroute.orderservice.dto.response.TenantResponse;
import com.swiftroute.orderservice.entity.OutboxEvent;
import com.swiftroute.orderservice.entity.Tenant;
import com.swiftroute.orderservice.entity.TenantEventPayload;
import com.swiftroute.orderservice.entity.enums.SubscriptionPlan;
import com.swiftroute.orderservice.entity.enums.TenantStatus;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import com.swiftroute.orderservice.repository.OutboxEventRepository;
import com.swiftroute.orderservice.repository.TenantRepository;

import lombok.RequiredArgsConstructor;

/** Contains tenant onboarding state transitions and writes their durable events. */
@Service
@RequiredArgsConstructor
public class TenantService {
    private static final Set<String> CREATE_ROLES = Set.of("SYSTEM_ADMIN", "OPS_STAFF", "ADMIN", "OWNER", "TENANT_ADMIN");
    private static final Set<String> REVIEW_ROLES = Set.of("SYSTEM_ADMIN", "OPS_STAFF", "ADMIN");

    private final TenantRepository tenantRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final Clock clock = Clock.systemUTC();

    @Transactional
    public TenantResponse createTenant(CreateTenantRequest request, UUID requesterId, String requesterRole) {
        requireRole(requesterRole, CREATE_ROLES);
        String normalizedBusinessName = request.businessName().trim();
        validateWeights(request);

        if (tenantRepository.existsByBusinessNameIgnoreCase(normalizedBusinessName)) {
            throw new AppException(ErrorCode.TENANT_ALREADY_EXISTS);
        }

        Tenant tenant = Tenant.builder()
                .ownerUserId(requesterId)
                .businessName(normalizedBusinessName)
                .businessType(request.businessType())
                .address(request.address().trim())
                .pickupLat(request.pickupLat())
                .pickupLng(request.pickupLng())
                .optimizationWeights(request.optimizationWeights())
                .subscriptionPlan(SubscriptionPlan.FREE_TRIAL)
                .status(TenantStatus.PENDING)
                .build();

        Tenant savedTenant = tenantRepository.save(tenant);
        createOutboxEvent(savedTenant, "tenant.created");
        return TenantResponse.fromEntity(savedTenant);
    }

    @Transactional(readOnly = true)
    public TenantResponse getTenant(UUID tenantId, UUID requesterId, String requesterRole) {
        Tenant tenant = findTenant(tenantId);
        if (!REVIEW_ROLES.contains(requesterRole) && !requesterId.equals(tenant.getOwnerUserId())) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        return TenantResponse.fromEntity(tenant);
    }

    @Transactional
    public TenantResponse changeStatus(UUID tenantId, TenantStatusRequest request, UUID reviewerId, String reviewerRole) {
        requireRole(reviewerRole, REVIEW_ROLES);
        Tenant tenant = findTenant(tenantId);
        TenantStatus current = tenant.getStatus();
        TenantStatus next = request.status();

        validateTransition(current, next, request.reason());
        tenant.setStatus(next);
        tenant.setApprovalReason(blankToNull(request.reason()));

        if (next == TenantStatus.APPROVED) {
            tenant.setApprovedBy(reviewerId);
            tenant.setApprovedAt(LocalDateTime.now(clock));
            tenant.setSuspendedAt(null);
        } else if (next == TenantStatus.SUSPENDED) {
            tenant.setSuspendedAt(LocalDateTime.now(clock));
        }

        Tenant savedTenant = tenantRepository.save(tenant);
        createOutboxEvent(savedTenant, eventTypeFor(next));
        return TenantResponse.fromEntity(savedTenant);
    }

    private Tenant findTenant(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.TENANT_NOT_FOUND));
    }

    private void validateWeights(CreateTenantRequest request) {
        double sum = request.optimizationWeights().getTimeWeight() + request.optimizationWeights().getCostWeight();
        if (Math.abs(sum - 1.0d) > 0.000001d) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private void validateTransition(TenantStatus current, TenantStatus next, String reason) {
        boolean allowed = switch (current) {
            case PENDING -> next == TenantStatus.APPROVED || next == TenantStatus.REJECTED || next == TenantStatus.SUSPENDED;
            case APPROVED -> next == TenantStatus.SUSPENDED;
            case SUSPENDED -> next == TenantStatus.APPROVED;
            case REJECTED -> false;
        };
        if (!allowed || (next == TenantStatus.REJECTED && blankToNull(reason) == null)) {
            throw new AppException(ErrorCode.INVALID_TENANT_STATUS_TRANSITION);
        }
    }

    private void requireRole(String requesterRole, Set<String> allowedRoles) {
        if (!allowedRoles.contains(requesterRole)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
    }

    private void createOutboxEvent(Tenant tenant, String eventType) {
        UUID eventId = UUID.randomUUID();
        TenantEventPayload payload = new TenantEventPayload(eventId, eventType, tenant.getId(), tenant.getOwnerUserId(), Instant.now(clock));
        outboxEventRepository.save(OutboxEvent.builder()
                .eventId(eventId)
                .aggregateType("tenant")
                .aggregateId(tenant.getId())
                .eventType(eventType)
                .payload(payload)
                .build());
    }

    private String eventTypeFor(TenantStatus status) {
        return switch (status) {
            case APPROVED -> "tenant.approved";
            case REJECTED -> "tenant.rejected";
            case SUSPENDED -> "tenant.suspended";
            case PENDING -> throw new AppException(ErrorCode.INVALID_TENANT_STATUS_TRANSITION);
        };
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
