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
import com.swiftroute.authservice.repository.RefreshTokenRepository;
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
    private RefreshTokenRepository refreshTokenRepository;

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

    @Test
    @DisplayName("TENANT_ADMIN cannot invite a user with OWNER role (rejected with ACCESS_DENIED)")
    void tenantAdminCannotInviteOwner() {
        UUID adminUserId = UUID.randomUUID();
        User adminUser = User.builder().id(adminUserId).email("admin@swiftroute.vn").build();
        TenantMembership adminMembership = TenantMembership.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .user(adminUser)
                .membershipRole(TenantMembershipRole.TENANT_ADMIN)
                .status(TenantMembershipStatus.ACTIVE)
                .build();

        when(tenantMembershipRepository.findByTenantIdAndUser_Id(tenantId, adminUserId))
                .thenReturn(Optional.of(adminMembership));

        InviteMemberRequest req = new InviteMemberRequest("newowner@swiftroute.vn", TenantMembershipRole.OWNER);

        assertThatThrownBy(() -> tenantEmployeeService.inviteMember(tenantId, adminUserId, req))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("TENANT_ADMIN cannot suspend an OWNER member (rejected with ACCESS_DENIED)")
    void tenantAdminCannotSuspendOwner() {
        UUID adminUserId = UUID.randomUUID();
        User adminUser = User.builder().id(adminUserId).email("admin@swiftroute.vn").build();
        TenantMembership adminMembership = TenantMembership.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .user(adminUser)
                .membershipRole(TenantMembershipRole.TENANT_ADMIN)
                .status(TenantMembershipStatus.ACTIVE)
                .build();

        UUID targetMembershipId = ownerMembership.getId();

        when(tenantMembershipRepository.findByTenantIdAndUser_Id(tenantId, adminUserId))
                .thenReturn(Optional.of(adminMembership));
        when(tenantMembershipRepository.findByIdAndTenantId(targetMembershipId, tenantId))
                .thenReturn(Optional.of(ownerMembership));

        UpdateMemberStatusRequest req = new UpdateMemberStatusRequest(TenantMembershipStatus.SUSPENDED);

        assertThatThrownBy(() -> tenantEmployeeService.updateMemberStatus(tenantId, targetMembershipId, req, adminUserId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("TENANT_ADMIN cannot suspend themselves (rejected with ACCESS_DENIED)")
    void tenantAdminCannotSuspendSelf() {
        UUID adminUserId = UUID.randomUUID();
        User adminUser = User.builder().id(adminUserId).email("admin@swiftroute.vn").build();
        UUID adminMembershipId = UUID.randomUUID();
        TenantMembership adminMembership = TenantMembership.builder()
                .id(adminMembershipId)
                .tenantId(tenantId)
                .user(adminUser)
                .membershipRole(TenantMembershipRole.TENANT_ADMIN)
                .status(TenantMembershipStatus.ACTIVE)
                .build();

        when(tenantMembershipRepository.findByTenantIdAndUser_Id(tenantId, adminUserId))
                .thenReturn(Optional.of(adminMembership));
        when(tenantMembershipRepository.findByIdAndTenantId(adminMembershipId, tenantId))
                .thenReturn(Optional.of(adminMembership));

        UpdateMemberStatusRequest req = new UpdateMemberStatusRequest(TenantMembershipStatus.SUSPENDED);

        assertThatThrownBy(() -> tenantEmployeeService.updateMemberStatus(tenantId, adminMembershipId, req, adminUserId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("Suspending member revokes all refresh tokens and synchronizes user status to SUSPENDED")
    void suspendingMemberRevokesTokensAndSyncsStatus() {
        UUID targetUserId = UUID.randomUUID();
        User targetUser = User.builder().id(targetUserId).email("staff@swiftroute.vn").status("ACTIVE").build();
        UUID targetMembershipId = UUID.randomUUID();
        TenantMembership targetMembership = TenantMembership.builder()
                .id(targetMembershipId)
                .tenantId(tenantId)
                .user(targetUser)
                .membershipRole(TenantMembershipRole.OPERATOR)
                .status(TenantMembershipStatus.ACTIVE)
                .build();

        when(tenantMembershipRepository.findByTenantIdAndUser_Id(tenantId, ownerUserId))
                .thenReturn(Optional.of(ownerMembership));
        when(tenantMembershipRepository.findByIdAndTenantId(targetMembershipId, tenantId))
                .thenReturn(Optional.of(targetMembership));
        when(tenantMembershipRepository.save(any(TenantMembership.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateMemberStatusRequest req = new UpdateMemberStatusRequest(TenantMembershipStatus.SUSPENDED);
        tenantEmployeeService.updateMemberStatus(tenantId, targetMembershipId, req, ownerUserId);

        verify(refreshTokenRepository).revokeAllByUserId(targetUserId);
        assertThat(targetUser.getStatus()).isEqualTo("SUSPENDED");
        verify(userRepository).save(targetUser);
    }

    @Test
    @DisplayName("Reactivating member synchronizes user status to ACTIVE")
    void reactivatingMemberSyncsUserStatusToActive() {
        UUID targetUserId = UUID.randomUUID();
        User targetUser = User.builder().id(targetUserId).email("staff@swiftroute.vn").status("SUSPENDED").build();
        UUID targetMembershipId = UUID.randomUUID();
        TenantMembership targetMembership = TenantMembership.builder()
                .id(targetMembershipId)
                .tenantId(tenantId)
                .user(targetUser)
                .membershipRole(TenantMembershipRole.OPERATOR)
                .status(TenantMembershipStatus.SUSPENDED)
                .build();

        when(tenantMembershipRepository.findByTenantIdAndUser_Id(tenantId, ownerUserId))
                .thenReturn(Optional.of(ownerMembership));
        when(tenantMembershipRepository.findByIdAndTenantId(targetMembershipId, tenantId))
                .thenReturn(Optional.of(targetMembership));
        when(tenantMembershipRepository.save(any(TenantMembership.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateMemberStatusRequest req = new UpdateMemberStatusRequest(TenantMembershipStatus.ACTIVE);
        tenantEmployeeService.updateMemberStatus(tenantId, targetMembershipId, req, ownerUserId);

        assertThat(targetUser.getStatus()).isEqualTo("ACTIVE");
        verify(userRepository).save(targetUser);
    }

    @Test
    @DisplayName("OWNER can customize per-member granted and revoked permissions and bump permissionsVersion")
    void ownerCanCustomizeMemberPermissions() {
        UUID targetUserId = UUID.randomUUID();
        User targetUser = User.builder().id(targetUserId).email("op@swiftroute.vn").status("ACTIVE").build();
        UUID targetMembershipId = UUID.randomUUID();
        TenantMembership targetMembership = TenantMembership.builder()
                .id(targetMembershipId)
                .tenantId(tenantId)
                .user(targetUser)
                .membershipRole(TenantMembershipRole.OPERATOR)
                .status(TenantMembershipStatus.ACTIVE)
                .permissionsVersion(1L)
                .build();

        when(tenantMembershipRepository.findByTenantIdAndUser_Id(tenantId, ownerUserId))
                .thenReturn(Optional.of(ownerMembership));
        when(tenantMembershipRepository.findByIdAndTenantId(targetMembershipId, tenantId))
                .thenReturn(Optional.of(targetMembership));
        when(tenantMembershipRepository.save(any(TenantMembership.class))).thenAnswer(inv -> inv.getArgument(0));

        var req = new com.swiftroute.authservice.dto.request.UpdateMemberPermissionsRequest(
                java.util.List.of("billing:read", "apikey:read"),
                java.util.List.of("order:cancel"));

        TenantMemberResponse res = tenantEmployeeService.updateMemberPermissions(
                tenantId, targetMembershipId, req, ownerUserId);

        assertThat(res.permissionsVersion()).isEqualTo(2L);
        assertThat(res.grantedPermissions()).containsExactly("billing:read", "apikey:read");
        assertThat(res.revokedPermissions()).containsExactly("order:cancel");
        assertThat(res.effectivePermissions()).contains("billing:read", "apikey:read", "order:read");
        assertThat(res.effectivePermissions()).doesNotContain("order:cancel");
        verify(refreshTokenRepository).revokeAllByUserId(targetUserId);
    }

    @Test
    @DisplayName("TENANT_ADMIN cannot modify permissions of another TENANT_ADMIN or grant billing:manage")
    void tenantAdminHierarchyAndGrantCeilingEnforced() {
        UUID adminUserId = UUID.randomUUID();
        User adminUser = User.builder().id(adminUserId).email("admin@swiftroute.vn").build();
        TenantMembership adminMembership = TenantMembership.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .user(adminUser)
                .membershipRole(TenantMembershipRole.TENANT_ADMIN)
                .status(TenantMembershipStatus.ACTIVE)
                .build();

        UUID operatorUserId = UUID.randomUUID();
        User operatorUser = User.builder().id(operatorUserId).email("op@swiftroute.vn").build();
        UUID operatorMembershipId = UUID.randomUUID();
        TenantMembership operatorMembership = TenantMembership.builder()
                .id(operatorMembershipId)
                .tenantId(tenantId)
                .user(operatorUser)
                .membershipRole(TenantMembershipRole.OPERATOR)
                .status(TenantMembershipStatus.ACTIVE)
                .build();

        when(tenantMembershipRepository.findByTenantIdAndUser_Id(tenantId, adminUserId))
                .thenReturn(Optional.of(adminMembership));
        when(tenantMembershipRepository.findByIdAndTenantId(operatorMembershipId, tenantId))
                .thenReturn(Optional.of(operatorMembership));

        // TENANT_ADMIN does not hold billing:manage by default -> must fail grant ceiling
        var overGrantReq = new com.swiftroute.authservice.dto.request.UpdateMemberPermissionsRequest(
                java.util.List.of("billing:manage"),
                java.util.List.of());

        assertThatThrownBy(() -> tenantEmployeeService.updateMemberPermissions(
                tenantId, operatorMembershipId, overGrantReq, adminUserId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
    }
}

