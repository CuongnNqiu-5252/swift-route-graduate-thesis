package com.swiftroute.orderservice.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import java.util.Set;
import java.util.UUID;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.service.weather.RouteWeatherService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class RouteWeatherControllerTest {
    @Test
    void rejectsDriverAndMissingTenantBeforeWeatherLookup() {
        var service = mock(RouteWeatherService.class);
        var controller = new RouteWeatherController(service);
        assertThatThrownBy(() -> controller.risks(UUID.randomUUID(), "DRIVER", null, "today")).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> controller.risks(null, "OWNER", null, "today")).isInstanceOf(AppException.class);
        verifyNoInteractions(service);
    }

    @Test
    void requiresOrderAndDispatchPermissions() {
        var service = mock(RouteWeatherService.class);
        var request = new MockHttpServletRequest();
        request.setAttribute("authorizedPermissions", Set.of("dispatch:read"));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            var controller = new RouteWeatherController(service);
            assertThatThrownBy(() -> controller.risks(UUID.randomUUID(), "OPERATOR", null, "today")).isInstanceOf(AppException.class);
            verifyNoInteractions(service);
        } finally { RequestContextHolder.resetRequestAttributes(); }
    }
}
