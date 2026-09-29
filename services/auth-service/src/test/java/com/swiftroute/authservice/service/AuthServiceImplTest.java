package com.swiftroute.authservice.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.swiftroute.authservice.dto.request.RegisterRequestDTO;
import com.swiftroute.authservice.entity.User;
import com.swiftroute.authservice.exception.EmailAlreadyExistsException;
import com.swiftroute.authservice.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtServiceTest jwtService;

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
}
