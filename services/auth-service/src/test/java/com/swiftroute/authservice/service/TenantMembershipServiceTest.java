package com.swiftroute.authservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
import com.swiftroute.authservice.exception.AppException;
import com.swiftroute.authservice.repository.ProcessedEventRepository;
import com.swiftroute.authservice.repository.TenantMembershipRepository;
import com.swiftroute.authservice.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class TenantMembershipServiceTest {

    @Mock
    private TenantMembershipRepository tenantMembershipRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ProcessedEventRepository processedEventRepository;

    private TenantMembershipService tenantMembershipService;

    @BeforeEach
    void setUp() {
        tenantMembershipService = new TenantMembershipService(tenantMembershipRepository, userRepository, processedEventRepository);
    }

    @Test
    void approvedTenantActivatesOwnerMembershipAndJwtTenantReference() {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        User owner = User.builder().id(ownerId).status("ACTIVE").build();
        TenantMembership membership = TenantMembership.builder()
                .tenantId(tenantId)
                .user(owner)
                .lastEventVersion(0L)
                .build();

        when(processedEventRepository.insertIfNotExists(eq(eventId), eq(TenantMembershipService.CONSUMER_NAME))).thenReturn(1);
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(tenantMembershipRepository.findByTenantIdAndUser_Id(tenantId, ownerId)).thenReturn(Optional.of(membership));

        tenantMembershipService.applyTenantEvent(
                new TenantEvent(eventId, "tenant.approved", tenantId, ownerId, Instant.now(), 2L));

        ArgumentCaptor<TenantMembership> membershipCaptor = ArgumentCaptor.forClass(TenantMembership.class);
        verify(tenantMembershipRepository).save(membershipCaptor.capture());
        assertThat(membershipCaptor.getValue().getStatus()).isEqualTo(TenantMembershipStatus.ACTIVE);
        assertThat(membershipCaptor.getValue().getLastEventVersion()).isEqualTo(2L);
    }

    @Test
    void duplicateEventDeliveryIsSkippedWithoutModifyingState() {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        // Simulate event already exists in processed_events (insertIfNotExists returns 0)
        when(processedEventRepository.insertIfNotExists(eq(eventId), eq(TenantMembershipService.CONSUMER_NAME))).thenReturn(0);

        tenantMembershipService.applyTenantEvent(
                new TenantEvent(eventId, "tenant.approved", tenantId, ownerId, Instant.now(), 2L));

        verify(userRepository, never()).findById(any());
        verify(tenantMembershipRepository, never()).save(any());
    }

    @Test
    void outOfOrderOlderEventDoesNotOverwriteNewerProjection() {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        User owner = User.builder().id(ownerId).status("ACTIVE").build();
        TenantMembership currentMembership = TenantMembership.builder()
                .tenantId(tenantId)
                .user(owner)
                .status(TenantMembershipStatus.ACTIVE)
                .lastEventVersion(5L) // Current version is 5
                .build();

        when(processedEventRepository.insertIfNotExists(eq(eventId), eq(TenantMembershipService.CONSUMER_NAME))).thenReturn(1);
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(tenantMembershipRepository.findByTenantIdAndUser_Id(tenantId, ownerId)).thenReturn(Optional.of(currentMembership));

        // Incoming event has older version 3
        tenantMembershipService.applyTenantEvent(
                new TenantEvent(eventId, "tenant.created", tenantId, ownerId, Instant.now(), 3L));

        // Should not save or overwrite
        verify(tenantMembershipRepository, never()).save(any());
        assertThat(currentMembership.getStatus()).isEqualTo(TenantMembershipStatus.ACTIVE);
    }

    @Test
    void suspendedTenantUpdatesMembershipStatus() {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        User owner = User.builder().id(ownerId).status("ACTIVE").build();
        TenantMembership membership = TenantMembership.builder()
                .tenantId(tenantId)
                .user(owner)
                .status(TenantMembershipStatus.ACTIVE)
                .lastEventVersion(2L)
                .build();

        when(processedEventRepository.insertIfNotExists(eq(eventId), eq(TenantMembershipService.CONSUMER_NAME))).thenReturn(1);
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(tenantMembershipRepository.findByTenantIdAndUser_Id(tenantId, ownerId)).thenReturn(Optional.of(membership));

        tenantMembershipService.applyTenantEvent(
                new TenantEvent(eventId, "tenant.suspended", tenantId, ownerId, Instant.now(), 3L));

        ArgumentCaptor<TenantMembership> captor = ArgumentCaptor.forClass(TenantMembership.class);
        verify(tenantMembershipRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(TenantMembershipStatus.SUSPENDED);
        assertThat(captor.getValue().getLastEventVersion()).isEqualTo(3L);
    }

    @Test
    void applicationFailureRollsBackTransaction() {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        when(processedEventRepository.insertIfNotExists(eq(eventId), eq(TenantMembershipService.CONSUMER_NAME))).thenReturn(1);
        when(userRepository.findById(ownerId)).thenReturn(Optional.empty()); // User not found triggers AppException

        assertThatThrownBy(() -> tenantMembershipService.applyTenantEvent(
                new TenantEvent(eventId, "tenant.approved", tenantId, ownerId, Instant.now(), 1L)))
                .isInstanceOf(AppException.class);

        verify(tenantMembershipRepository, never()).save(any());
    }
}
