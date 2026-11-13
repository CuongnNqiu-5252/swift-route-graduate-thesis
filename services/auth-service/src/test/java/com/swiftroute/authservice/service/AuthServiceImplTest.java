package com.swiftroute.authservice.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.swiftroute.authservice.dto.request.LoginRequestDTO;
import com.swiftroute.authservice.dto.request.RegisterRequestDTO;
import com.swiftroute.authservice.dto.response.LoginResponseDTO;
import com.swiftroute.authservice.entity.TenantMembership;
import com.swiftroute.authservice.entity.User;
import com.swiftroute.authservice.entity.enums.Role;
import com.swiftroute.authservice.entity.enums.TenantMembershipRole;
import com.swiftroute.authservice.entity.enums.TenantMembershipStatus;
import com.swiftroute.authservice.exception.EmailAlreadyExistsException;
import com.swiftroute.authservice.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private com.swiftroute.authservice.repository.TenantMembershipRepository tenantMembershipRepository;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthserviceImpl authService; // The class under test

    @Test
    @DisplayName("Should successfully register a new user when email does not exist")
    void register_ValidRequest_SavesUser() {
        // Arrange (Given)
        RegisterRequestDTO request = RegisterRequestDTO.builder()
                .email("newuser@example.com")
                .password("Password123")
                .build();

        // Mock repository to return empty, meaning email is available
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());

        // Mock password encoding
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encoded_password");

        // Act (When)
        assertDoesNotThrow(() -> authService.register(request));

        // Assert (Then)
        // Verify that userRepository.save() was called exactly once with any User
        // object
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw EmailAlreadyExistsException when email is already taken")
    void register_EmailExists_ThrowsException() {
        // Arrange (Given)
        RegisterRequestDTO request = RegisterRequestDTO.builder()
                .email("existing@example.com")
                .password("Password123")
                .build();

        User existingUser = new User();
        existingUser.setEmail("existing@example.com");

        // Mock repository to return an existing user, meaning email is taken
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(existingUser));

        // Act & Assert (When & Then)
        // Verify that the correct exception is thrown
        assertThrows(EmailAlreadyExistsException.class, () -> authService.register(request));

        // Verify that userRepository.save() was NEVER called because it threw an
        // exception early
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should return tokens and tenant-scoped user profile on login when user has active membership")
    void login_WithActiveMembership_ReturnsTokensAndUserProfile() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("admin@tenant.vn")
                .fullName("Tenant Admin")
                .phone("0901234567")
                .passwordHash("hashed")
                .role(Role.USER)
                .status("ACTIVE")
                .emailVerified(true)
                .build();

        TenantMembership membership = TenantMembership.builder()
                .tenantId(tenantId)
                .user(user)
                .membershipRole(TenantMembershipRole.TENANT_ADMIN)
                .status(TenantMembershipStatus.ACTIVE)
                .build();

        LoginRequestDTO request = new LoginRequestDTO();
        request.setUsername("admin@tenant.vn");
        request.setPassword("secret123");

        when(userRepository.findByEmail("admin@tenant.vn")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret123", "hashed")).thenReturn(true);
        when(tenantMembershipRepository.findByUser_IdAndStatus(userId, TenantMembershipStatus.ACTIVE))
                .thenReturn(List.of(membership));
        when(jwtService.generateTenantAccessToken(user, tenantId, "TENANT_ADMIN", 15)).thenReturn("access-jwt");
        when(jwtService.generateAndStoreRefreshToken(user, null)).thenReturn("refresh-raw");

        LoginResponseDTO response = authService.login(request, null);

        assertEquals("access-jwt", response.getAccessToken());
        assertEquals("refresh-raw", response.getRefreshToken());
        assertNotNull(response.getUser());
        assertEquals(userId, response.getUser().id());
        assertEquals("admin@tenant.vn", response.getUser().email());
        assertEquals(Role.USER, response.getUser().accountRole());
        assertEquals("TENANT_ADMIN", response.getUser().role());
        assertEquals(tenantId, response.getUser().tenantId());
    }

    @Test
    @DisplayName("Should return tokens and base system user profile on login when user has no tenant membership")
    void login_WithoutMembership_ReturnsBaseUserProfile() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("sysadmin@swiftroute.vn")
                .fullName("System Admin")
                .passwordHash("hashed")
                .role(Role.SYSTEM_ADMIN)
                .status("ACTIVE")
                .emailVerified(true)
                .build();

        LoginRequestDTO request = new LoginRequestDTO();
        request.setUsername("sysadmin@swiftroute.vn");
        request.setPassword("secret123");

        when(userRepository.findByEmail("sysadmin@swiftroute.vn")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret123", "hashed")).thenReturn(true);
        when(tenantMembershipRepository.findByUser_IdAndStatus(userId, TenantMembershipStatus.ACTIVE))
                .thenReturn(List.of());
        when(jwtService.generateAccessToken(user, 15)).thenReturn("access-jwt");
        when(jwtService.generateAndStoreRefreshToken(user, null)).thenReturn("refresh-raw");

        LoginResponseDTO response = authService.login(request, null);

        assertEquals("access-jwt", response.getAccessToken());
        assertEquals("refresh-raw", response.getRefreshToken());
        assertNotNull(response.getUser());
        assertEquals(Role.SYSTEM_ADMIN, response.getUser().accountRole());
        assertEquals("SYSTEM_ADMIN", response.getUser().role());
        assertNull(response.getUser().tenantId());
    }
}
