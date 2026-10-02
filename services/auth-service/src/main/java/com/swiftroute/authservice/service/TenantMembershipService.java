package com.swiftroute.authservice.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftroute.authservice.entity.TenantMembership;
import com.swiftroute.authservice.entity.User;
import com.swiftroute.authservice.entity.enums.TenantMembershipRole;
import com.swiftroute.authservice.entity.enums.TenantMembershipStatus;
import com.swiftroute.authservice.event.TenantEvent;
import com.swiftroute.authservice.exception.AppException;
import com.swiftroute.authservice.exception.ErrorCode;
import com.swiftroute.authservice.repository.TenantMembershipRepository;
import com.swiftroute.authservice.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/** Maintains Auth Service's local membership projection from tenant lifecycle events. */
@Service
@RequiredArgsConstructor
public class TenantMembershipService {
    private final TenantMembershipRepository tenantMembershipRepository;
    private final UserRepository userRepository;

    @Transactional
    public void applyTenantEvent(TenantEvent event) {
        User owner = userRepository.findById(event.ownerUserId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        TenantMembership membership = tenantMembershipRepository
                .findByTenantIdAndUser_Id(event.tenantId(), event.ownerUserId())
                .orElseGet(() -> TenantMembership.builder()
                        .tenantId(event.tenantId())
                        .user(owner)
                        .membershipRole(TenantMembershipRole.OWNER)
                        .status(TenantMembershipStatus.PENDING)
                        .build());

        if ("tenant.approved".equals(event.eventType())) {
            membership.setStatus(TenantMembershipStatus.ACTIVE);
            owner.setTenantId(event.tenantId());
        }

        tenantMembershipRepository.save(membership);
    }
}
