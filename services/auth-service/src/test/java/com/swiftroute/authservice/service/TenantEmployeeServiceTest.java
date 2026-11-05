package com.swiftroute.authservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.swiftroute.authservice.dto.request.AcceptInvitationRequest;
import com.swiftroute.authservice.dto.request.InviteMemberRequest;
import com.swiftroute.authservice.dto.request.UpdateMemberStatusRequest;
import com.swiftroute.authservice.dto.response.InviteMemberResponse;
import com.swiftroute.authservice.dto.response.TenantMemberResponse;
import com.swiftroute.authservice.entity.TenantInvitation;
import com.swiftroute.authservice.entity.TenantMembership;
import com.swiftroute.authservice.entity.User;
import com.swiftroute.authservice.entity.enums.TenantInvitationStatus;
import com.swiftroute.authservice.entity.enums.TenantMembershipRole;
import com.swiftroute.authservice.entity.enums.TenantMembershipStatus;
import com.swiftroute.authservice.exception.AppException;
import com.swiftroute.authservice.exception.ErrorCode;
import com.swiftroute.authservice.repository.TenantInvitationRepository;
import com.swiftroute.authservice.repository.TenantMembershipRepository;
import com.swiftroute.authservice.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class TenantEmployeeServiceTest {

    @Mock
    private TenantMembershipRepository tenantMembershipRepository;

    @Mock
    private TenantInvitationRepository tenantInvitationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private DriverOnboardingClient driverOnboarding;

    @Mock
    private VerificationCipher invitationCipher;

    @Mock
    private TenantDetailsClient tenantDetails;

    @InjectMocks
    private TenantEmployeeService tenantEmployeeService;

    private UUID tenantId;
    private UUID ownerUserId;
    private TenantMembership ownerMembership;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        ownerUserId = UUID.randomUUID();

        User ownerUser = User.builder()
                .id(ownerUserId)
                .email("owner@swiftroute.vn")
                .fullName("Owner User")
                .build();

        ownerMembership = TenantMembership.builder()
                .id(UUID.randomUUID())
                
                .user(ownerUser)
                .membershipRole(TenantMembershipRole.OWNER)
                .status(TenantMembershipStatus.ACTIVE)
                .build();

        org.mockito.Mockito.lenient().when(tenantDetails.businessName(org.mockito.ArgumentMatchers.any()))
                .thenReturn("SwiftRoute Logistics");
        org.mockito.Mockito.lenient().when(invitationCipher.encrypt(org.mockito.ArgumentMatchers.any()))
                .thenReturn("encrypted-link");
    }

    @Test
    @DisplayName("Should successfully invite member, hash token and return plain token once")
    void inviteMemberSuccess() {
        when(tenantMembershipRepository.findByTenantIdAndUser_Id(tenantId, ownerUserId))
                .thenReturn(Optional.of(ownerMembership));
        when(tenantMembershipRepository.findByTenantIdAndUser_EmailIgnoreCase(tenantId, "operator@swiftroute.vn"))
                .thenReturn(Optional.empty());
        when(tenantInvitationRepository.findByTenantIdAndEmailIgnoreCase(tenantId, "operator@swiftroute.vn"))
                .thenReturn(List.of());
        when(tenantInvitationRepository.save(any(TenantInvitation.class))).thenAnswer(inv -> {
            TenantInvitation i = inv.getArgument(0);
            i.setId(UUID.randomUUID());
            return i;
        });

        InviteMemberRequest req = new InviteMemberRequest("operator@swiftroute.vn", TenantMembershipRole.OPERATOR);
        InviteMemberResponse res = tenantEmployeeService.inviteMember(tenantId, ownerUserId, req);

        assertThat(res.email()).isEqualTo("operator@swiftroute.vn");
        assertThat(res.role()).isEqualTo(TenantMembershipRole.OPERATOR);
        assertThat(res.status()).isEqualTo(TenantInvitationStatus.PENDING);
        assertThat(res.inviteToken()).isNotBlank();
        assertThat(res.expiresAt()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("Should reject invitation if user is already an active member of this tenant")
    void rejectInviteExistingActiveMember() {
        when(tenantMembershipRepository.findByTenantIdAndUser_Id(tenantId, ownerUserId))
                .thenReturn(Optional.of(ownerMembership));

        TenantMembership existing = TenantMembership.builder()
                
                .membershipRole(TenantMembershipRole.OPERATOR)
                .status(TenantMembershipStatus.ACTIVE)
                .build();

        when(tenantMembershipRepository.findByTenantIdAndUser_EmailIgnoreCase(tenantId, "operator@swiftroute.vn"))
                .thenReturn(Optional.of(existing));

        InviteMemberRequest req = new InviteMemberRequest("operator@swiftroute.vn", TenantMembershipRole.OPERATOR);

        assertThatThrownBy(() -> tenantEmployeeService.inviteMember(tenantId, ownerUserId, req))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TENANT_MEMBERSHIP_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("Should prevent suspending the last remaining active OWNER")
    void cannotSuspendLastOwner() {
        UUID membershipId = ownerMembership.getId();
        when(tenantMembershipRepository.findByTenantIdAndUser_Id(tenantId, ownerUserId))
                .thenReturn(Optional.of(ownerMembership));
        when(tenantMembershipRepository.findByIdAndTenantId(membershipId, tenantId))
                .thenReturn(Optional.of(ownerMembership));
        when(tenantMembershipRepository.countByTenantIdAndMembershipRoleAndStatus(
                tenantId, TenantMembershipRole.OWNER, TenantMembershipStatus.ACTIVE))
                .thenReturn(1L);

        UpdateMemberStatusRequest req = new UpdateMemberStatusRequest(TenantMembershipStatus.SUSPENDED);

        assertThatThrownBy(() -> tenantEmployeeService.updateMemberStatus(tenantId, membershipId, req, ownerUserId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CANNOT_SUSPEND_LAST_OWNER);
    }

    @Test
    @DisplayName("Should accept valid invitation, create new user and activate membership")
    void acceptInvitationSuccess() {
        TenantInvitation inv = TenantInvitation.builder()
                .id(UUID.randomUUID())
                
                .email("newstaff@swiftroute.vn")
                .role(TenantMembershipRole.OPERATOR)
                .status(TenantInvitationStatus.PENDING)
                .invitedBy(ownerUserId)
                .expiresAt(Instant.now().plus(3, ChronoUnit.DAYS))
                .build();

        when(tenantInvitationRepository.findByTokenHash(anyString())).thenReturn(Optional.of(inv));
        when(userRepository.findByEmail("newstaff@swiftroute.vn")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Secret123")).thenReturn("hashedSecret123");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        when(tenantMembershipRepository.findByTenantIdAndUser_Id(any(), any())).thenReturn(Optional.empty());
        when(tenantMembershipRepository.save(any(TenantMembership.class))).thenAnswer(invocation -> {
            TenantMembership m = invocation.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        AcceptInvitationRequest req = new AcceptInvitationRequest("raw-test-token-123", "Secret123", "New Staff");
        TenantMemberResponse res = tenantEmployeeService.acceptInvitation(req);

        assertThat(res.role()).isEqualTo(TenantMembershipRole.OPERATOR);
        assertThat(res.status()).isEqualTo(TenantMembershipStatus.ACTIVE);
        assertThat(inv.getStatus()).isEqualTo(TenantInvitationStatus.ACCEPTED);
    }
}

