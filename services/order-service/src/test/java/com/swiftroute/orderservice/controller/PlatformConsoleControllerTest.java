package com.swiftroute.orderservice.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.Date;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.security.JwtTokenValidator;
import com.swiftroute.orderservice.service.platform.PlatformReadService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

class PlatformConsoleControllerTest {
    @Test
    void tenantOwnerCannotReadGlobalAuditOrSnapshot() {
        var tokens = mock(JwtTokenValidator.class); var claims = mock(Claims.class);
        var reads = mock(PlatformReadService.class);
        when(tokens.parseAndValidate("Bearer tenant")).thenReturn(claims);
        when(claims.getExpiration()).thenReturn(new Date(System.currentTimeMillis() + 60_000));
        when(claims.get("system_role", String.class)).thenReturn("USER");
        var controller = new PlatformConsoleController(tokens, reads);
        assertThatThrownBy(() -> controller.history("Bearer tenant", "", "", "", 7, null, null)).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> controller.snapshot("Bearer tenant")).isInstanceOf(AppException.class);
        verifyNoInteractions(reads);
    }

    @Test
    void validSystemAdminCanReadTheExistingSnapshot() {
        var tokens = mock(JwtTokenValidator.class); var claims = mock(Claims.class);
        var reads = mock(PlatformReadService.class);
        when(tokens.parseAndValidate("Bearer admin")).thenReturn(claims);
        when(claims.getExpiration()).thenReturn(new Date(System.currentTimeMillis() + 45_000));
        when(claims.get("system_role", String.class)).thenReturn("SYSTEM_ADMIN");
        new PlatformConsoleController(tokens, reads).snapshot("Bearer admin");
        verify(reads).snapshot();
    }
}
