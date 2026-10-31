package com.swiftroute.authservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.swiftroute.authservice.entity.TenantMembership;
import com.swiftroute.authservice.entity.User;
import com.swiftroute.authservice.entity.enums.TenantMembershipStatus;
import com.swiftroute.authservice.event.TenantEvent;
import com.swiftroute.authservice.repository.TenantMembershipRepository;
import com.swiftroute.authservice.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class TenantMembershipServiceTest {
    @Mock
    private TenantMembershipRepository tenantMembershipRepository;
    @Mock
    private UserRepository userRepository;

    private TenantMembershipService tenantMembershipService;

    @BeforeEach
    void setUp() {
        tenantMembershipService = new TenantMembershipService(tenantMembershipRepository, userRepository);
    }

    @Test
    void approvedTenantActivatesOwnerMembershipAndJwtTenantReference() {
        UUID tenantId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        User owner = User.builder().id(ownerId).status("ACTIVE").build();
        TenantMembership membership = TenantMembership.builder().tenantId(tenantId).user(owner).build();
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(tenantMembershipRepository.findByTenantIdAndUser_Id(tenantId, ownerId)).thenReturn(Optional.of(membership));

        tenantMembershipService.applyTenantEvent(
                new TenantEvent(UUID.randomUUID(), "tenant.approved", tenantId, ownerId, Instant.now()));

        ArgumentCaptor<TenantMembership> membershipCaptor = ArgumentCaptor.forClass(TenantMembership.class);
        verify(tenantMembershipRepository).save(membershipCaptor.capture());
        assertThat(membershipCaptor.getValue().getStatus()).isEqualTo(TenantMembershipStatus.ACTIVE);
        assertThat(owner.getTenantId()).isEqualTo(tenantId);
    }
}
